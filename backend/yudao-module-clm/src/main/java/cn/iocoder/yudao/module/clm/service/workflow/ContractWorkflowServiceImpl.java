package cn.iocoder.yudao.module.clm.service.workflow;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService;
import cn.iocoder.yudao.module.clm.access.ContractAccessService;
import cn.iocoder.yudao.module.clm.controller.admin.contract.vo.ContractApprovalPreviewRespVO;
import cn.iocoder.yudao.module.clm.controller.admin.contract.vo.ContractRespVO;
import cn.iocoder.yudao.module.clm.controller.admin.contract.vo.ContractSubmitReqVO;
import cn.iocoder.yudao.module.clm.controller.admin.workflow.vo.WorkflowBindingDetailRespVO;
import cn.iocoder.yudao.module.clm.controller.admin.workflow.vo.WorkflowBindingRespVO;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractPartyDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.contracttype.ContractTypeDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.contracttype.ContractTypeVersionDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.document.DocumentVersionDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.workflow.WorkflowBindingDO;
import cn.iocoder.yudao.module.clm.dal.mysql.contract.ContractMapper;
import cn.iocoder.yudao.module.clm.dal.mysql.workflow.WorkflowBindingMapper;
import cn.iocoder.yudao.module.clm.document.ClmDocumentStorage;
import cn.iocoder.yudao.module.clm.enums.audit.ClmAuditActionEnum;
import cn.iocoder.yudao.module.clm.enums.audit.ClmAuditAggregateTypeEnum;
import cn.iocoder.yudao.module.clm.enums.contract.ClmApprovalStatusEnum;
import cn.iocoder.yudao.module.clm.enums.contract.ClmLifecycleStatusEnum;
import cn.iocoder.yudao.module.clm.enums.workflow.ClmWorkflowBindingStatusEnum;
import cn.iocoder.yudao.module.clm.service.audit.ClmAuditService;
import cn.iocoder.yudao.module.clm.service.contract.ContractService;
import cn.iocoder.yudao.module.clm.service.contracttype.ContractFormSchemaValidator;
import cn.iocoder.yudao.module.clm.service.contracttype.ContractTypeService;
import cn.iocoder.yudao.module.clm.service.document.DocumentService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertList;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertSet;
import static cn.iocoder.yudao.module.clm.enums.ErrorCodeConstants.*;

/**
 * CLM 合同审批流程 Service 实现类
 */
@Service
@Validated
@Slf4j
public class ContractWorkflowServiceImpl implements ContractWorkflowService {

    public static final String ACTOR_BPM = "BPM";

    @Resource
    private WorkflowBindingMapper workflowBindingMapper;
    @Resource
    private ContractMapper contractMapper;

    @Resource
    private ContractService contractService;
    @Resource
    private ContractTypeService contractTypeService;
    @Resource
    private DocumentService documentService;
    @Resource
    private ContractAccessService contractAccessService;
    @Resource
    private ClmAuditService clmAuditService;
    @Resource
    private ContractFormSchemaValidator contractFormSchemaValidator;
    @Resource
    private ClmDocumentStorage documentStorage;

