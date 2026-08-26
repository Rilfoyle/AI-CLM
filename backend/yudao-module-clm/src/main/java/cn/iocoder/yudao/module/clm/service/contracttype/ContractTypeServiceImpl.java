package cn.iocoder.yudao.module.clm.service.contracttype;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.clm.controller.admin.contracttype.vo.*;
import cn.iocoder.yudao.module.clm.dal.dataobject.contracttype.ContractTypeDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.contracttype.ContractTypeVersionDO;
import cn.iocoder.yudao.module.clm.dal.mysql.contract.ContractMapper;
import cn.iocoder.yudao.module.clm.dal.mysql.contracttype.ContractTypeMapper;
import cn.iocoder.yudao.module.clm.dal.mysql.contracttype.ContractTypeVersionMapper;
import cn.iocoder.yudao.module.clm.document.ClmDocumentStorage;
import cn.iocoder.yudao.module.clm.enums.audit.ClmAuditActionEnum;
import cn.iocoder.yudao.module.clm.enums.audit.ClmAuditAggregateTypeEnum;
import cn.iocoder.yudao.module.clm.enums.contracttype.ClmTypeVersionStatusEnum;
import cn.iocoder.yudao.module.clm.service.audit.ClmAuditService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.invalidParamException;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertList;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertSet;
import static cn.iocoder.yudao.module.clm.enums.ErrorCodeConstants.*;

/**
 * CLM 合同类型 Service 实现类
 */
@Service
@Validated
public class ContractTypeServiceImpl implements ContractTypeService {

    /**
     * 默认的审批流程定义 KEY
     */
    public static final String DEFAULT_PROCESS_DEFINITION_KEY = "clm_contract_approval_v1";

    /**
     * 范本文件允许的扩展名
     */
    public static final Set<String> TEMPLATE_ALLOWED_EXTENSIONS = new HashSet<>(Arrays.asList("docx", "doc", "pdf"));
    /**
     * 范本文件最大大小（MB）
     */
    public static final int TEMPLATE_MAX_FILE_SIZE_MB = 50;
    public static final long TEMPLATE_MAX_FILE_SIZE = TEMPLATE_MAX_FILE_SIZE_MB * 1024L * 1024L;

    @Resource
    private ContractTypeMapper contractTypeMapper;
    @Resource
    private ContractTypeVersionMapper contractTypeVersionMapper;
    @Resource
    private ContractMapper contractMapper;

    @Resource
    private ClmDocumentStorage documentStorage;
    @Resource
    private ClmAuditService clmAuditService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createContractType(ContractTypeSaveReqVO createReqVO) {
        // 1. 校验编码非空且唯一（update 时 code 可不传、且忽略）
        if (StrUtil.isBlank(createReqVO.getCode())) {
            throw invalidParamException("类型编码不能为空");
        }
        validateCodeUnique(null, createReqVO.getCode());
        // 2. 插入类型
        ContractTypeDO type = BeanUtils.toBean(createReqVO, ContractTypeDO.class)
                .setId(null)
                .setStatus(ObjUtil.defaultIfNull(createReqVO.getStatus(), CommonStatusEnum.ENABLE.getStatus()))
                .setSort(ObjUtil.defaultIfNull(createReqVO.getSort(), 0))
                .setDescription(StrUtil.nullToEmpty(createReqVO.getDescription()))
                .setCurrentVersionId(null);
        contractTypeMapper.insert(type);
        // 3. 创建版本 1（草稿）
        ContractTypeVersionDO version = new ContractTypeVersionDO()
                .setTypeId(type.getId())
                .setVersionNo(1)
                .setStatus(ClmTypeVersionStatusEnum.DRAFT.getStatus())
                .setFormConf(null)
                .setFormFields(new ArrayList<>())
                .setProcessDefinitionKey(StrUtil.blankToDefault(createReqVO.getProcessDefinitionKey(),
                        DEFAULT_PROCESS_DEFINITION_KEY))
                .setRemark("");
        contractTypeVersionMapper.insert(version);
        return type.getId();
    }

