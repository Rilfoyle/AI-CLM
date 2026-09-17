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
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance.BpmProcessInstanceCancelReqVO;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import cn.iocoder.yudao.module.bpm.service.task.BpmTaskService;
import cn.iocoder.yudao.module.clm.access.ContractAccessService;
import cn.iocoder.yudao.module.clm.controller.admin.contract.vo.ContractApprovalPreviewRespVO;
import cn.iocoder.yudao.module.clm.controller.admin.contract.vo.ContractRespVO;
import cn.iocoder.yudao.module.clm.controller.admin.contract.vo.ContractSubmitReqVO;
import cn.iocoder.yudao.module.clm.controller.admin.contract.vo.ContractSubmitRespVO;
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
import cn.iocoder.yudao.module.clm.revision.ContractRevisionDO;
import cn.iocoder.yudao.module.clm.revision.ContractRevisionService;
import cn.iocoder.yudao.module.clm.commitment.CommitmentMapper;
import cn.iocoder.yudao.module.clm.commitment.CommitmentDO;
import cn.iocoder.yudao.module.clm.collaboration.CollaborationService;
import cn.iocoder.yudao.module.clm.numbering.NumberingService;
import cn.iocoder.yudao.module.clm.integration.IntegrationService;
import cn.iocoder.yudao.module.clm.governance.GovernanceIssueService;
import cn.iocoder.yudao.module.clm.routing.RoutingRuleVersionDO;
import cn.iocoder.yudao.module.clm.routing.RoutingService;
import cn.iocoder.yudao.module.clm.template.TemplateVersionDO;
import cn.iocoder.yudao.module.clm.template.TemplateVersionMapper;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.task.api.Task;
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

    private static final Set<String> RESERVED_PROCESS_VARIABLE_NAMES = Set.of(
            "contractId", "bindingId", "amount", "ownerDeptId",
            "contractTypeCode", "contractTitle", "contractNo", "revisionId");

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
    private ContractRevisionService contractRevisionService;
    @Resource
    private CommitmentMapper commitmentMapper;
    @Resource
    private CollaborationService collaborationService;
    @Resource
    private TemplateVersionMapper templateVersionMapper;
    @Resource
    private NumberingService numberingService;
    @Resource
    private RoutingService routingService;
    @Resource
    private IntegrationService integrationService;
    @Resource
    private GovernanceIssueService governanceIssueService;

    @Resource
    private BpmProcessInstanceApi bpmProcessInstanceApi;
    @Resource
    private BpmProcessInstanceService bpmProcessInstanceService;
    @Resource
    private BpmTaskService bpmTaskService;
    @Resource
    private BpmProcessDefinitionService bpmProcessDefinitionService;
    @Resource
    private AdminUserApi adminUserApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContractSubmitRespVO submit(ContractSubmitReqVO reqVO) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        return submitInternal(reqVO, userId, userId, false, null);
    }

    private ContractSubmitRespVO submitInternal(ContractSubmitReqVO reqVO, Long requestActorUserId,
                                                Long processStarterUserId, boolean bypassPrincipal,
                                                Long explicitSupersedesCaseId) {
        // 1. 合同与主体权限
        ContractDO contract = contractMapper.selectByIdForUpdate(reqVO.getId());
        if (contract == null) {
            throw exception(CONTRACT_NOT_EXISTS);
        }
        if (!bypassPrincipal) {
            contractAccessService.assertCanSubmit(contract, requestActorUserId);
        }
        WorkflowBindingDO idempotent = workflowBindingMapper.selectBySubmitRequestId(contract.getId(), reqVO.getSubmitRequestId());
        if (idempotent != null) {
            return buildSubmitResp(contract, idempotent);
        }
        if (!Objects.equals(contract.getCurrentRevisionId(), reqVO.getBaseRevisionId())) {
            throw exception(CONTRACT_REVISION_CONFLICT, contract.getCurrentRevisionId());
        }
        ContractRevisionDO submittedRevision = contractRevisionService.getRequiredRevision(reqVO.getBaseRevisionId());
        if (!Objects.equals(submittedRevision.getContractId(), contract.getId())) {
            throw exception(CONTRACT_REVISION_CONTRACT_MISMATCH);
        }
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
        List<CommitmentDO> commitments = commitmentMapper.selectListByContractId(contract.getId());
        if (!Boolean.TRUE.equals(contract.getNoCommitmentConfirmed()) && commitments.isEmpty()) {
            throw exception(CONTRACT_COMMITMENT_CONFIRM_REQUIRED);
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
        TemplateVersionDO templateVersion = submittedRevision.getTemplateVersionId() == null ? null
                : templateVersionMapper.selectById(submittedRevision.getTemplateVersionId());
        if (templateVersion != null && StrUtil.isBlank(templateVersion.getChecksumSha256())
                && StrUtil.isNotBlank(templateVersion.getFileKey())) {
            // 08 迁移的旧类型范本没有历史 checksum；首次使用时按私有存储内容计算，不放宽正文比对。
            templateVersion.setChecksumSha256(DigestUtil.sha256Hex(documentStorage.load(templateVersion.getFileKey())));
        }
        if (!canDirectSubmitFromTemplate(contract, submittedRevision, version, templateVersion, commitments)
                && !collaborationService.hasValidConclusion(contract.getId(), submittedRevision.getId())) {
            throw exception(CONTRACT_LEGAL_CONCLUSION_REQUIRED);
        }
        if (!BooleanUtil.isTrue(version.getFrozen())) {
            documentService.freezeDocumentVersion(version.getId());
        }
        // 5. 流程定义 KEY
        RoutingRuleVersionDO route = routingService.resolveUnique(contract);
        String key = route.getProcessDefinitionKey();
        String contractNo;
        try {
            contractNo = numberingService.assignIfAbsent(contract.getId());
        } catch (RuntimeException ex) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("contractTypeId", contract.getTypeId());
            detail.put("revisionId", submittedRevision.getId());
            detail.put("errorType", ex.getClass().getSimpleName());
            governanceIssueService.open("NUMBERING_BLOCKED",
                    "numbering:" + contract.getId() + ":" + submittedRevision.getId(),
                    contract.getId(), null, contract.getOwnerUserId(),
                    "合同编号分配失败，请检查我方主体简称和已发布编号规则", detail);
            throw ex;
        }
        // 6. 插入 binding（PREPARING）
        ContractTypeDO type = contractTypeService.getContractType(contract.getTypeId());
        WorkflowBindingDO binding = new WorkflowBindingDO()
                .setContractId(contract.getId())
                .setPurpose(WorkflowBindingDO.PURPOSE_APPROVAL)
                .setProcessDefinitionKey(key)
                .setDocumentVersionId(version.getId())
                .setContractTypeVersionId(typeVersion.getId())
                .setSubmittedRevisionId(submittedRevision.getId())
                .setCurrentRevisionId(submittedRevision.getId())
                .setRouteVersionId(route.getId())
                .setSubmitRequestId(reqVO.getSubmitRequestId())
                .setSupersedesCaseId(explicitSupersedesCaseId != null ? explicitSupersedesCaseId
                        : currentBinding == null ? null : currentBinding.getId())
                .setChecksumSha256(version.getChecksumSha256())
                .setFormSnapshot(JsonUtils.toJsonString(buildFormSnapshot(contract, type, parties, version, reqVO.getRemark())))
                .setStatus(ClmWorkflowBindingStatusEnum.PREPARING.getStatus())
                .setResultReason("");
        binding.setCreator(String.valueOf(processStarterUserId));
        workflowBindingMapper.insert(binding);
        // 7. 流程变量
        Map<String, Object> variables = buildProcessVariables(
                contract, type, binding.getId(), contractNo, submittedRevision.getId());
        // 8. 发起 BPM 流程
        String processInstanceId;
        try {
            processInstanceId = bpmProcessInstanceApi.createProcessInstance(processStarterUserId,
                    new BpmProcessInstanceCreateReqDTO().setProcessDefinitionKey(key)
                            .setVariables(variables).setBusinessKey(String.valueOf(binding.getId()))
                            .setStartUserSelectAssignees(reqVO.getStartUserSelectAssignees()));
        } catch (RuntimeException ex) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("contractTypeId", contract.getTypeId());
            detail.put("revisionId", submittedRevision.getId());
            detail.put("routeVersionId", route.getId());
            detail.put("processDefinitionKey", key);
            detail.put("errorType", ex.getClass().getSimpleName());
            governanceIssueService.open("PROCESS_START_BLOCKED",
                    "process-start:" + contract.getId() + ":" + submittedRevision.getId(),
                    contract.getId(), null, contract.getOwnerUserId(),
                    "审批流程启动失败，请检查审批定义和审批人配置后重试", detail);
            throw ex;
        }
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
                .setCurrentBindingId(binding.getId())
                .setStageCode("APPROVING"));
        enqueueCurrentApprovalTodos(processInstanceId, contract.getId());
        // 10. 审计
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("bindingId", binding.getId());
        detail.put("processInstanceId", processInstanceId);
        detail.put("documentVersionId", version.getId());
        detail.put("versionNo", version.getVersionNo());
        detail.put("checksum", version.getChecksumSha256());
        detail.put("submittedRevisionId", submittedRevision.getId());
        detail.put("routeVersionId", route.getId());
        detail.put("contractNo", contractNo);
        clmAuditService.record(ClmAuditAggregateTypeEnum.CONTRACT, contract.getId(), contract.getId(),
                ClmAuditActionEnum.CONTRACT_SUBMIT, detail);
        contract.setContractNo(contractNo);
        return buildSubmitResp(contract, new WorkflowBindingDO().setId(binding.getId())
                .setProcessInstanceId(processInstanceId).setSubmittedRevisionId(submittedRevision.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContractSubmitRespVO supersedeAndResubmit(WorkflowBindingDO oldBinding, Long revisionId,
                                                     String requestId, String reason) {
        WorkflowBindingDO currentOld = workflowBindingMapper.selectById(oldBinding.getId());
        if (currentOld == null) {
            throw exception(WORKFLOW_BINDING_NOT_EXISTS);
        }
        WorkflowBindingDO idempotent = workflowBindingMapper.selectBySubmitRequestId(
                currentOld.getContractId(), requestId);
        ContractDO contract = contractMapper.selectByIdForUpdate(currentOld.getContractId());
        if (idempotent != null) {
            return buildSubmitResp(contract, idempotent);
        }
        if (!ClmWorkflowBindingStatusEnum.RUNNING.getStatus().equals(currentOld.getStatus())) {
            throw exception(APPROVAL_CASE_NOT_RUNNING);
        }
        ContractRevisionDO revision = contractRevisionService.getRequiredRevision(revisionId);
        if (!Objects.equals(revision.getContractId(), contract.getId())) {
            throw exception(CONTRACT_REVISION_CONTRACT_MISMATCH);
        }
        if (!Objects.equals(contract.getCurrentRevisionId(), revisionId)) {
            throw exception(CONTRACT_REVISION_CONFLICT, contract.getCurrentRevisionId());
        }
        Long actorUserId = SecurityFrameworkUtils.getLoginUserId();
        bpmProcessInstanceService.cancelProcessInstanceByAdmin(actorUserId,
                new BpmProcessInstanceCancelReqVO().setId(currentOld.getProcessInstanceId())
                        .setReason("REVISION_SUPERSEDED: " + StrUtil.blankToDefault(reason, "重大修订重新审批")));
        LocalDateTime now = LocalDateTime.now();
        workflowBindingMapper.updateById(new WorkflowBindingDO().setId(currentOld.getId())
                .setStatus(ClmWorkflowBindingStatusEnum.CANCELED.getStatus())
                .setCancelReason("REVISION_SUPERSEDED")
                .setResultReason(StrUtil.blankToDefault(reason, "重大修订重新审批"))
                .setFinishedTime(now));
        contractMapper.updateById(new ContractDO().setId(contract.getId())
                .setApprovalStatus(ClmApprovalStatusEnum.CANCELED.getStatus())
                .setLifecycleStatus(ClmLifecycleStatusEnum.DRAFT.getStatus())
                .setStageCode("DRAFT"));

        ContractSubmitReqVO restart = new ContractSubmitReqVO();
        restart.setId(contract.getId());
        restart.setBaseRevisionId(revisionId);
        restart.setSubmitRequestId(requestId);
        restart.setRemark("因重大修订完整重走审批：" + StrUtil.blankToDefault(reason, "未填写"));
        ContractSubmitRespVO response = submitInternal(restart, actorUserId, contract.getOwnerUserId(),
                true, currentOld.getId());
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("oldApprovalCaseId", currentOld.getId());
        detail.put("newApprovalCaseId", response.getApprovalCaseId());
        detail.put("revisionId", revisionId);
        detail.put("reason", reason);
        clmAuditService.record(ClmAuditAggregateTypeEnum.CONTRACT, contract.getId(), contract.getId(),
                ClmAuditActionEnum.APPROVAL_SUPERSEDE, detail);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void withdrawByStarter(Long approvalCaseId, String reason) {
        WorkflowBindingDO initial = workflowBindingMapper.selectById(approvalCaseId);
        if (initial == null) {
            throw exception(WORKFLOW_BINDING_NOT_EXISTS);
        }
        ContractDO contract = contractMapper.selectByIdForUpdate(initial.getContractId());
        if (contract == null) {
            throw exception(CONTRACT_NOT_EXISTS);
        }
        WorkflowBindingDO binding = workflowBindingMapper.selectById(approvalCaseId);
        if (binding == null) {
            throw exception(WORKFLOW_BINDING_NOT_EXISTS);
        }
        if (ClmWorkflowBindingStatusEnum.CANCELED.getStatus().equals(binding.getStatus())
                && "STARTER_WITHDRAWN".equals(binding.getCancelReason())) {
            return;
        }
        if (!ClmWorkflowBindingStatusEnum.RUNNING.getStatus().equals(binding.getStatus())) {
            throw exception(APPROVAL_CASE_NOT_RUNNING);
        }
        if (!Objects.equals(contract.getCurrentBindingId(), binding.getId())) {
            throw exception(APPROVAL_CASE_NOT_CURRENT);
        }
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (!NumberUtil.isLong(binding.getCreator())
                || !Objects.equals(Long.valueOf(binding.getCreator()), userId)) {
            throw exception(APPROVAL_STARTER_WITHDRAW_DENIED);
        }
        String finalReason = StrUtil.blankToDefault(reason, "发起人撤回后修订");
        bpmProcessInstanceService.cancelProcessInstanceByStartUser(userId,
                new BpmProcessInstanceCancelReqVO().setId(binding.getProcessInstanceId()).setReason(finalReason));
        LocalDateTime now = LocalDateTime.now();
        workflowBindingMapper.updateById(new WorkflowBindingDO().setId(binding.getId())
                .setStatus(ClmWorkflowBindingStatusEnum.CANCELED.getStatus())
                .setCancelReason("STARTER_WITHDRAWN").setResultReason(finalReason).setFinishedTime(now));
        contractMapper.updateById(new ContractDO().setId(contract.getId())
                .setApprovalStatus(ClmApprovalStatusEnum.CANCELED.getStatus())
                .setLifecycleStatus(ClmLifecycleStatusEnum.DRAFT.getStatus()).setStageCode("DRAFT"));
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("approvalCaseId", binding.getId());
        detail.put("processInstanceId", binding.getProcessInstanceId());
        detail.put("reason", finalReason);
        detail.put("contractNo", contract.getContractNo());
        clmAuditService.record(ClmAuditAggregateTypeEnum.CONTRACT, contract.getId(), contract.getId(),
                ClmAuditActionEnum.APPROVAL_STARTER_WITHDRAW, detail);
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
                    .setApprovedRevisionId(binding.getCurrentRevisionId())
                    .setFinishedTime(now).setResultReason(StrUtil.nullToEmpty(reason));
            contractUpdate.setApprovalStatus(ClmApprovalStatusEnum.APPROVED.getStatus())
                    .setLifecycleStatus(ClmLifecycleStatusEnum.APPROVED.getStatus())
                    .setStageCode("APPROVED");
        } else if (BpmProcessInstanceStatusEnum.REJECT.getStatus().equals(status)) {
            bindingUpdate.setStatus(ClmWorkflowBindingStatusEnum.REJECTED.getStatus())
                    .setFinishedTime(now).setResultReason(StrUtil.nullToEmpty(reason));
            contractUpdate.setApprovalStatus(ClmApprovalStatusEnum.REJECTED.getStatus()).setStageCode("DRAFT");
        } else if (BpmProcessInstanceStatusEnum.CANCEL.getStatus().equals(status)) {
            bindingUpdate.setStatus(ClmWorkflowBindingStatusEnum.CANCELED.getStatus())
                    .setFinishedTime(now).setResultReason(StrUtil.nullToEmpty(reason));
            contractUpdate.setApprovalStatus(ClmApprovalStatusEnum.CANCELED.getStatus()).setStageCode("DRAFT");
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
        if (!BpmProcessInstanceStatusEnum.RUNNING.getStatus().equals(status)) {
            bpmTaskService.getTaskListByProcessInstanceId(binding.getProcessInstanceId(), true)
                    .forEach(task -> integrationService.cancelByTaskId(task.getId(), "合同审批业务单已结束"));
            if (contract.getOwnerUserId() != null) {
                integrationService.enqueue("CLM-RESULT-" + binding.getId() + "-" + status,
                        "RESULT", contract.getOwnerUserId(), contract.getId(), null,
                        "/clm/approval?contractId=" + contract.getId());
            }
        }
    }

    @Override
    public ContractApprovalPreviewRespVO getApprovalPreview(Long contractId) {
        ContractDO contract = contractService.getRequiredContract(contractId);
        contractAccessService.assertCanView(contract, SecurityFrameworkUtils.getLoginUserId());
        RoutingRuleVersionDO route = routingService.resolveUnique(contract);
        String key = route.getProcessDefinitionKey();
        ContractApprovalPreviewRespVO vo = new ContractApprovalPreviewRespVO();
        vo.setProcessDefinitionKey(key);
        ProcessDefinition definition = bpmProcessDefinitionService.getActiveProcessDefinition(key);
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
            p.put("shortName", partySnapshot.get("shortName"));
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

    private ContractSubmitRespVO buildSubmitResp(ContractDO contract, WorkflowBindingDO binding) {
        ContractSubmitRespVO resp = new ContractSubmitRespVO();
        resp.setApprovalCaseId(binding.getId());
        resp.setProcessInstanceId(binding.getProcessInstanceId());
        resp.setContractNo(contract.getContractNo());
        resp.setSubmittedRevisionId(binding.getSubmittedRevisionId());
        return resp;
    }

    static Map<String, Object> buildProcessVariables(ContractDO contract, ContractTypeDO type,
                                                     Long bindingId, String contractNo, Long revisionId) {
        Map<String, Object> variables = new LinkedHashMap<>();
        if (contract.getCustomData() != null) {
            contract.getCustomData().forEach((name, value) -> {
                if (name != null && !RESERVED_PROCESS_VARIABLE_NAMES.contains(name)) {
                    variables.put(name, value);
                }
            });
        }
        variables.put("contractId", contract.getId());
        variables.put("bindingId", bindingId);
        variables.put("amount", contract.getAmount() == null ? 0D : contract.getAmount().doubleValue());
        variables.put("ownerDeptId", contract.getOwnerDeptId());
        variables.put("contractTypeCode", type == null ? null : type.getCode());
        variables.put("contractTitle", contract.getTitle());
        variables.put("contractNo", contractNo);
        variables.put("revisionId", revisionId);
        return variables;
    }

    private void enqueueCurrentApprovalTodos(String processInstanceId, Long contractId) {
        for (Task task : bpmTaskService.getRunningTaskListByProcessInstanceId(processInstanceId, true, null)) {
            if (!NumberUtil.isLong(task.getAssignee())) {
                continue;
            }
            Long assignee = Long.valueOf(task.getAssignee());
            integrationService.enqueue("CLM-TODO-" + task.getId() + "-" + assignee,
                    "TODO", assignee, contractId, task.getId(),
                    "/clm/approval?taskId=" + task.getId());
        }
    }

    /**
     * 只有仍使用已发布标准范本、正文未改且未声明高风险承诺的合同可以跳过法务协同。
     * 其它来源或变更一律从严，要求当前精确修订存在已完成的法务结论。
     */
    static boolean canDirectSubmitFromTemplate(ContractDO contract, ContractRevisionDO revision,
                                               DocumentVersionDO documentVersion,
                                               TemplateVersionDO templateVersion,
                                               List<CommitmentDO> commitments) {
        if (!"TEMPLATE".equals(contract.getSourceMode()) || revision.getTemplateVersionId() == null
                || templateVersion == null || !"PUBLISHED".equals(templateVersion.getStatus())
                || !Objects.equals(revision.getMainDocumentVersionId(), documentVersion.getId())
                || !StrUtil.equalsIgnoreCase(templateVersion.getChecksumSha256(), documentVersion.getChecksumSha256())) {
            return false;
        }
        return commitments == null || commitments.stream()
                .noneMatch(item -> "HIGH".equalsIgnoreCase(StrUtil.nullToEmpty(item.getRiskLevel())));
    }

}
