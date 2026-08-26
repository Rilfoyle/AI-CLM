package cn.iocoder.yudao.module.clm.service.contract;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.clm.access.ContractAccessService;
import cn.iocoder.yudao.module.clm.controller.admin.contract.vo.ContractCopyReqVO;
import cn.iocoder.yudao.module.clm.controller.admin.contract.vo.ContractPartyItemVO;
import cn.iocoder.yudao.module.clm.controller.admin.contract.vo.ContractRespVO;
import cn.iocoder.yudao.module.clm.controller.admin.contract.vo.ContractSaveReqVO;
import cn.iocoder.yudao.module.clm.controller.admin.contracttype.vo.ContractTypeSaveReqVO;
import cn.iocoder.yudao.module.clm.controller.admin.contracttype.vo.ContractTypeVersionSaveReqVO;
import cn.iocoder.yudao.module.clm.dal.dataobject.audit.AuditEventDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractParticipantDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractPartyDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.contracttype.ContractTypeVersionDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.document.DocumentVersionDO;
import cn.iocoder.yudao.module.clm.dal.mysql.audit.AuditEventMapper;
import cn.iocoder.yudao.module.clm.dal.mysql.contract.ContractMapper;
import cn.iocoder.yudao.module.clm.dal.mysql.contract.ContractParticipantMapper;
import cn.iocoder.yudao.module.clm.dal.mysql.contract.ContractPartyMapper;
import cn.iocoder.yudao.module.clm.dal.mysql.contracttype.ContractTypeMapper;
import cn.iocoder.yudao.module.clm.dal.mysql.contracttype.ContractTypeVersionMapper;
import cn.iocoder.yudao.module.clm.dal.mysql.document.DocumentBlobMapper;
import cn.iocoder.yudao.module.clm.dal.mysql.document.DocumentVersionMapper;
import cn.iocoder.yudao.module.clm.dal.mysql.party.PartyMapper;
import cn.iocoder.yudao.module.clm.dal.dataobject.party.PartyDO;
import cn.iocoder.yudao.module.clm.document.DbDocumentStorage;
import cn.iocoder.yudao.module.clm.enums.audit.ClmAuditActionEnum;
import cn.iocoder.yudao.module.clm.enums.contract.ClmApprovalStatusEnum;
import cn.iocoder.yudao.module.clm.enums.contract.ClmContractPartyRoleEnum;
import cn.iocoder.yudao.module.clm.enums.contract.ClmContractRelationTypeEnum;
import cn.iocoder.yudao.module.clm.enums.contract.ClmLifecycleStatusEnum;
import cn.iocoder.yudao.module.clm.enums.contract.ClmParticipantRoleEnum;
import cn.iocoder.yudao.module.clm.enums.document.ClmDocumentSourceTypeEnum;
import cn.iocoder.yudao.module.clm.service.audit.ClmAuditServiceImpl;
import cn.iocoder.yudao.module.clm.service.contracttype.ContractFormSchemaValidator;
import cn.iocoder.yudao.module.clm.service.contracttype.ContractTypeServiceImpl;
import cn.iocoder.yudao.module.clm.service.document.DocumentServiceImpl;
import cn.iocoder.yudao.module.clm.service.party.PartyServiceImpl;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.filterList;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.clm.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ContractServiceImpl} 复制/续签 + 归档定稿 的单元测试
 */
@Import({ContractServiceImpl.class, ContractTypeServiceImpl.class, PartyServiceImpl.class,
        ContractParticipantServiceImpl.class, DocumentServiceImpl.class, DbDocumentStorage.class,
        ClmAuditServiceImpl.class, ContractFormSchemaValidator.class})
public class ContractCopyArchiveServiceTest extends BaseDbUnitTest {

    @Resource
    private ContractServiceImpl contractService;
    @Resource
    private ContractTypeServiceImpl contractTypeService;
    @Resource
    private DocumentServiceImpl documentService;