    @Override
    public void updateContractType(ContractTypeSaveReqVO updateReqVO) {
        ContractTypeDO type = getRequiredContractType(updateReqVO.getId());
        ContractTypeDO updateObj = new ContractTypeDO()
                .setId(type.getId())
                .setName(updateReqVO.getName())
                .setDescription(updateReqVO.getDescription())
                .setStatus(updateReqVO.getStatus())
                .setSort(updateReqVO.getSort());
        contractTypeMapper.updateById(updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteContractType(Long id) {
        ContractTypeDO type = getRequiredContractType(id);
        if (contractMapper.selectCountByTypeId(type.getId()) > 0) {
            throw exception(CONTRACT_TYPE_IN_USE);
        }
        contractTypeMapper.deleteById(id);
        List<ContractTypeVersionDO> versions = contractTypeVersionMapper.selectListByTypeId(id);
        if (CollUtil.isNotEmpty(versions)) {
            contractTypeVersionMapper.deleteByIds(convertList(versions, ContractTypeVersionDO::getId));
        }
    }

    @Override
    public ContractTypeDO getContractType(Long id) {
        return contractTypeMapper.selectById(id);
    }

    @Override
    public ContractTypeDO getRequiredContractType(Long id) {
        ContractTypeDO type = contractTypeMapper.selectById(id);
        if (type == null) {
            throw exception(CONTRACT_TYPE_NOT_EXISTS);
        }
        return type;
    }

    @Override
    public ContractTypeRespVO getContractTypeDetail(Long id) {
        ContractTypeDO type = getRequiredContractType(id);
        return buildRespVOList(Collections.singletonList(type)).get(0);
    }

    @Override
    public PageResult<ContractTypeRespVO> getContractTypePage(ContractTypePageReqVO pageReqVO) {
        PageResult<ContractTypeDO> pageResult = contractTypeMapper.selectPage(pageReqVO);
        return new PageResult<>(buildRespVOList(pageResult.getList()), pageResult.getTotal());
    }

    @Override
    public List<ContractTypeSimpleRespVO> getContractTypeSimpleList() {
        List<ContractTypeDO> list = contractTypeMapper.selectListByStatusAndPublished(
                CommonStatusEnum.ENABLE.getStatus());
        return convertList(list, type -> {
            ContractTypeSimpleRespVO vo = BeanUtils.toBean(type, ContractTypeSimpleRespVO.class);
            vo.setHasTemplate(StrUtil.isNotBlank(type.getTemplateFileKey()));
            return vo;
        });
    }

    // ========== 范本 ==========

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void uploadContractTypeTemplate(Long typeId, String fileName, byte[] content) {
        ContractTypeDO type = getRequiredContractType(typeId);
        // 1. 文件校验
        if (ArrayUtil.isEmpty(content)) {
            throw exception(DOCUMENT_FILE_EMPTY);
        }
        if (content.length > TEMPLATE_MAX_FILE_SIZE) {
            throw exception(DOCUMENT_FILE_TOO_LARGE, TEMPLATE_MAX_FILE_SIZE_MB);
        }
        fileName = StrUtil.blankToDefault(fileName, "unknown");
        String extension = StrUtil.emptyToDefault(FileUtil.extName(fileName), "").toLowerCase();
        if (!TEMPLATE_ALLOWED_EXTENSIONS.contains(extension)) {
            throw exception(DOCUMENT_FILE_TYPE_NOT_ALLOWED, extension);
        }
        // 2. 存储并回写
        String fileKey = documentStorage.store(content, DigestUtil.sha256Hex(content));
        contractTypeMapper.updateById(new ContractTypeDO()
                .setId(type.getId())
                .setTemplateFileKey(fileKey)
                .setTemplateFileName(fileName));
    }

    @Override
    public TypeTemplateDownloadResult downloadContractTypeTemplate(Long typeId) {
        ContractTypeDO type = getRequiredContractType(typeId);
        if (StrUtil.isBlank(type.getTemplateFileKey())) {
            throw exception(CONTRACT_TYPE_TEMPLATE_NOT_EXISTS);
        }
        byte[] content = documentStorage.load(type.getTemplateFileKey());
        return new TypeTemplateDownloadResult(
                StrUtil.blankToDefault(type.getTemplateFileName(), "template"), content);
    }

    private List<ContractTypeRespVO> buildRespVOList(List<ContractTypeDO> types) {
        if (CollUtil.isEmpty(types)) {
            return new ArrayList<>();
        }
        List<ContractTypeVersionDO> versions = contractTypeVersionMapper.selectListByTypeIds(
                convertSet(types, ContractTypeDO::getId));
        Map<Long, ContractTypeVersionDO> versionMap = new HashMap<>();
        Map<Long, ContractTypeVersionDO> draftMap = new HashMap<>();
        for (ContractTypeVersionDO version : versions) {
            versionMap.put(version.getId(), version);
            if (ClmTypeVersionStatusEnum.DRAFT.getStatus().equals(version.getStatus())) {
                draftMap.put(version.getTypeId(), version);
            }
        }
        return convertList(types, type -> {
            ContractTypeRespVO vo = BeanUtils.toBean(type, ContractTypeRespVO.class);
            ContractTypeVersionDO current = type.getCurrentVersionId() == null ? null
                    : versionMap.get(type.getCurrentVersionId());
            vo.setCurrentVersionNo(current == null ? null : current.getVersionNo());
            ContractTypeVersionDO draft = draftMap.get(type.getId());
            vo.setDraftVersionId(draft == null ? null : draft.getId());
            return vo;
        });
    }

    // ========== 版本 ==========

    @Override
    public List<ContractTypeVersionDO> getContractTypeVersionList(Long typeId) {
        return contractTypeVersionMapper.selectListByTypeId(typeId);
    }

    @Override
    public ContractTypeVersionDO getContractTypeVersion(Long id) {
        return contractTypeVersionMapper.selectById(id);
    }

    @Override
    public ContractTypeVersionDO getRequiredContractTypeVersion(Long id) {
        ContractTypeVersionDO version = contractTypeVersionMapper.selectById(id);
        if (version == null) {
            throw exception(CONTRACT_TYPE_VERSION_NOT_EXISTS);
        }
        return version;
    }

    @Override
    public void updateContractTypeVersion(ContractTypeVersionSaveReqVO updateReqVO) {
        ContractTypeVersionDO version = getRequiredContractTypeVersion(updateReqVO.getId());
        if (!ClmTypeVersionStatusEnum.DRAFT.getStatus().equals(version.getStatus())) {
            throw exception(CONTRACT_TYPE_VERSION_NOT_DRAFT);
        }
        ContractTypeVersionDO updateObj = new ContractTypeVersionDO()
                .setId(version.getId())
                .setFormConf(updateReqVO.getFormConf())
                .setFormFields(updateReqVO.getFormFields() == null ? new ArrayList<>() : updateReqVO.getFormFields())
                .setProcessDefinitionKey(updateReqVO.getProcessDefinitionKey())
                .setRemark(updateReqVO.getRemark());
        contractTypeVersionMapper.updateById(updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishContractTypeVersion(Long id) {
        ContractTypeVersionDO version = getRequiredContractTypeVersion(id);
        if (!ClmTypeVersionStatusEnum.DRAFT.getStatus().equals(version.getStatus())) {
            throw exception(CONTRACT_TYPE_VERSION_NOT_DRAFT);
        }
        ContractTypeDO type = getRequiredContractType(version.getTypeId());
        LocalDateTime now = LocalDateTime.now();
        contractTypeVersionMapper.updateById(new ContractTypeVersionDO()
                .setId(version.getId())
                .setStatus(ClmTypeVersionStatusEnum.PUBLISHED.getStatus())
                .setPublishedTime(now));
        contractTypeMapper.updateById(new ContractTypeDO()
                .setId(type.getId())
                .setCurrentVersionId(version.getId()));
        // 审计
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("versionId", version.getId());
        detail.put("versionNo", version.getVersionNo());
        detail.put("processDefinitionKey", version.getProcessDefinitionKey());
        clmAuditService.record(ClmAuditAggregateTypeEnum.CONTRACT_TYPE, type.getId(), null,
                ClmAuditActionEnum.TYPE_PUBLISH, detail);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createDraftVersion(Long typeId) {
        ContractTypeDO type = getRequiredContractType(typeId);
        if (contractTypeVersionMapper.selectByTypeIdAndStatus(typeId,
                ClmTypeVersionStatusEnum.DRAFT.getStatus()) != null) {
            throw exception(CONTRACT_TYPE_DRAFT_EXISTS);
        }
        ContractTypeVersionDO current = type.getCurrentVersionId() == null ? null
                : contractTypeVersionMapper.selectById(type.getCurrentVersionId());
        ContractTypeVersionDO latest = contractTypeVersionMapper.selectLatestByTypeId(typeId);
        int nextNo = latest == null ? 1 : latest.getVersionNo() + 1;
        ContractTypeVersionDO draft = new ContractTypeVersionDO()
                .setTypeId(typeId)
                .setVersionNo(nextNo)
                .setStatus(ClmTypeVersionStatusEnum.DRAFT.getStatus())
                .setFormConf(current == null ? null : current.getFormConf())
                .setFormFields(current == null || current.getFormFields() == null
                        ? new ArrayList<>() : new ArrayList<>(current.getFormFields()))
                .setProcessDefinitionKey(current == null ? DEFAULT_PROCESS_DEFINITION_KEY
                        : current.getProcessDefinitionKey())
                .setRemark("");
        contractTypeVersionMapper.insert(draft);
        return draft.getId();
    }

    // ========== 私有方法 ==========

    private void validateCodeUnique(Long id, String code) {
        ContractTypeDO type = contractTypeMapper.selectByCode(code);
        if (type == null) {
            return;
        }
        if (id == null || !type.getId().equals(id)) {
            throw exception(CONTRACT_TYPE_CODE_DUPLICATE, code);
        }
    }

}
