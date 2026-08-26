package cn.iocoder.yudao.module.clm.service.contracttype;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.clm.access.ContractAccessService;
import cn.iocoder.yudao.module.clm.controller.admin.contracttype.vo.*;
import cn.iocoder.yudao.module.clm.dal.dataobject.audit.AuditEventDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.contracttype.ContractTypeDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.contracttype.ContractTypeVersionDO;
import cn.iocoder.yudao.module.clm.dal.mysql.audit.AuditEventMapper;
import cn.iocoder.yudao.module.clm.dal.mysql.contract.ContractMapper;
import cn.iocoder.yudao.module.clm.dal.mysql.contracttype.ContractTypeMapper;
import cn.iocoder.yudao.module.clm.dal.mysql.contracttype.ContractTypeVersionMapper;
import cn.iocoder.yudao.module.clm.document.DbDocumentStorage;
import cn.iocoder.yudao.module.clm.enums.audit.ClmAuditActionEnum;
import cn.iocoder.yudao.module.clm.enums.audit.ClmAuditAggregateTypeEnum;
import cn.iocoder.yudao.module.clm.enums.contract.ClmApprovalStatusEnum;
import cn.iocoder.yudao.module.clm.enums.contract.ClmLifecycleStatusEnum;
import cn.iocoder.yudao.module.clm.enums.contracttype.ClmTypeVersionStatusEnum;
import cn.iocoder.yudao.module.clm.service.audit.ClmAuditServiceImpl;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.clm.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ContractTypeServiceImpl} 的单元测试
 */
@Import({ContractTypeServiceImpl.class, DbDocumentStorage.class, ClmAuditServiceImpl.class})
public class ContractTypeServiceImplTest extends BaseDbUnitTest {

    @Resource
    private ContractTypeServiceImpl contractTypeService;

    @Resource
    private ContractTypeMapper contractTypeMapper;
    @Resource
    private ContractTypeVersionMapper contractTypeVersionMapper;
    @Resource
    private ContractMapper contractMapper;
    @Resource
    private AuditEventMapper auditEventMapper;

    @MockitoBean
    private ContractAccessService contractAccessService;

    @Test
    public void testCreateContractType_success() {
        ContractTypeSaveReqVO reqVO = buildSaveReqVO("SALES", "销售合同");
        reqVO.setProcessDefinitionKey("custom_key");

        Long id = contractTypeService.createContractType(reqVO);

        ContractTypeDO type = contractTypeMapper.selectById(id);
        assertEquals("SALES", type.getCode());
        assertEquals("销售合同", type.getName());
        assertEquals(CommonStatusEnum.ENABLE.getStatus(), type.getStatus());
        assertNull(type.getCurrentVersionId());
        // 版本 1（草稿）
        List<ContractTypeVersionDO> versions = contractTypeVersionMapper.selectListByTypeId(id);
        assertEquals(1, versions.size());
        ContractTypeVersionDO v1 = versions.get(0);
        assertEquals(1, v1.getVersionNo());
        assertEquals(ClmTypeVersionStatusEnum.DRAFT.getStatus(), v1.getStatus());
        assertNull(v1.getFormConf());
        assertNotNull(v1.getFormFields());
        assertTrue(v1.getFormFields().isEmpty());
        assertEquals("custom_key", v1.getProcessDefinitionKey());
        // 详情
        ContractTypeRespVO detail = contractTypeService.getContractTypeDetail(id);
        assertEquals(v1.getId(), detail.getDraftVersionId());
        assertNull(detail.getCurrentVersionNo());
    }

    @Test
    public void testCreateContractType_codeDuplicate() {
        contractTypeService.createContractType(buildSaveReqVO("DUP", "A"));
        assertServiceException(() -> contractTypeService.createContractType(buildSaveReqVO("DUP", "B")),
                CONTRACT_TYPE_CODE_DUPLICATE, "DUP");
    }