    @Resource
    private ContractMapper contractMapper;
    @Resource
    private ContractPartyMapper contractPartyMapper;
    @Resource
    private ContractParticipantMapper contractParticipantMapper;
    @Resource
    private ContractTypeMapper contractTypeMapper;
    @Resource
    private ContractTypeVersionMapper contractTypeVersionMapper;
    @Resource
    private DocumentVersionMapper documentVersionMapper;
    @Resource
    private DocumentBlobMapper documentBlobMapper;
    @Resource
    private PartyMapper partyMapper;
    @Resource
    private AuditEventMapper auditEventMapper;

    @MockitoBean
    private ContractAccessService contractAccessService;
    @MockitoBean
    private BpmProcessInstanceApi bpmProcessInstanceApi;
    @MockitoBean
    private AdminUserApi adminUserApi;
    @MockitoBean
    private DeptApi deptApi;

    @AfterEach
    public void clearLoginUser() {
        SecurityContextHolder.clearContext();
    }

    // ========== 复制 / 续签 ==========

    @Test
    public void testCopyContract_withPartiesAndDocumentAndRelation() {
        mockLoginUser(1L, 100L);
        // 1. 类型 v1：字段 a + 必填字段 b，发布
        Long typeId = createContractType("CPY", "复制类型");
        publishDraftVersion(typeId,
                "{\"field\":\"a\",\"title\":\"A\"}", "{\"field\":\"b\",\"title\":\"B\",\"$required\":true}");
        // 2. 源合同（含扩展字段 a/b、两个签约方）
        Long ourPartyId = insertParty("灵犀科技（杭州）有限公司");
        Long otherPartyId = insertParty("华南云创信息技术有限公司");
        Map<String, Object> customData = new HashMap<>();
        customData.put("a", "A1");
        customData.put("b", "B1");
        Long sourceId = contractService.createContract(buildCreateReqVO(typeId, "2026 年度采购合同",
                customData, ourPartyId, otherPartyId));
        // 3. 上传两个正文版本，当前版本 = v2
        documentService.uploadDocument(sourceId, null, null, "正文.docx", null,
                "v1 content".getBytes(StandardCharsets.UTF_8));
        byte[] latestContent = "v2 content latest".getBytes(StandardCharsets.UTF_8);
        documentService.uploadDocument(sourceId, null, null, "正文v2.docx", null, latestContent);
        // 4. 类型发布 v2：仅字段 a + 新必填字段 c（源 customData 的 b 应被过滤、缺 c 不报错）
        Long draftId = contractTypeService.createDraftVersion(typeId);
        updateDraftFields(draftId,
                "{\"field\":\"a\",\"title\":\"A\"}", "{\"field\":\"c\",\"title\":\"C\",\"$required\":true}");
        contractTypeService.publishContractTypeVersion(draftId);
        // 5. 以另一用户复制
        mockLoginUser(2L, 200L);
        ContractCopyReqVO reqVO = new ContractCopyReqVO();
        reqVO.setSourceContractId(sourceId);
        reqVO.setRelationType(ClmContractRelationTypeEnum.COPY.getCode());
        Long newId = contractService.copyContract(reqVO);

        verify(contractAccessService).assertCanView(any(ContractDO.class), eq(2L));
        ContractDO source = contractMapper.selectById(sourceId);
        ContractDO copied = contractMapper.selectById(newId);
        // 基本字段
        assertEquals("2026 年度采购合同（复制）", copied.getTitle());
        assertEquals(typeId, copied.getTypeId());
        assertEquals(contractTypeMapper.selectById(typeId).getCurrentVersionId(), copied.getTypeVersionId());
        assertEquals(draftId, copied.getTypeVersionId()); // 用新发布版，而不是源合同的 v1
        assertEquals(2L, copied.getOwnerUserId());
        assertEquals(200L, copied.getOwnerDeptId());
        assertEquals(0, new BigDecimal("100.50").compareTo(copied.getAmount()));
        assertEquals("CNY", copied.getCurrency());
        assertEquals(source.getSignDate(), copied.getSignDate());
        assertEquals(source.getExpiryDate(), copied.getExpiryDate());
        assertEquals("合同说明", copied.getDescription());
        assertEquals(ClmLifecycleStatusEnum.DRAFT.getStatus(), copied.getLifecycleStatus());
        assertEquals(ClmApprovalStatusEnum.NOT_SUBMITTED.getStatus(), copied.getApprovalStatus());
        assertTrue(StrUtil.isNotBlank(copied.getContractNo()));
        assertNotEquals(source.getContractNo(), copied.getContractNo());
        // 关联字段
        assertEquals(sourceId, copied.getSourceContractId());
        assertEquals(ClmContractRelationTypeEnum.COPY.getCode(), copied.getRelationType());
        // customData：按新版本 schema 过滤未知字段 b，缺必填 c 不报错
        assertEquals("A1", copied.getCustomData().get("a"));
        assertFalse(copied.getCustomData().containsKey("b"));
        assertFalse(copied.getCustomData().containsKey("c"));
        // 签约方快照重建
        List<ContractPartyDO> parties = contractPartyMapper.selectListByContractId(newId);
        assertEquals(2, parties.size());
        for (ContractPartyDO party : parties) {
            assertTrue(StrUtil.isNotBlank(party.getPartySnapshot()));
            assertTrue(party.getPartySnapshot().contains("name"));
        }
        // 参与人 OWNER = 复制人
        List<ContractParticipantDO> participants = contractParticipantMapper.selectListByContractId(newId);
        assertEquals(1, participants.size());
        assertEquals(2L, participants.get(0).getPrincipalId());
        assertEquals(ClmParticipantRoleEnum.OWNER.getCode(), participants.get(0).getRoleCode());
        // 复制当前正文版本 blob 为新合同 MAIN v1
        assertNotNull(copied.getCurrentDocumentVersionId());
        DocumentVersionDO version = documentVersionMapper.selectById(copied.getCurrentDocumentVersionId());
        assertEquals(1, version.getVersionNo());
        assertEquals("正文v2.docx", version.getFileName());
        assertEquals(ClmDocumentSourceTypeEnum.UPLOAD.getCode(), version.getSourceType());
        assertEquals("复制自 " + source.getContractNo(), version.getRemark());
        assertArrayEquals(latestContent,
                documentBlobMapper.selectById(Long.parseLong(version.getFileKey())).getContent());
        // 审计 CONTRACT_CREATE（detail 含 sourceContractId / relationType）
        List<AuditEventDO> events = filterList(auditEventMapper.selectList(),
                e -> ClmAuditActionEnum.CONTRACT_CREATE.getCode().equals(e.getAction())
                        && newId.equals(e.getContractId()));
        assertEquals(1, events.size());
        assertTrue(events.get(0).getDetailJson().contains("\"relationType\":\"COPY\""));
        assertTrue(events.get(0).getDetailJson().contains("\"sourceContractId\":" + sourceId));
    }

