package cn.iocoder.yudao.module.clm.service.contract;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.clm.access.ContractAccessService;
import cn.iocoder.yudao.module.clm.controller.admin.contract.vo.*;
import cn.iocoder.yudao.module.clm.controller.admin.workflow.vo.WorkflowBindingRespVO;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractPartyDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.contracttype.ContractTypeDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.contracttype.ContractTypeVersionDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.document.DocumentVersionDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.party.PartyDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.workflow.WorkflowBindingDO;
import cn.iocoder.yudao.module.clm.dal.mysql.contract.ContractMapper;
import cn.iocoder.yudao.module.clm.dal.mysql.contract.ContractPartyMapper;
import cn.iocoder.yudao.module.clm.dal.mysql.workflow.WorkflowBindingMapper;
import cn.iocoder.yudao.module.clm.document.ClmDocumentStorage;
import cn.iocoder.yudao.module.clm.enums.audit.ClmAuditActionEnum;
import cn.iocoder.yudao.module.clm.enums.audit.ClmAuditAggregateTypeEnum;
import cn.iocoder.yudao.module.clm.enums.contract.ClmApprovalStatusEnum;
import cn.iocoder.yudao.module.clm.enums.contract.ClmContractPartyRoleEnum;
import cn.iocoder.yudao.module.clm.enums.contract.ClmContractRelationTypeEnum;
import cn.iocoder.yudao.module.clm.enums.contract.ClmLifecycleStatusEnum;
import cn.iocoder.yudao.module.clm.enums.document.ClmDocumentRoleEnum;
import cn.iocoder.yudao.module.clm.enums.document.ClmDocumentSourceTypeEnum;
import cn.iocoder.yudao.module.clm.service.audit.ClmAuditService;
import cn.iocoder.yudao.module.clm.service.contracttype.ContractFormSchemaValidator;
import cn.iocoder.yudao.module.clm.service.contracttype.ContractTypeService;
import cn.iocoder.yudao.module.clm.service.document.DocumentService;
import cn.iocoder.yudao.module.clm.service.party.PartyService;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.invalidParamException;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.*;
import static cn.iocoder.yudao.module.clm.enums.ErrorCodeConstants.*;

/**
 * CLM 合同 Service 实现类
 */
@Service
@Validated
public class ContractServiceImpl implements ContractService {

    public static final String DEFAULT_CURRENCY = "CNY";

    /**
     * 归档盖章扫描件允许的扩展名
     */
    public static final Set<String> ARCHIVE_ALLOWED_EXTENSIONS = new HashSet<>(Arrays.asList(
            "pdf", "docx", "doc", "jpg", "jpeg", "png", "zip"));

    private static final DateTimeFormatter CONTRACT_NO_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Resource
    private ContractMapper contractMapper;
    @Resource
    private ContractPartyMapper contractPartyMapper;
    @Resource
    private WorkflowBindingMapper workflowBindingMapper;