    @Test
    public void testDraftPublishFlow() {
        // 1. 创建 → 草稿 v1
        Long typeId = contractTypeService.createContractType(buildSaveReqVO("FLOW", "流程类型"));
        ContractTypeVersionDO v1 = contractTypeVersionMapper.selectListByTypeId(typeId).get(0);
        // 2. 更新草稿
        ContractTypeVersionSaveReqVO updateReqVO = new ContractTypeVersionSaveReqVO();
        updateReqVO.setId(v1.getId());
        updateReqVO.setFormConf("{\"labelWidth\":125}");
        updateReqVO.setFormFields(Arrays.asList("{\"field\":\"a\",\"title\":\"A\"}", "{\"field\":\"b\",\"title\":\"B\",\"$required\":true}"));
        updateReqVO.setProcessDefinitionKey("clm_contract_approval_v1");
        updateReqVO.setRemark("first");
        contractTypeService.updateContractTypeVersion(updateReqVO);
        ContractTypeVersionDO dbV1 = contractTypeVersionMapper.selectById(v1.getId());
        assertEquals("{\"labelWidth\":125}", dbV1.getFormConf());
        assertEquals(2, dbV1.getFormFields().size());
        assertEquals("{\"field\":\"b\",\"title\":\"B\",\"$required\":true}", dbV1.getFormFields().get(1));
        assertEquals("first", dbV1.getRemark());
        // 3. 发布
        contractTypeService.publishContractTypeVersion(v1.getId());
        dbV1 = contractTypeVersionMapper.selectById(v1.getId());
        assertEquals(ClmTypeVersionStatusEnum.PUBLISHED.getStatus(), dbV1.getStatus());
        assertNotNull(dbV1.getPublishedTime());
        ContractTypeDO type = contractTypeMapper.selectById(typeId);
        assertEquals(v1.getId(), type.getCurrentVersionId());
        // 审计 TYPE_PUBLISH
        List<AuditEventDO> events = auditEventMapper.selectList();
        assertEquals(1, events.size());
        assertEquals(ClmAuditActionEnum.TYPE_PUBLISH.getCode(), events.get(0).getAction());
        assertEquals(ClmAuditAggregateTypeEnum.CONTRACT_TYPE.getCode(), events.get(0).getAggregateType());
        assertEquals(typeId, events.get(0).getAggregateId());
        // 4. 发布后 update 抛错
        assertServiceException(() -> contractTypeService.updateContractTypeVersion(updateReqVO),
                CONTRACT_TYPE_VERSION_NOT_DRAFT);
        // 重复发布抛错
        assertServiceException(() -> contractTypeService.publishContractTypeVersion(v1.getId()),
                CONTRACT_TYPE_VERSION_NOT_DRAFT);
        // 5. create-draft：复制当前发布版本
        Long draftId = contractTypeService.createDraftVersion(typeId);
        ContractTypeVersionDO draft = contractTypeVersionMapper.selectById(draftId);
        assertEquals(2, draft.getVersionNo());
        assertEquals(ClmTypeVersionStatusEnum.DRAFT.getStatus(), draft.getStatus());
        assertEquals(dbV1.getFormConf(), draft.getFormConf());
        assertEquals(dbV1.getFormFields(), draft.getFormFields());
        assertEquals(dbV1.getProcessDefinitionKey(), draft.getProcessDefinitionKey());
        // 已存在草稿再建抛错
        assertServiceException(() -> contractTypeService.createDraftVersion(typeId), CONTRACT_TYPE_DRAFT_EXISTS);
        // 详情：current=v1，draft=v2
        ContractTypeRespVO detail = contractTypeService.getContractTypeDetail(typeId);
        assertEquals(v1.getId(), detail.getCurrentVersionId());
        assertEquals(1, detail.getCurrentVersionNo());
        assertEquals(draftId, detail.getDraftVersionId());
        // 版本列表按 versionNo 降序
        List<ContractTypeVersionDO> versions = contractTypeService.getContractTypeVersionList(typeId);
        assertEquals(2, versions.size());
        assertEquals(2, versions.get(0).getVersionNo());
        // simple-list：仅已发布且开启
        Long unpublishedId = contractTypeService.createContractType(buildSaveReqVO("UNPUB", "未发布"));
        List<ContractTypeSimpleRespVO> simpleList = contractTypeService.getContractTypeSimpleList();
        assertEquals(1, simpleList.size());
        assertEquals(typeId, simpleList.get(0).getId());
        assertNotEquals(unpublishedId, simpleList.get(0).getId());
    }

    @Test
    public void testUpdateContractType_success() {
        Long typeId = contractTypeService.createContractType(buildSaveReqVO("UPD", "旧"));
        ContractTypeSaveReqVO reqVO = buildSaveReqVO("IGNORED_CODE", "新");
        reqVO.setId(typeId);
        reqVO.setStatus(CommonStatusEnum.DISABLE.getStatus());
        reqVO.setSort(9);

        contractTypeService.updateContractType(reqVO);

        ContractTypeDO type = contractTypeMapper.selectById(typeId);
        assertEquals("UPD", type.getCode()); // code 不可改
        assertEquals("新", type.getName());
        assertEquals(CommonStatusEnum.DISABLE.getStatus(), type.getStatus());
        assertEquals(9, type.getSort());
    }

    @Test
    public void testDeleteContractType() {
        Long typeId = contractTypeService.createContractType(buildSaveReqVO("DEL", "删除"));
        ContractTypeVersionDO v1 = contractTypeVersionMapper.selectListByTypeId(typeId).get(0);
        // 被合同引用
        contractMapper.insert(new ContractDO().setTitle("t").setTypeId(typeId).setTypeVersionId(v1.getId())
                .setOwnerUserId(1L).setCurrency("CNY")
                .setLifecycleStatus(ClmLifecycleStatusEnum.DRAFT.getStatus())
                .setApprovalStatus(ClmApprovalStatusEnum.NOT_SUBMITTED.getStatus()));
        assertServiceException(() -> contractTypeService.deleteContractType(typeId), CONTRACT_TYPE_IN_USE);
        // 无引用可删
        Long typeId2 = contractTypeService.createContractType(buildSaveReqVO("DEL2", "删除2"));
        contractTypeService.deleteContractType(typeId2);
        assertNull(contractTypeMapper.selectById(typeId2));
        assertTrue(contractTypeVersionMapper.selectListByTypeId(typeId2).isEmpty());
        assertServiceException(() -> contractTypeService.deleteContractType(typeId2), CONTRACT_TYPE_NOT_EXISTS);
    }