    @Test
    public void testCopyContract_titleDefaultAndRenewal() {
        mockLoginUser(1L, 100L);
        Long typeId = createContractType("RNW", "续签类型");
        publishDraftVersion(typeId, "{\"field\":\"a\",\"title\":\"A\"}");
        Long ourPartyId = insertParty("我方主体");
        Long otherPartyId = insertParty("相对方主体");
        Long sourceId = contractService.createContract(buildCreateReqVO(typeId, "服务器维保合同",
                null, ourPartyId, otherPartyId));

        // 1. 续签：缺省标题 = 源标题 +（续签）；源合同无正文则不生成版本
        ContractCopyReqVO renewReqVO = new ContractCopyReqVO();
        renewReqVO.setSourceContractId(sourceId);
        renewReqVO.setRelationType(ClmContractRelationTypeEnum.RENEWAL.getCode());
        Long renewId = contractService.copyContract(renewReqVO);
        ContractDO renewed = contractMapper.selectById(renewId);
        assertEquals("服务器维保合同（续签）", renewed.getTitle());
        assertEquals(ClmContractRelationTypeEnum.RENEWAL.getCode(), renewed.getRelationType());
        assertNull(renewed.getCurrentDocumentVersionId());
        assertTrue(documentVersionMapper.selectListByContractId(renewId).isEmpty());
        // 2. 指定标题优先
        ContractCopyReqVO titledReqVO = new ContractCopyReqVO();
        titledReqVO.setSourceContractId(sourceId);
        titledReqVO.setRelationType(ClmContractRelationTypeEnum.COPY.getCode());
        titledReqVO.setTitle("自定义标题");
        Long titledId = contractService.copyContract(titledReqVO);
        assertEquals("自定义标题", contractMapper.selectById(titledId).getTitle());
        // 3. 不支持的关联类型
        ContractCopyReqVO invalidReqVO = new ContractCopyReqVO();
        invalidReqVO.setSourceContractId(sourceId);
        invalidReqVO.setRelationType("MERGE");
        assertThrows(ServiceException.class, () -> contractService.copyContract(invalidReqVO));
        // 4. 源合同不存在
        ContractCopyReqVO notExistsReqVO = new ContractCopyReqVO();
        notExistsReqVO.setSourceContractId(99999L);
        notExistsReqVO.setRelationType(ClmContractRelationTypeEnum.COPY.getCode());
        assertServiceException(() -> contractService.copyContract(notExistsReqVO), CONTRACT_NOT_EXISTS);
    }