    @Resource
    private ContractTypeService contractTypeService;
    @Resource
    private PartyService partyService;
    @Resource
    private ContractParticipantService contractParticipantService;
    @Resource
    private DocumentService documentService;
    @Resource
    private ContractAccessService contractAccessService;
    @Resource
    private ClmDocumentStorage documentStorage;
    @Resource
    private ClmAuditService clmAuditService;
    @Resource
    private ContractFormSchemaValidator contractFormSchemaValidator;

    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private DeptApi deptApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createContract(ContractSaveReqVO createReqVO) {
        // 1. 解析类型版本
        ContractTypeVersionDO typeVersion = resolveTypeVersion(createReqVO.getTypeId());
        // 2. 校验签约方、扩展字段
        validateParties(createReqVO.getParties());
        Map<Long, PartyDO> partyMap = partyService.validatePartyList(
                convertSet(createReqVO.getParties(), ContractPartyItemVO::getPartyId));
        contractFormSchemaValidator.validate(typeVersion.getFormFields(), createReqVO.getCustomData());
        // 3. 插入合同
        Long ownerUserId = ObjUtil.defaultIfNull(createReqVO.getOwnerUserId(), SecurityFrameworkUtils.getLoginUserId());
        Long ownerDeptId = ObjUtil.defaultIfNull(createReqVO.getOwnerDeptId(), SecurityFrameworkUtils.getLoginUserDeptId());
        ContractDO contract = BeanUtils.toBean(createReqVO, ContractDO.class)
                .setId(null)
                .setContractNo(null)
                .setTypeVersionId(typeVersion.getId())
                .setOwnerUserId(ownerUserId)
                .setOwnerDeptId(ownerDeptId)
                .setCurrency(StrUtil.blankToDefault(createReqVO.getCurrency(), DEFAULT_CURRENCY))
                .setCustomData(createReqVO.getCustomData() == null ? new HashMap<>() : createReqVO.getCustomData())
                .setLifecycleStatus(ClmLifecycleStatusEnum.DRAFT.getStatus())
                .setApprovalStatus(ClmApprovalStatusEnum.NOT_SUBMITTED.getStatus())
                .setCurrentDocumentVersionId(null)
                .setCurrentBindingId(null);
        contractMapper.insert(contract);
        // 4. 生成合同编号回写
        String contractNo = generateContractNo(contract.getId());
        contractMapper.updateById(new ContractDO().setId(contract.getId()).setContractNo(contractNo));
        contract.setContractNo(contractNo);
        // 5. 签约方
        insertParties(contract.getId(), createReqVO.getParties(), partyMap);
        // 6. 参与人 OWNER 行
        contractParticipantService.createOwnerParticipant(contract.getId(), ownerUserId);
        // 7. 审计
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("contractNo", contractNo);
        detail.put("title", contract.getTitle());
        detail.put("typeId", contract.getTypeId());
        detail.put("typeVersionId", contract.getTypeVersionId());
        clmAuditService.record(ClmAuditAggregateTypeEnum.CONTRACT, contract.getId(), contract.getId(),
                ClmAuditActionEnum.CONTRACT_CREATE, detail);
        // 8. 类型范本生成正文 v1
        if (Boolean.TRUE.equals(createReqVO.getUseTypeTemplate())) {
            ContractTypeDO type = contractTypeService.getContractType(contract.getTypeId());
            if (type != null && StrUtil.isNotBlank(type.getTemplateFileKey())) {
                byte[] templateContent = documentStorage.load(type.getTemplateFileKey());
                documentService.uploadDocument(contract.getId(), ClmDocumentRoleEnum.MAIN.getCode(),
                        "由类型范本创建", type.getTemplateFileName(), null, templateContent);
            }
        }
        return contract.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long copyContract(ContractCopyReqVO reqVO) {
        // 1. 校验源合同可见 + 关联类型
        ContractDO source = getRequiredContract(reqVO.getSourceContractId());
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        contractAccessService.assertCanView(source, userId);
        ClmContractRelationTypeEnum relationType = ClmContractRelationTypeEnum.of(reqVO.getRelationType());
        if (relationType == null) {
            throw invalidParamException("不支持的关联类型【{}】", reqVO.getRelationType());
        }
        // 2. 按源合同类型重新取当前发布版；无发布版抛 CONTRACT_TYPE_NOT_PUBLISHED
        ContractTypeVersionDO typeVersion = resolveTypeVersion(source.getTypeId());
        // 3. customData：内部创建绕过必填校验，只按新版本 schema 过滤未知字段
        Map<String, Object> customData = filterCustomDataBySchema(typeVersion, source.getCustomData());
        // 4. 标题：缺省 = 源标题 + （复制/续签）
        String title = StrUtil.blankToDefault(reqVO.getTitle(),
                source.getTitle() + (relationType == ClmContractRelationTypeEnum.RENEWAL ? "（续签）" : "（复制）"));
        // 5. 插入新合同（owner = 当前用户）
        ContractDO contract = new ContractDO()
                .setTitle(title)
                .setTypeId(source.getTypeId())
                .setTypeVersionId(typeVersion.getId())
                .setOwnerUserId(userId)
                .setOwnerDeptId(SecurityFrameworkUtils.getLoginUserDeptId())
                .setAmount(source.getAmount())
                .setCurrency(StrUtil.blankToDefault(source.getCurrency(), DEFAULT_CURRENCY))
                .setSignDate(source.getSignDate())
                .setEffectiveDate(source.getEffectiveDate())
                .setExpiryDate(source.getExpiryDate())
                .setDescription(source.getDescription())
                .setCustomData(customData)
                .setLifecycleStatus(ClmLifecycleStatusEnum.DRAFT.getStatus())
                .setApprovalStatus(ClmApprovalStatusEnum.NOT_SUBMITTED.getStatus())
                .setCurrentDocumentVersionId(null)
                .setCurrentBindingId(null)
                .setSourceContractId(source.getId())
                .setRelationType(relationType.getCode());
        contractMapper.insert(contract);
        String contractNo = generateContractNo(contract.getId());
        contractMapper.updateById(new ContractDO().setId(contract.getId()).setContractNo(contractNo));
        contract.setContractNo(contractNo);
        // 6. 签约方快照重建
        List<ContractPartyDO> sourceParties = contractPartyMapper.selectListByContractId(source.getId());
        if (CollUtil.isNotEmpty(sourceParties)) {
            List<ContractPartyItemVO> items = convertList(sourceParties, party -> {
                ContractPartyItemVO item = new ContractPartyItemVO();
                item.setPartyId(party.getPartyId());
                item.setRoleCode(party.getRoleCode());
                item.setSort(party.getSort());
                return item;
            });
            Map<Long, PartyDO> partyMap = partyService.validatePartyList(
                    convertSet(items, ContractPartyItemVO::getPartyId));
            insertParties(contract.getId(), items, partyMap);
        }
        // 7. 参与人 OWNER 行
        contractParticipantService.createOwnerParticipant(contract.getId(), userId);
        // 8. 审计 CONTRACT_CREATE（detail 含 sourceContractId / relationType）
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("contractNo", contractNo);
        detail.put("title", contract.getTitle());
        detail.put("typeId", contract.getTypeId());
        detail.put("typeVersionId", contract.getTypeVersionId());
        detail.put("sourceContractId", source.getId());
        detail.put("sourceContractNo", source.getContractNo());
        detail.put("relationType", relationType.getCode());
        clmAuditService.record(ClmAuditAggregateTypeEnum.CONTRACT, contract.getId(), contract.getId(),
                ClmAuditActionEnum.CONTRACT_CREATE, detail);
        // 9. 复制当前正文版本 blob 为新合同 MAIN v1
        if (source.getCurrentDocumentVersionId() != null) {
            DocumentVersionDO sourceVersion = documentService.getDocumentVersion(source.getCurrentDocumentVersionId());
            if (sourceVersion != null) {
                byte[] content = documentStorage.load(sourceVersion.getFileKey());
                documentService.uploadDocument(contract.getId(), ClmDocumentRoleEnum.MAIN.getCode(),
                        "复制自 " + source.getContractNo(), sourceVersion.getFileName(),
                        sourceVersion.getMimeType(), content);
            }
        }
        return contract.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long archiveContract(Long contractId, LocalDate signDate, LocalDate effectiveDate, LocalDate expiryDate,
                                String fileName, String contentType, byte[] content) {
        // 1. 合同与权限（主体条件）校验
        ContractDO contract = getRequiredContract(contractId);
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (!contractAccessService.hasEditPrincipal(contract, userId)) {
            throw exception(CONTRACT_ACCESS_DENIED);
        }
        // 2. 状态校验：仅审批通过（approval=APPROVED 且 lifecycle=APPROVED）可归档
        if (!isArchivable(contract)) {
            throw exception(CONTRACT_ARCHIVE_NOT_ALLOWED);
        }
        // 3. 文件校验
        if (ArrayUtil.isEmpty(content)) {
            throw exception(DOCUMENT_FILE_EMPTY);
        }
        fileName = StrUtil.blankToDefault(fileName, "unknown");
        String extension = StrUtil.emptyToDefault(FileUtil.extName(fileName), "").toLowerCase();
        if (!ARCHIVE_ALLOWED_EXTENSIONS.contains(extension)) {
            throw exception(DOCUMENT_FILE_TYPE_NOT_ALLOWED, extension);
        }
        // 4. 新建 MAIN 冻结版本（MANUAL_FINAL）；审批通过的合同必有当前正文版本
        DocumentVersionDO currentVersion = documentService.getRequiredDocumentVersion(
                contract.getCurrentDocumentVersionId());
        Long versionId = documentService.createVersionFromBytes(contractId, currentVersion.getDocumentId(),
                currentVersion.getId(), fileName, contentType, content,
                ClmDocumentSourceTypeEnum.MANUAL_FINAL, "线下盖章定稿", userId, null);
        documentService.freezeDocumentVersion(versionId);
        // 5. 更新合同：日期 + lifecycle=EFFECTIVE（currentDocumentVersionId 已由 createVersionFromBytes 更新）
        contractMapper.updateById(new ContractDO()
                .setId(contractId)
                .setSignDate(signDate)
                .setEffectiveDate(effectiveDate)
                .setExpiryDate(expiryDate)
                .setLifecycleStatus(ClmLifecycleStatusEnum.EFFECTIVE.getStatus()));
        // 6. 审计 CONTRACT_ARCHIVE
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("versionId", versionId);
        detail.put("fileName", fileName);
        detail.put("signDate", signDate == null ? null : signDate.toString());
        detail.put("effectiveDate", effectiveDate == null ? null : effectiveDate.toString());
        detail.put("expiryDate", expiryDate == null ? null : expiryDate.toString());
        clmAuditService.record(ClmAuditAggregateTypeEnum.CONTRACT, contractId, contractId,
                ClmAuditActionEnum.CONTRACT_ARCHIVE, detail);
        return versionId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateContract(ContractSaveReqVO updateReqVO) {
        ContractDO contract = getRequiredContract(updateReqVO.getId());
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        contractAccessService.assertCanEdit(contract, userId);
        // 1. 类型：仅 NOT_SUBMITTED 时允许改（重新解析版本），否则忽略
        Long typeId = contract.getTypeId();
        Long typeVersionId = contract.getTypeVersionId();
        if (ClmApprovalStatusEnum.NOT_SUBMITTED.getStatus().equals(contract.getApprovalStatus())
                && updateReqVO.getTypeId() != null && !updateReqVO.getTypeId().equals(contract.getTypeId())) {
            ContractTypeVersionDO typeVersion = resolveTypeVersion(updateReqVO.getTypeId());
            typeId = updateReqVO.getTypeId();
            typeVersionId = typeVersion.getId();
        }
        ContractTypeVersionDO typeVersion = contractTypeService.getRequiredContractTypeVersion(typeVersionId);
        // 2. 校验签约方、扩展字段
        validateParties(updateReqVO.getParties());
        Map<Long, PartyDO> partyMap = partyService.validatePartyList(
                convertSet(updateReqVO.getParties(), ContractPartyItemVO::getPartyId));
        Map<String, Object> customData = updateReqVO.getCustomData() == null ? new HashMap<>() : updateReqVO.getCustomData();
        contractFormSchemaValidator.validate(typeVersion.getFormFields(), customData);
        // 3. 变更字段
        Long newOwnerUserId = ObjUtil.defaultIfNull(updateReqVO.getOwnerUserId(), contract.getOwnerUserId());
        Long newOwnerDeptId = ObjUtil.defaultIfNull(updateReqVO.getOwnerDeptId(), contract.getOwnerDeptId());
        ContractDO updateObj = new ContractDO()
                .setId(contract.getId())
                .setTitle(updateReqVO.getTitle())
                .setTypeId(typeId)
                .setTypeVersionId(typeVersionId)
                .setOwnerUserId(newOwnerUserId)
                .setOwnerDeptId(newOwnerDeptId)
                .setAmount(updateReqVO.getAmount())
                .setCurrency(StrUtil.blankToDefault(updateReqVO.getCurrency(), DEFAULT_CURRENCY))
                .setSignDate(updateReqVO.getSignDate())
                .setEffectiveDate(updateReqVO.getEffectiveDate())
                .setExpiryDate(updateReqVO.getExpiryDate())
                .setDescription(updateReqVO.getDescription())
                .setCustomData(customData);
        List<String> changedFields = diffFields(contract, updateObj);
        contractMapper.updateById(updateObj);
        // 4. 替换签约方
        List<ContractPartyDO> oldParties = contractPartyMapper.selectListByContractId(contract.getId());
        contractPartyMapper.deleteByContractId(contract.getId());
        insertParties(contract.getId(), updateReqVO.getParties(), partyMap);
        if (isPartiesChanged(oldParties, updateReqVO.getParties())) {
            changedFields.add("parties");
        }
        // 5. 负责人变更
        if (!Objects.equals(contract.getOwnerUserId(), newOwnerUserId)) {
            contractParticipantService.changeOwner(contract.getId(), contract.getOwnerUserId(), newOwnerUserId);
        }
        // 6. 审计
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("changedFields", changedFields);
        clmAuditService.record(ClmAuditAggregateTypeEnum.CONTRACT, contract.getId(), contract.getId(),
                ClmAuditActionEnum.CONTRACT_UPDATE, detail);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteContract(Long id) {
        ContractDO contract = getRequiredContract(id);
        contractAccessService.assertCanManage(contract, SecurityFrameworkUtils.getLoginUserId());
        boolean approvalOk = ClmApprovalStatusEnum.NOT_SUBMITTED.getStatus().equals(contract.getApprovalStatus())
                || ClmApprovalStatusEnum.CANCELED.getStatus().equals(contract.getApprovalStatus());
        if (!approvalOk || !ClmLifecycleStatusEnum.DRAFT.getStatus().equals(contract.getLifecycleStatus())) {
            throw exception(CONTRACT_DELETE_NOT_ALLOWED);
        }
        contractMapper.deleteById(id);
        contractPartyMapper.deleteByContractId(id);
        contractParticipantService.deleteByContractId(id);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("contractNo", contract.getContractNo());
        detail.put("title", contract.getTitle());
        clmAuditService.record(ClmAuditAggregateTypeEnum.CONTRACT, id, id,
                ClmAuditActionEnum.CONTRACT_DELETE, detail);
    }

    @Override
    public ContractDO getContract(Long id) {
        return contractMapper.selectById(id);
    }

    @Override
    public ContractDO getRequiredContract(Long id) {
        ContractDO contract = contractMapper.selectById(id);
        if (contract == null) {
            throw exception(CONTRACT_NOT_EXISTS);
        }
        return contract;
    }

    @Override
    public ContractRespVO getContractDetail(Long id) {
        ContractDO contract = getRequiredContract(id);
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        contractAccessService.assertCanView(contract, userId);
        return buildContractRespVO(contract, userId);
    }

    @Override
    public PageResult<ContractRespVO> getContractPage(ContractPageReqVO pageReqVO) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) {
            return PageResult.empty();
        }
        PageResult<ContractDO> pageResult = contractMapper.selectPage(pageReqVO, userId);
        if (CollUtil.isEmpty(pageResult.getList())) {
            return PageResult.empty(pageResult.getTotal());
        }
        List<ContractDO> contracts = pageResult.getList();
        // 批量补充类型、负责人、部门名称
        Map<Long, ContractTypeDO> typeMap = new HashMap<>();
        for (Long typeId : convertSet(contracts, ContractDO::getTypeId)) {
            ContractTypeDO type = contractTypeService.getContractType(typeId);
            if (type != null) {
                typeMap.put(typeId, type);
            }
        }
        Map<Long, ContractTypeVersionDO> versionMap = new HashMap<>();
        for (Long versionId : convertSet(contracts, ContractDO::getTypeVersionId)) {
            ContractTypeVersionDO version = contractTypeService.getContractTypeVersion(versionId);
            if (version != null) {
                versionMap.put(versionId, version);
            }
        }
        Map<Long, AdminUserRespDTO> userMap = safeMap(adminUserApi.getUserMap(
                convertSet(contracts, ContractDO::getOwnerUserId)));
        Map<Long, DeptRespDTO> deptMap = safeMap(deptApi.getDeptMap(
                convertSet(contracts, ContractDO::getOwnerDeptId, c -> c.getOwnerDeptId() != null)));
        // 来源合同（复制/续签）
        Set<Long> sourceContractIds = convertSet(contracts, ContractDO::getSourceContractId,
                c -> c.getSourceContractId() != null);
        Map<Long, ContractDO> sourceContractMap = CollUtil.isEmpty(sourceContractIds) ? Collections.emptyMap()
                : convertMap(contractMapper.selectByIds(sourceContractIds), ContractDO::getId);
        List<ContractRespVO> list = convertList(contracts, contract -> {
            ContractRespVO vo = BeanUtils.toBean(contract, ContractRespVO.class);
            fillTypeInfo(vo, typeMap.get(contract.getTypeId()), versionMap.get(contract.getTypeVersionId()));
            AdminUserRespDTO user = userMap.get(contract.getOwnerUserId());
            vo.setOwnerUserName(user == null ? null : user.getNickname());
            DeptRespDTO dept = contract.getOwnerDeptId() == null ? null : deptMap.get(contract.getOwnerDeptId());
            vo.setOwnerDeptName(dept == null ? null : dept.getName());
            fillSourceContractInfo(vo, contract.getSourceContractId() == null ? null
                    : sourceContractMap.get(contract.getSourceContractId()));
            return vo;
        });
        return new PageResult<>(list, pageResult.getTotal());
    }

    @Override
    public ContractRespVO buildContractRespVO(ContractDO contract, Long userId) {
        ContractRespVO vo = BeanUtils.toBean(contract, ContractRespVO.class);
        // 类型
        fillTypeInfo(vo, contractTypeService.getContractType(contract.getTypeId()),
                contractTypeService.getContractTypeVersion(contract.getTypeVersionId()));
        // 负责人 / 部门
        AdminUserRespDTO user = contract.getOwnerUserId() == null ? null : adminUserApi.getUser(contract.getOwnerUserId());
        vo.setOwnerUserName(user == null ? null : user.getNickname());
        DeptRespDTO dept = contract.getOwnerDeptId() == null ? null : deptApi.getDept(contract.getOwnerDeptId());
        vo.setOwnerDeptName(dept == null ? null : dept.getName());
        // 签约方
        vo.setParties(convertList(contractPartyMapper.selectListByContractId(contract.getId()), this::buildPartyRespVO));
        // 当前正文版本
        if (contract.getCurrentDocumentVersionId() != null) {
            DocumentVersionDO version = documentService.getDocumentVersion(contract.getCurrentDocumentVersionId());
            vo.setCurrentDocumentVersion(documentService.buildDocumentVersionRespVO(version));
        }
        // 当前绑定
        WorkflowBindingDO binding = contract.getCurrentBindingId() == null ? null
                : workflowBindingMapper.selectById(contract.getCurrentBindingId());
        if (binding != null) {
            WorkflowBindingRespVO bindingVO = BeanUtils.toBean(binding, WorkflowBindingRespVO.class);
            DocumentVersionDO bindingVersion = documentService.getDocumentVersion(binding.getDocumentVersionId());
            bindingVO.setDocumentVersionNo(bindingVersion == null ? null : bindingVersion.getVersionNo());
            if (NumberUtil.isLong(binding.getCreator())) {
                AdminUserRespDTO creator = adminUserApi.getUser(Long.parseLong(binding.getCreator()));
                bindingVO.setCreatorName(creator == null ? null : creator.getNickname());
            }
            vo.setCurrentBinding(bindingVO);
        }
        // 来源合同（复制/续签）
        if (contract.getSourceContractId() != null) {
            fillSourceContractInfo(vo, contractMapper.selectById(contract.getSourceContractId()));
        }
        // 权限
        vo.setPermissions(buildPermissions(contract, binding, userId));
        return vo;
    }

    @Override
    public List<ContractPartyDO> getContractPartyList(Long contractId) {
        return contractPartyMapper.selectListByContractId(contractId);
    }

    @Override
    public void validateContractForSubmit(ContractDO contract, List<ContractPartyDO> parties) {
        if (StrUtil.isBlank(contract.getTitle())) {
            throw exception(CONTRACT_TITLE_REQUIRED);
        }
        boolean hasOurSide = anyMatch(parties, p -> ClmContractPartyRoleEnum.OUR_SIDE.getCode().equals(p.getRoleCode()));
        boolean hasCounterparty = anyMatch(parties, p -> ClmContractPartyRoleEnum.COUNTERPARTY.getCode().equals(p.getRoleCode()));
        if (!hasOurSide || !hasCounterparty) {
            throw exception(CONTRACT_PARTY_REQUIRED);
        }
    }

    // ========== 私有方法 ==========

    private ContractPermissionsVO buildPermissions(ContractDO contract, WorkflowBindingDO binding, Long userId) {
        ContractPermissionsVO permissions = new ContractPermissionsVO();
        boolean canEdit = contractAccessService.canEdit(contract, userId);
        boolean canManage = contractAccessService.canManage(contract, userId);
        permissions.setCanView(contractAccessService.canView(contract, userId));
        permissions.setCanEdit(canEdit);
        permissions.setCanDownload(contractAccessService.canDownload(contract, userId));
        permissions.setCanManage(canManage);
        // canSubmit
        boolean running = ClmApprovalStatusEnum.RUNNING.getStatus().equals(contract.getApprovalStatus());
        boolean approved = ClmApprovalStatusEnum.APPROVED.getStatus().equals(contract.getApprovalStatus());
        boolean sameVersionApproved = approved && binding != null
                && Objects.equals(binding.getDocumentVersionId(), contract.getCurrentDocumentVersionId());
        permissions.setCanSubmit(contractAccessService.hasEditPrincipal(contract, userId)
                && !running
                && contract.getCurrentDocumentVersionId() != null
                && !sameVersionApproved);
        // canDelete
        boolean approvalOk = ClmApprovalStatusEnum.NOT_SUBMITTED.getStatus().equals(contract.getApprovalStatus())
                || ClmApprovalStatusEnum.CANCELED.getStatus().equals(contract.getApprovalStatus());
        permissions.setCanDelete(canManage && approvalOk
                && ClmLifecycleStatusEnum.DRAFT.getStatus().equals(contract.getLifecycleStatus()));
        // canCancelApproval
        permissions.setCanCancelApproval(running && binding != null && userId != null
                && Objects.equals(binding.getCreator(), String.valueOf(userId)));
        // canArchive：主体可编辑 && approvalStatus==APPROVED && lifecycle==APPROVED
        permissions.setCanArchive(contractAccessService.hasEditPrincipal(contract, userId) && isArchivable(contract));
        // canCopy = canView
        permissions.setCanCopy(permissions.getCanView());
        return permissions;
    }

    private boolean isArchivable(ContractDO contract) {
        return ClmApprovalStatusEnum.APPROVED.getStatus().equals(contract.getApprovalStatus())
                && ClmLifecycleStatusEnum.APPROVED.getStatus().equals(contract.getLifecycleStatus());
    }

    /**
     * 按类型版本 schema 过滤 customData 的未知字段（不校验必填，草稿允许后补）
     */
    private Map<String, Object> filterCustomDataBySchema(ContractTypeVersionDO typeVersion,
                                                         Map<String, Object> customData) {
        Map<String, Object> result = new HashMap<>();
        if (CollUtil.isEmpty(customData)) {
            return result;
        }
        Set<String> knownFields = contractFormSchemaValidator.parseRules(typeVersion.getFormFields()).keySet();
        for (Map.Entry<String, Object> entry : customData.entrySet()) {
            if (knownFields.contains(entry.getKey())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    private void fillSourceContractInfo(ContractRespVO vo, ContractDO sourceContract) {
        if (sourceContract == null) {
            return;
        }
        vo.setSourceContractTitle(sourceContract.getTitle());
        vo.setSourceContractNo(sourceContract.getContractNo());
    }

    private void fillTypeInfo(ContractRespVO vo, ContractTypeDO type, ContractTypeVersionDO version) {
        if (type != null) {
            vo.setTypeCode(type.getCode());
            vo.setTypeName(type.getName());
        }
        if (version != null) {
            vo.setTypeVersionNo(version.getVersionNo());
        }
    }

    private ContractPartyRespVO buildPartyRespVO(ContractPartyDO party) {
        // 注意：partySnapshot 在 DO 中是 JSON 字符串、在 VO 中是 Map，不能用 BeanUtils 直接拷贝
        ContractPartyRespVO vo = new ContractPartyRespVO();
        vo.setId(party.getId());
        vo.setPartyId(party.getPartyId());
        vo.setRoleCode(party.getRoleCode());
        vo.setSort(party.getSort());
        Map<String, Object> snapshot = StrUtil.isBlank(party.getPartySnapshot()) ? null
                : JsonUtils.parseMap(party.getPartySnapshot());
        vo.setPartySnapshot(snapshot);
        vo.setPartyName(snapshot == null ? null : ObjUtil.toString(snapshot.get("name")));
        return vo;
    }

    private ContractTypeVersionDO resolveTypeVersion(Long typeId) {
        ContractTypeDO type = contractTypeService.getRequiredContractType(typeId);
        if (type.getCurrentVersionId() == null) {
            throw exception(CONTRACT_TYPE_NOT_PUBLISHED);
        }
        return contractTypeService.getRequiredContractTypeVersion(type.getCurrentVersionId());
    }

    private void validateParties(List<ContractPartyItemVO> parties) {
        if (CollUtil.isEmpty(parties)) {
            throw exception(CONTRACT_PARTY_REQUIRED);
        }
        boolean hasOurSide = false;
        boolean hasCounterparty = false;
        for (ContractPartyItemVO item : parties) {
            if (!ClmContractPartyRoleEnum.contains(item.getRoleCode())) {
                throw exception(CONTRACT_PARTY_REQUIRED);
            }
            hasOurSide |= ClmContractPartyRoleEnum.OUR_SIDE.getCode().equals(item.getRoleCode());
            hasCounterparty |= ClmContractPartyRoleEnum.COUNTERPARTY.getCode().equals(item.getRoleCode());
        }
        if (!hasOurSide || !hasCounterparty) {
            throw exception(CONTRACT_PARTY_REQUIRED);
        }
    }

    private void insertParties(Long contractId, List<ContractPartyItemVO> items, Map<Long, PartyDO> partyMap) {
        int index = 0;
        for (ContractPartyItemVO item : items) {
            PartyDO party = partyMap.get(item.getPartyId());
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("name", party.getName());
            snapshot.put("unifiedCreditCode", party.getUnifiedCreditCode());
            snapshot.put("partyType", party.getPartyType());
            contractPartyMapper.insert(new ContractPartyDO()
                    .setContractId(contractId)
                    .setPartyId(item.getPartyId())
                    .setRoleCode(item.getRoleCode())
                    .setSort(ObjUtil.defaultIfNull(item.getSort(), index))
                    .setPartySnapshot(JsonUtils.toJsonString(snapshot)));
            index++;
        }
    }

    private boolean isPartiesChanged(List<ContractPartyDO> oldParties, List<ContractPartyItemVO> newParties) {
        Set<String> oldKeys = convertSet(oldParties, p -> p.getPartyId() + ":" + p.getRoleCode());
        Set<String> newKeys = convertSet(newParties, p -> p.getPartyId() + ":" + p.getRoleCode());
        return !oldKeys.equals(newKeys);
    }

    private List<String> diffFields(ContractDO oldObj, ContractDO newObj) {
        List<String> changed = new ArrayList<>();
        if (!Objects.equals(oldObj.getTitle(), newObj.getTitle())) changed.add("title");
        if (!Objects.equals(oldObj.getTypeId(), newObj.getTypeId())) changed.add("typeId");
        if (!Objects.equals(oldObj.getTypeVersionId(), newObj.getTypeVersionId())) changed.add("typeVersionId");
        if (!Objects.equals(oldObj.getOwnerUserId(), newObj.getOwnerUserId())) changed.add("ownerUserId");
        if (!Objects.equals(oldObj.getOwnerDeptId(), newObj.getOwnerDeptId())) changed.add("ownerDeptId");
        if (!isSameAmount(oldObj, newObj)) changed.add("amount");
        if (!Objects.equals(oldObj.getCurrency(), newObj.getCurrency())) changed.add("currency");
        if (!Objects.equals(oldObj.getSignDate(), newObj.getSignDate())) changed.add("signDate");
        if (!Objects.equals(oldObj.getEffectiveDate(), newObj.getEffectiveDate())) changed.add("effectiveDate");
        if (!Objects.equals(oldObj.getExpiryDate(), newObj.getExpiryDate())) changed.add("expiryDate");
        if (!Objects.equals(StrUtil.nullToEmpty(oldObj.getDescription()), StrUtil.nullToEmpty(newObj.getDescription()))) changed.add("description");
        Map<String, Object> oldData = oldObj.getCustomData() == null ? new HashMap<>() : oldObj.getCustomData();
        Map<String, Object> newData = newObj.getCustomData() == null ? new HashMap<>() : newObj.getCustomData();
        if (!Objects.equals(oldData, newData)) changed.add("customData");
        return changed;
    }

    private boolean isSameAmount(ContractDO oldObj, ContractDO newObj) {
        if (oldObj.getAmount() == null || newObj.getAmount() == null) {
            return oldObj.getAmount() == null && newObj.getAmount() == null;
        }
        return oldObj.getAmount().compareTo(newObj.getAmount()) == 0;
    }

    private String generateContractNo(Long id) {
        return "HT" + LocalDate.now().format(CONTRACT_NO_DATE_FORMAT) + "-" + String.format("%05d", id);
    }

    private static <K, V> Map<K, V> safeMap(Map<K, V> map) {
        return map == null ? Collections.emptyMap() : map;
    }

}