    @Test
    public void testGetContractTypePage() {
        contractTypeService.createContractType(buildSaveReqVO("PAGE_A", "分页甲"));
        contractTypeService.createContractType(buildSaveReqVO("PAGE_B", "分页乙"));
        ContractTypePageReqVO reqVO = new ContractTypePageReqVO();
        reqVO.setCode("PAGE_A");
        PageResult<ContractTypeRespVO> pageResult = contractTypeService.getContractTypePage(reqVO);
        assertEquals(1, pageResult.getTotal());
        assertEquals("分页甲", pageResult.getList().get(0).getName());
        assertNotNull(pageResult.getList().get(0).getDraftVersionId());
    }

    // ========== 范本 ==========

    @Test
    public void testTemplateUploadAndDownload() {
        Long typeId = contractTypeService.createContractType(buildSaveReqVO("TPL", "范本类型"));
        // 未配置范本 → 下载报错
        assertServiceException(() -> contractTypeService.downloadContractTypeTemplate(typeId),
                CONTRACT_TYPE_TEMPLATE_NOT_EXISTS);
        // 上传
        byte[] content = "范本正文 v1".getBytes(StandardCharsets.UTF_8);
        contractTypeService.uploadContractTypeTemplate(typeId, "采购范本.docx", content);
        ContractTypeDO type = contractTypeMapper.selectById(typeId);
        assertEquals("采购范本.docx", type.getTemplateFileName());
        assertTrue(StrUtil.isNotBlank(type.getTemplateFileKey()));
        TypeTemplateDownloadResult result = contractTypeService.downloadContractTypeTemplate(typeId);
        assertEquals("采购范本.docx", result.getFileName());
        assertArrayEquals(content, result.getContent());
        // 替换
        byte[] content2 = "范本正文 v2".getBytes(StandardCharsets.UTF_8);
        contractTypeService.uploadContractTypeTemplate(typeId, "采购范本v2.pdf", content2);
        result = contractTypeService.downloadContractTypeTemplate(typeId);
        assertEquals("采购范本v2.pdf", result.getFileName());
        assertArrayEquals(content2, result.getContent());
        // simple-list：发布后返回 hasTemplate / description
        ContractTypeVersionDO v1 = contractTypeVersionMapper.selectListByTypeId(typeId).get(0);
        contractTypeService.publishContractTypeVersion(v1.getId());
        Long noTemplateTypeId = contractTypeService.createContractType(buildSaveReqVO("TPL2", "无范本类型"));
        contractTypeService.publishContractTypeVersion(
                contractTypeVersionMapper.selectListByTypeId(noTemplateTypeId).get(0).getId());
        List<ContractTypeSimpleRespVO> simpleList = contractTypeService.getContractTypeSimpleList();
        assertEquals(2, simpleList.size());
        ContractTypeSimpleRespVO withTemplate = simpleList.stream()
                .filter(item -> item.getId().equals(typeId)).findFirst().orElseThrow(AssertionError::new);
        assertTrue(withTemplate.getHasTemplate());
        assertEquals("desc", withTemplate.getDescription());
        ContractTypeSimpleRespVO withoutTemplate = simpleList.stream()
                .filter(item -> item.getId().equals(noTemplateTypeId)).findFirst().orElseThrow(AssertionError::new);
        assertFalse(withoutTemplate.getHasTemplate());
    }

    @Test
    public void testTemplateUpload_validation() {
        Long typeId = contractTypeService.createContractType(buildSaveReqVO("TPLV", "范本校验"));
        byte[] content = "x".getBytes(StandardCharsets.UTF_8);
        // 扩展名不允许
        assertServiceException(() -> contractTypeService.uploadContractTypeTemplate(typeId, "范本.txt", content),
                DOCUMENT_FILE_TYPE_NOT_ALLOWED, "txt");
        // 空文件
        assertServiceException(() -> contractTypeService.uploadContractTypeTemplate(typeId, "范本.docx", new byte[0]),
                DOCUMENT_FILE_EMPTY);
        // 类型不存在
        assertServiceException(() -> contractTypeService.uploadContractTypeTemplate(99999L, "范本.docx", content),
                CONTRACT_TYPE_NOT_EXISTS);
        // 未产生副作用
        assertNull(contractTypeMapper.selectById(typeId).getTemplateFileKey());
    }

    // ========== 构造对象 ==========

    private static ContractTypeSaveReqVO buildSaveReqVO(String code, String name) {
        ContractTypeSaveReqVO reqVO = new ContractTypeSaveReqVO();
        reqVO.setCode(code);
        reqVO.setName(name);
        reqVO.setDescription("desc");
        reqVO.setSort(1);
        return reqVO;
    }

}