    // ========== 归档定稿 ==========

    @Test
    public void testArchiveContract_success() {
        mockLoginUser(1L, 100L);
        when(contractAccessService.hasEditPrincipal(any(), any())).thenReturn(true);
        when(contractAccessService.canView(any(), any())).thenReturn(true);
        Long contractId = prepareApprovedContract("ARC", "归档类型");
        // 归档前权限：canArchive / canCopy
        ContractRespVO before = contractService.buildContractRespVO(contractMapper.selectById(contractId), 1L);
        assertTrue(before.getPermissions().getCanArchive());
        assertTrue(before.getPermissions().getCanCopy());

        byte[] scanContent = "盖章扫描件".getBytes(StandardCharsets.UTF_8);
        LocalDate signDate = LocalDate.of(2026, 8, 20);
        LocalDate effectiveDate = LocalDate.of(2026, 9, 1);
        LocalDate expiryDate = LocalDate.of(2027, 8, 31);
        Long versionId = contractService.archiveContract(contractId, signDate, effectiveDate, expiryDate,
                "盖章件.pdf", "application/pdf", scanContent);

        // MANUAL_FINAL 冻结版本
        DocumentVersionDO version = documentVersionMapper.selectById(versionId);
        assertEquals(2, version.getVersionNo());
        assertEquals(ClmDocumentSourceTypeEnum.MANUAL_FINAL.getCode(), version.getSourceType());
        assertTrue(version.getFrozen());
        assertEquals("线下盖章定稿", version.getRemark());
        assertEquals("盖章件.pdf", version.getFileName());
        assertArrayEquals(scanContent,
                documentBlobMapper.selectById(Long.parseLong(version.getFileKey())).getContent());
        // 合同：日期 + lifecycle=EFFECTIVE + 当前正文指针
        ContractDO contract = contractMapper.selectById(contractId);
        assertEquals(ClmLifecycleStatusEnum.EFFECTIVE.getStatus(), contract.getLifecycleStatus());
        assertEquals(ClmApprovalStatusEnum.APPROVED.getStatus(), contract.getApprovalStatus());
        assertEquals(signDate, contract.getSignDate());
        assertEquals(effectiveDate, contract.getEffectiveDate());
        assertEquals(expiryDate, contract.getExpiryDate());
        assertEquals(versionId, contract.getCurrentDocumentVersionId());
        // 审计 CONTRACT_ARCHIVE
        List<AuditEventDO> events = filterList(auditEventMapper.selectList(),
                e -> ClmAuditActionEnum.CONTRACT_ARCHIVE.getCode().equals(e.getAction()));
        assertEquals(1, events.size());
        assertEquals(contractId, events.get(0).getContractId());
        assertTrue(events.get(0).getDetailJson().contains("\"versionId\":" + versionId));
        // 归档后 canArchive = false
        ContractRespVO after = contractService.buildContractRespVO(contract, 1L);
        assertFalse(after.getPermissions().getCanArchive());
    }