    @Resource
    private BpmProcessInstanceApi bpmProcessInstanceApi;
    @Resource
    private BpmProcessDefinitionService bpmProcessDefinitionService;
    @Resource
    private AdminUserApi adminUserApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submit(ContractSubmitReqVO reqVO) {
        // 1. 合同与主体权限
        ContractDO contract = contractService.getRequiredContract(reqVO.getId());
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        contractAccessService.assertCanSubmit(contract, userId);
        // 2. 状态校验
        if (ClmApprovalStatusEnum.RUNNING.getStatus().equals(contract.getApprovalStatus())) {
            throw exception(CONTRACT_APPROVAL_RUNNING);
        }
        if (!ClmLifecycleStatusEnum.DRAFT.getStatus().equals(contract.getLifecycleStatus())
                && !ClmLifecycleStatusEnum.APPROVED.getStatus().equals(contract.getLifecycleStatus())) {
            throw exception(CONTRACT_STATUS_NOT_ALLOW_SUBMIT);
        }
        if (contract.getCurrentDocumentVersionId() == null) {
            throw exception(CONTRACT_MAIN_DOCUMENT_REQUIRED);
        }
        WorkflowBindingDO currentBinding = contract.getCurrentBindingId() == null ? null
                : workflowBindingMapper.selectById(contract.getCurrentBindingId());
        if (ClmApprovalStatusEnum.APPROVED.getStatus().equals(contract.getApprovalStatus())
                && currentBinding != null
                && Objects.equals(currentBinding.getDocumentVersionId(), contract.getCurrentDocumentVersionId())) {
            throw exception(CONTRACT_NOTHING_TO_APPROVE);
        }
        // 3. 必填校验
        List<ContractPartyDO> parties = contractService.getContractPartyList(contract.getId());
        contractService.validateContractForSubmit(contract, parties);
        ContractTypeVersionDO typeVersion = contractTypeService.getRequiredContractTypeVersion(contract.getTypeVersionId());
        contractFormSchemaValidator.validate(typeVersion.getFormFields(), contract.getCustomData());
        // 4. 正文版本校验和 + 冻结
        DocumentVersionDO version = documentService.getRequiredDocumentVersion(contract.getCurrentDocumentVersionId());
        byte[] bytes = documentStorage.load(version.getFileKey());
        if (!StrUtil.equalsIgnoreCase(DigestUtil.sha256Hex(bytes), version.getChecksumSha256())) {
            throw exception(DOCUMENT_CHECKSUM_MISMATCH);
        }
        if (!BooleanUtil.isTrue(version.getFrozen())) {
            documentService.freezeDocumentVersion(version.getId());
        }
        // 5. 流程定义 KEY
        String key = typeVersion.getProcessDefinitionKey();
        // 6. 插入 binding（PREPARING）
        ContractTypeDO type = contractTypeService.getContractType(contract.getTypeId());
        WorkflowBindingDO binding = new WorkflowBindingDO()
                .setContractId(contract.getId())
                .setPurpose(WorkflowBindingDO.PURPOSE_APPROVAL)
                .setProcessDefinitionKey(key)
                .setDocumentVersionId(version.getId())
                .setContractTypeVersionId(typeVersion.getId())
                .setChecksumSha256(version.getChecksumSha256())
                .setFormSnapshot(JsonUtils.toJsonString(buildFormSnapshot(contract, type, parties, version, reqVO.getRemark())))
                .setStatus(ClmWorkflowBindingStatusEnum.PREPARING.getStatus())
                .setResultReason("");
        workflowBindingMapper.insert(binding);
        // 7. 流程变量
        Map<String, Object> variables = new HashMap<>();
        variables.put("contractId", contract.getId());
        variables.put("bindingId", binding.getId());
        variables.put("amount", contract.getAmount() == null ? 0D : contract.getAmount().doubleValue());
        variables.put("ownerDeptId", contract.getOwnerDeptId());
        variables.put("contractTypeCode", type == null ? null : type.getCode());
        variables.put("contractTitle", contract.getTitle());
        // 8. 发起 BPM 流程
        String processInstanceId = bpmProcessInstanceApi.createProcessInstance(userId,
                new BpmProcessInstanceCreateReqDTO().setProcessDefinitionKey(key)
                        .setVariables(variables).setBusinessKey(String.valueOf(binding.getId()))
                        .setStartUserSelectAssignees(reqVO.getStartUserSelectAssignees()));
        // 9. 更新 binding 与 contract
        String processDefinitionId = null;
        ProcessDefinition definition = bpmProcessDefinitionService.getActiveProcessDefinition(key);
        if (definition != null) {
            processDefinitionId = definition.getId();
        }
        workflowBindingMapper.updateById(new WorkflowBindingDO().setId(binding.getId())
                .setProcessInstanceId(processInstanceId)
                .setProcessDefinitionId(processDefinitionId)
                .setStatus(ClmWorkflowBindingStatusEnum.RUNNING.getStatus()));
        contractMapper.updateById(new ContractDO().setId(contract.getId())
                .setApprovalStatus(ClmApprovalStatusEnum.RUNNING.getStatus())
                .setCurrentBindingId(binding.getId()));
        // 10. 审计
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("bindingId", binding.getId());
        detail.put("processInstanceId", processInstanceId);
        detail.put("documentVersionId", version.getId());
        detail.put("versionNo", version.getVersionNo());
        detail.put("checksum", version.getChecksumSha256());
        clmAuditService.record(ClmAuditAggregateTypeEnum.CONTRACT, contract.getId(), contract.getId(),
                ClmAuditActionEnum.CONTRACT_SUBMIT, detail);
        return binding.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleProcessResult(WorkflowBindingDO binding, Integer status, String reason) {
        TenantUtils.execute(binding.getTenantId(), () -> handleProcessResult0(binding, status, reason));
    }

    private void handleProcessResult0(WorkflowBindingDO binding, Integer status, String reason) {
        ContractDO contract = contractMapper.selectById(binding.getContractId());
        if (contract == null) {
            log.warn("[handleProcessResult][binding({}) 对应合同({}) 不存在]", binding.getId(), binding.getContractId());
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        WorkflowBindingDO bindingUpdate = new WorkflowBindingDO().setId(binding.getId());
        ContractDO contractUpdate = new ContractDO().setId(contract.getId());
        if (BpmProcessInstanceStatusEnum.RUNNING.getStatus().equals(status)) {
            bindingUpdate.setStatus(ClmWorkflowBindingStatusEnum.RUNNING.getStatus());
            contractUpdate.setApprovalStatus(ClmApprovalStatusEnum.RUNNING.getStatus());
        } else if (BpmProcessInstanceStatusEnum.APPROVE.getStatus().equals(status)) {
            bindingUpdate.setStatus(ClmWorkflowBindingStatusEnum.APPROVED.getStatus())
                    .setFinishedTime(now).setResultReason(StrUtil.nullToEmpty(reason));
            contractUpdate.setApprovalStatus(ClmApprovalStatusEnum.APPROVED.getStatus())
                    .setLifecycleStatus(ClmLifecycleStatusEnum.APPROVED.getStatus());
        } else if (BpmProcessInstanceStatusEnum.REJECT.getStatus().equals(status)) {
            bindingUpdate.setStatus(ClmWorkflowBindingStatusEnum.REJECTED.getStatus())
                    .setFinishedTime(now).setResultReason(StrUtil.nullToEmpty(reason));
            contractUpdate.setApprovalStatus(ClmApprovalStatusEnum.REJECTED.getStatus());
        } else if (BpmProcessInstanceStatusEnum.CANCEL.getStatus().equals(status)) {
            bindingUpdate.setStatus(ClmWorkflowBindingStatusEnum.CANCELED.getStatus())
                    .setFinishedTime(now).setResultReason(StrUtil.nullToEmpty(reason));
            contractUpdate.setApprovalStatus(ClmApprovalStatusEnum.CANCELED.getStatus());
        } else {
            log.warn("[handleProcessResult][binding({}) 未知的流程状态({})]", binding.getId(), status);
            return;
        }
        workflowBindingMapper.updateById(bindingUpdate);
        // 仅当该 binding 仍是合同当前绑定时，才回写合同状态
        if (Objects.equals(contract.getCurrentBindingId(), binding.getId())) {
            contractMapper.updateById(contractUpdate);
        }
        // 审计
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("bindingId", binding.getId());
        detail.put("processInstanceId", binding.getProcessInstanceId());
        detail.put("status", status);
        detail.put("reason", reason);
        clmAuditService.record(ClmAuditAggregateTypeEnum.CONTRACT, contract.getId(), contract.getId(),
                ClmAuditActionEnum.APPROVAL_RESULT, detail, null, ACTOR_BPM);
    }

    @Override
    public ContractApprovalPreviewRespVO getApprovalPreview(Long contractId) {
        ContractDO contract = contractService.getRequiredContract(contractId);
        contractAccessService.assertCanView(contract, SecurityFrameworkUtils.getLoginUserId());
        ContractTypeVersionDO typeVersion = contractTypeService.getRequiredContractTypeVersion(contract.getTypeVersionId());
        ContractApprovalPreviewRespVO vo = new ContractApprovalPreviewRespVO();
        vo.setProcessDefinitionKey(typeVersion.getProcessDefinitionKey());
        ProcessDefinition definition = bpmProcessDefinitionService.getActiveProcessDefinition(typeVersion.getProcessDefinitionKey());
        if (definition != null) {
            vo.setProcessDefinitionId(definition.getId());
            vo.setProcessDefinitionName(definition.getName());
        }
        return vo;
    }

    @Override
    public List<WorkflowBindingRespVO> getBindingList(Long contractId) {
        ContractDO contract = contractService.getRequiredContract(contractId);
        contractAccessService.assertCanView(contract, SecurityFrameworkUtils.getLoginUserId());
        List<WorkflowBindingDO> bindings = workflowBindingMapper.selectListByContractId(contractId);
        return buildBindingRespVOList(bindings);
    }

    @Override
    public WorkflowBindingDetailRespVO getBindingDetail(Long id) {
        WorkflowBindingDO binding = getRequiredBinding(id);
        ContractDO contract = contractService.getRequiredContract(binding.getContractId());
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        contractAccessService.assertCanViewBinding(contract, binding.getProcessInstanceId(), userId);
        WorkflowBindingDetailRespVO vo = new WorkflowBindingDetailRespVO();
        vo.setBinding(CollUtil.getFirst(buildBindingRespVOList(Collections.singletonList(binding))));
        ContractRespVO contractVO = contractService.buildContractRespVO(contract, userId);
        vo.setContract(contractVO);
        vo.setFormSnapshot(StrUtil.isBlank(binding.getFormSnapshot()) ? null : JsonUtils.parseMap(binding.getFormSnapshot()));
        DocumentVersionDO version = documentService.getDocumentVersion(binding.getDocumentVersionId());
        vo.setDocumentVersion(documentService.buildDocumentVersionRespVO(version));
        ContractTypeVersionDO typeVersion = contractTypeService.getContractTypeVersion(binding.getContractTypeVersionId());
        if (typeVersion != null) {
            vo.setFormConf(typeVersion.getFormConf());
            vo.setFormFields(typeVersion.getFormFields());
        }
        return vo;
    }

    @Override
    public WorkflowBindingDO getBinding(Long id) {
        return workflowBindingMapper.selectById(id);
    }

    // ========== 私有方法 ==========

    private WorkflowBindingDO getRequiredBinding(Long id) {
        WorkflowBindingDO binding = workflowBindingMapper.selectById(id);
        if (binding == null) {
            throw exception(WORKFLOW_BINDING_NOT_EXISTS);
        }
        return binding;
    }

    private List<WorkflowBindingRespVO> buildBindingRespVOList(List<WorkflowBindingDO> bindings) {
        if (CollUtil.isEmpty(bindings)) {
            return new ArrayList<>();
        }
        Map<Long, DocumentVersionDO> versionMap = new HashMap<>();
        for (Long versionId : convertSet(bindings, WorkflowBindingDO::getDocumentVersionId)) {
            DocumentVersionDO version = documentService.getDocumentVersion(versionId);
            if (version != null) {
                versionMap.put(versionId, version);
            }
        }
        Set<Long> creatorIds = new HashSet<>();
        for (WorkflowBindingDO binding : bindings) {
            if (NumberUtil.isLong(binding.getCreator())) {
                creatorIds.add(Long.parseLong(binding.getCreator()));
            }
        }
        Map<Long, AdminUserRespDTO> userMap = creatorIds.isEmpty() ? Collections.emptyMap()
                : adminUserApi.getUserMap(creatorIds);
        return convertList(bindings, binding -> {
            WorkflowBindingRespVO vo = BeanUtils.toBean(binding, WorkflowBindingRespVO.class);
            DocumentVersionDO version = versionMap.get(binding.getDocumentVersionId());
            vo.setDocumentVersionNo(version == null ? null : version.getVersionNo());
            if (userMap != null && NumberUtil.isLong(binding.getCreator())) {
                AdminUserRespDTO user = userMap.get(Long.parseLong(binding.getCreator()));
                vo.setCreatorName(user == null ? null : user.getNickname());
            }
            return vo;
        });
    }

    private Map<String, Object> buildFormSnapshot(ContractDO contract, ContractTypeDO type,
                                                  List<ContractPartyDO> parties, DocumentVersionDO version,
                                                  String submitRemark) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        // contract
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("id", contract.getId());
        c.put("contractNo", contract.getContractNo());
        c.put("title", contract.getTitle());
        c.put("typeId", contract.getTypeId());
        c.put("typeCode", type == null ? null : type.getCode());
        c.put("typeName", type == null ? null : type.getName());
        c.put("amount", contract.getAmount());
        c.put("currency", contract.getCurrency());
        c.put("signDate", contract.getSignDate() == null ? null : contract.getSignDate().toString());
        c.put("effectiveDate", contract.getEffectiveDate() == null ? null : contract.getEffectiveDate().toString());
        c.put("expiryDate", contract.getExpiryDate() == null ? null : contract.getExpiryDate().toString());
        c.put("ownerUserId", contract.getOwnerUserId());
        AdminUserRespDTO owner = contract.getOwnerUserId() == null ? null : adminUserApi.getUser(contract.getOwnerUserId());
        c.put("ownerUserName", owner == null ? null : owner.getNickname());
        c.put("description", contract.getDescription());
        c.put("customData", contract.getCustomData());
        snapshot.put("contract", c);
        // parties
        snapshot.put("parties", convertList(parties, party -> {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("partyId", party.getPartyId());
            p.put("roleCode", party.getRoleCode());
            Map<String, Object> partySnapshot = StrUtil.isBlank(party.getPartySnapshot()) ? Collections.emptyMap()
                    : JsonUtils.parseMap(party.getPartySnapshot());
            p.put("name", partySnapshot.get("name"));
            p.put("unifiedCreditCode", partySnapshot.get("unifiedCreditCode"));
            p.put("partyType", partySnapshot.get("partyType"));
            return p;
        }));
        // documentVersion
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", version.getId());
        v.put("versionNo", version.getVersionNo());
        v.put("fileName", version.getFileName());
        v.put("fileSize", version.getFileSize());
        v.put("checksumSha256", version.getChecksumSha256());
        snapshot.put("documentVersion", v);
        snapshot.put("submitRemark", submitRemark);
        return snapshot;
    }

}