    @Test
    public void testArchiveContract_rejected() {
        mockLoginUser(1L, 100L);
        when(contractAccessService.hasEditPrincipal(any(), any())).thenReturn(true);
        Long typeId = createContractType("ARJ", "归档驳回类型");
        publishDraftVersion(typeId, "{\"field\":\"a\",\"title\":\"A\"}");
        Long contractId = contractService.createContract(buildCreateReqVO(typeId, "待归档合同", null,
                insertParty("我方"), insertParty("对方")));
        documentService.uploadDocument(contractId, null, null, "正文.docx", null,
                "content".getBytes(StandardCharsets.UTF_8));
        byte[] scanContent = "scan".getBytes(StandardCharsets.UTF_8);

        // 1. 未提交（草稿）不允许
        assertServiceException(() -> contractService.archiveContract(contractId, null, null, null,
                "盖章件.pdf", null, scanContent), CONTRACT_ARCHIVE_NOT_ALLOWED);
        // 2. 审批中不允许
        contractMapper.updateById(new ContractDO().setId(contractId)
                .setApprovalStatus(ClmApprovalStatusEnum.RUNNING.getStatus())
                .setLifecycleStatus(ClmLifecycleStatusEnum.APPROVED.getStatus()));
        assertServiceException(() -> contractService.archiveContract(contractId, null, null, null,
                "盖章件.pdf", null, scanContent), CONTRACT_ARCHIVE_NOT_ALLOWED);
        // 3. 已生效（重复归档）不允许
        contractMapper.updateById(new ContractDO().setId(contractId)
                .setApprovalStatus(ClmApprovalStatusEnum.APPROVED.getStatus())
                .setLifecycleStatus(ClmLifecycleStatusEnum.EFFECTIVE.getStatus()));
        assertServiceException(() -> contractService.archiveContract(contractId, null, null, null,
                "盖章件.pdf", null, scanContent), CONTRACT_ARCHIVE_NOT_ALLOWED);
        // 4. 状态满足但主体条件不满足
        contractMapper.updateById(new ContractDO().setId(contractId)
                .setLifecycleStatus(ClmLifecycleStatusEnum.APPROVED.getStatus()));
        when(contractAccessService.hasEditPrincipal(any(), any())).thenReturn(false);
        assertServiceException(() -> contractService.archiveContract(contractId, null, null, null,
                "盖章件.pdf", null, scanContent), CONTRACT_ACCESS_DENIED);
        // 5. 文件类型 / 空文件校验
        when(contractAccessService.hasEditPrincipal(any(), any())).thenReturn(true);
        assertServiceException(() -> contractService.archiveContract(contractId, null, null, null,
                "evil.exe", null, scanContent), DOCUMENT_FILE_TYPE_NOT_ALLOWED, "exe");
        assertServiceException(() -> contractService.archiveContract(contractId, null, null, null,
                "盖章件.pdf", null, new byte[0]), DOCUMENT_FILE_EMPTY);
        // 6. 全程未产生归档副作用
        ContractDO contract = contractMapper.selectById(contractId);
        assertEquals(ClmLifecycleStatusEnum.APPROVED.getStatus(), contract.getLifecycleStatus());
        assertEquals(1, documentVersionMapper.selectListByContractId(contractId).size());
        assertTrue(filterList(auditEventMapper.selectList(),
                e -> ClmAuditActionEnum.CONTRACT_ARCHIVE.getCode().equals(e.getAction())).isEmpty());
    }

    // ========== 构造对象 ==========

    private static void mockLoginUser(Long userId, Long deptId) {
        LoginUser loginUser = new LoginUser();
        loginUser.setId(userId);
        loginUser.setUserType(UserTypeEnum.ADMIN.getValue());
        loginUser.setTenantId(1L);
        Map<String, String> info = new HashMap<>();
        info.put(LoginUser.INFO_KEY_DEPT_ID, String.valueOf(deptId));
        loginUser.setInfo(info);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, Collections.emptyList()));
    }

    private Long createContractType(String code, String name) {
        ContractTypeSaveReqVO reqVO = new ContractTypeSaveReqVO();
        reqVO.setCode(code);
        reqVO.setName(name);
        reqVO.setDescription("desc");
        reqVO.setSort(1);
        return contractTypeService.createContractType(reqVO);
    }

    private void publishDraftVersion(Long typeId, String... formFields) {
        ContractTypeVersionDO draft = contractTypeVersionMapper.selectListByTypeId(typeId).get(0);
        updateDraftFields(draft.getId(), formFields);
        contractTypeService.publishContractTypeVersion(draft.getId());
    }

    private void updateDraftFields(Long versionId, String... formFields) {
        ContractTypeVersionSaveReqVO reqVO = new ContractTypeVersionSaveReqVO();
        reqVO.setId(versionId);
        reqVO.setFormFields(Arrays.asList(formFields));
        reqVO.setProcessDefinitionKey("clm_contract_approval_v1");
        reqVO.setRemark("");
        contractTypeService.updateContractTypeVersion(reqVO);
    }

    private Long insertParty(String name) {
        PartyDO party = new PartyDO()
                .setName(name)
                .setPartyType(1)
                .setUnifiedCreditCode("91330100MA27XW0000")
                .setInternalFlag(false)
                .setStatus(0);
        partyMapper.insert(party);
        return party.getId();
    }

    private ContractSaveReqVO buildCreateReqVO(Long typeId, String title, Map<String, Object> customData,
                                               Long ourPartyId, Long otherPartyId) {
        ContractSaveReqVO reqVO = new ContractSaveReqVO();
        reqVO.setTitle(title);
        reqVO.setTypeId(typeId);
        reqVO.setAmount(new BigDecimal("100.50"));
        reqVO.setSignDate(LocalDate.of(2026, 1, 10));
        reqVO.setExpiryDate(LocalDate.of(2026, 12, 31));
        reqVO.setDescription("合同说明");
        reqVO.setCustomData(customData);
        ContractPartyItemVO our = new ContractPartyItemVO();
        our.setPartyId(ourPartyId);
        our.setRoleCode(ClmContractPartyRoleEnum.OUR_SIDE.getCode());
        ContractPartyItemVO other = new ContractPartyItemVO();
        other.setPartyId(otherPartyId);
        other.setRoleCode(ClmContractPartyRoleEnum.COUNTERPARTY.getCode());
        reqVO.setParties(Arrays.asList(our, other));
        return reqVO;
    }

    /**
     * 创建一个 approvalStatus=APPROVED、lifecycle=APPROVED、含正文 v1 的合同
     */
    private Long prepareApprovedContract(String typeCode, String typeName) {
        Long typeId = createContractType(typeCode, typeName);
        publishDraftVersion(typeId, "{\"field\":\"a\",\"title\":\"A\"}");
        Long contractId = contractService.createContract(buildCreateReqVO(typeId, "已审批合同", null,
                insertParty("我方"), insertParty("对方")));
        documentService.uploadDocument(contractId, null, null, "正文.docx", null,
                "approved content".getBytes(StandardCharsets.UTF_8));
        contractMapper.updateById(new ContractDO().setId(contractId)
                .setApprovalStatus(ClmApprovalStatusEnum.APPROVED.getStatus())
                .setLifecycleStatus(ClmLifecycleStatusEnum.APPROVED.getStatus()));
        return contractId;
    }

}
