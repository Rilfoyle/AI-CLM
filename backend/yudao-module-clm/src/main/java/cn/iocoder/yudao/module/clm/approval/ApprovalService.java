package cn.iocoder.yudao.module.clm.approval;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskApproveReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskCopyReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskRejectReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskReturnReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskSignCreateReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskTransferReqVO;
import cn.iocoder.yudao.module.bpm.service.task.BpmTaskService;
import cn.iocoder.yudao.module.clm.access.ContractAccessService;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.document.DocumentVersionDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.workflow.WorkflowBindingDO;
import cn.iocoder.yudao.module.clm.dal.mysql.contract.ContractMapper;
import cn.iocoder.yudao.module.clm.dal.mysql.workflow.WorkflowBindingMapper;
import cn.iocoder.yudao.module.clm.revision.ContractRevisionDO;
import cn.iocoder.yudao.module.clm.revision.ContractRevisionMapper;
import cn.iocoder.yudao.module.clm.revision.ContractRevisionService;
import cn.iocoder.yudao.module.clm.revision.ContractRevisionCompareRespVO;
import cn.iocoder.yudao.module.clm.revision.ContractRevisionSaveRespVO;
import cn.iocoder.yudao.module.clm.permission.PermissionPolicyMapper;
import cn.iocoder.yudao.module.clm.permission.PermissionPolicyVersionDO;
import cn.iocoder.yudao.module.clm.service.audit.ClmAuditService;
import cn.iocoder.yudao.module.clm.service.workflow.ContractWorkflowService;
import cn.iocoder.yudao.module.clm.controller.admin.contract.vo.ContractSubmitRespVO;
import cn.iocoder.yudao.module.clm.enums.audit.ClmAuditActionEnum;
import cn.iocoder.yudao.module.clm.enums.audit.ClmAuditAggregateTypeEnum;
import cn.iocoder.yudao.module.clm.service.contract.ContractService;
import cn.iocoder.yudao.module.clm.service.document.DocumentService;
import cn.iocoder.yudao.module.clm.enums.workflow.ClmWorkflowBindingStatusEnum;
import cn.iocoder.yudao.module.clm.integration.IntegrationService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import jakarta.annotation.Resource;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.bpmn.model.UserTask;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.clm.enums.ErrorCodeConstants.*;

@Service
public class ApprovalService {
    private static final Set<String> APPROVAL_DOCUMENT_EXTENSIONS = Set.of("doc", "docx", "pdf");
    private static final int MAX_APPROVAL_DOCUMENT_SIZE_MB = 50;
    private static final long MAX_APPROVAL_DOCUMENT_SIZE = MAX_APPROVAL_DOCUMENT_SIZE_MB * 1024L * 1024L;

    @Resource private BpmTaskService bpmTaskService;
    @Resource private WorkflowBindingMapper workflowBindingMapper;
    @Resource private ApprovalTaskRevisionBindingMapper decisionMapper;
    @Resource private ContractRevisionService revisionService;
    @Resource private ContractRevisionMapper revisionMapper;
    @Resource private ContractMapper contractMapper;
    @Resource private ContractService contractService;
    @Resource private ContractAccessService contractAccessService;
    @Resource private DocumentService documentService;
    @Resource private PermissionPolicyMapper policyMapper;
    @Resource private ApprovalEditRequestMapper editRequestMapper;
    @Resource private ContractWorkflowService contractWorkflowService;
    @Resource private ClmAuditService auditService;
    @Resource private IntegrationService integrationService;
    @Resource private PermissionApi permissionApi;

    public ApprovalTaskDetailRespVO getTask(String taskId) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        Task task = bpmTaskService.validateTask(userId, taskId);
        WorkflowBindingDO binding = requiredBinding(task.getProcessInstanceId());
        ContractDO contract = requiredContract(binding.getContractId());
        ApprovalTaskDetailRespVO resp = new ApprovalTaskDetailRespVO();
        resp.setTask(bpmTaskService.getTodoTask(userId, taskId, task.getProcessInstanceId()));
        resp.setTaskId(taskId); resp.setTaskName(resp.getTask() == null ? task.getName() : resp.getTask().getName());
        resp.setTaskStatus(resp.getTask() == null ? "RUNNING" : String.valueOf(resp.getTask().getStatus()));
        resp.setContract(contractService.buildContractRespVO(contract, userId));
        resp.setContractId(contract.getId());
        resp.setApprovalCaseId(binding.getId()); resp.setSubmittedRevisionId(binding.getSubmittedRevisionId());
        resp.setCurrentRevisionId(binding.getCurrentRevisionId() == null ? binding.getSubmittedRevisionId() : binding.getCurrentRevisionId());
        DocumentVersionDO document = documentService.getDocumentVersion(binding.getDocumentVersionId());
        resp.setMainDocument(documentService.buildDocumentVersionRespVO(document));
        List<Map<String, Object>> revisions = new ArrayList<>();
        for (ContractRevisionDO revision : revisionMapper.selectListByContractId(contract.getId())) {
            Map<String, Object> row = new LinkedHashMap<>(); row.put("id", revision.getId());
            row.put("revisionNo", revision.getRevisionNo()); row.put("changeSource", revision.getChangeSource());
            row.put("sourceType", revision.getChangeSource()); row.put("documentVersionId", revision.getMainDocumentVersionId());
            row.put("changeReason", revision.getChangeReason()); row.put("createTime", revision.getCreateTime()); revisions.add(row);
        }
        resp.setRevisions(revisions);
        ContractRevisionDO submitted = revisionService.getRequiredRevision(binding.getSubmittedRevisionId());
        List<Map<String, Object>> parties = flattenParties(parseListMap(submitted.getPartySnapshotJson()));
        List<Map<String, Object>> commitments = parseListMap(submitted.getCommitmentSnapshotJson());
        resp.setPartySnapshot(parties); resp.setParties(parties);
        resp.setCommitmentSnapshot(commitments); resp.setCommitments(commitments);
        List<ApprovalTaskRevisionBindingDO> decisions = decisionMapper.selectListByContractId(contract.getId());
        resp.setHistoryOpinions(decisions); resp.setOpinions(toOpinions(decisions));
        ApprovalNodeEditPolicy nodePolicy = resolveEditPolicy(task);
        Map<String, Object> editPolicy = new LinkedHashMap<>(); editPolicy.put("canEdit", nodePolicy.enabled());
        editPolicy.put("editableFields", nodePolicy.editableFields()); editPolicy.put("reason", nodePolicy.reason());
        resp.setEditPolicy(editPolicy); resp.setNodeEditPolicy(editPolicy);
        List<ApprovalReturnTargetRespVO> targets = new ArrayList<>();
        for (UserTask target : bpmTaskService.getUserTaskListByReturn(taskId)) {
            targets.add(new ApprovalReturnTargetRespVO(target.getId(), target.getName()));
        }
        resp.setReturnTargets(targets);
        List<String> actions = new ArrayList<>();
        if (hasPermission(userId, "clm:approval:approve")) actions.add("APPROVE");
        if (hasPermission(userId, "clm:approval:reject")) actions.add("REJECT");
        if (!targets.isEmpty() && hasPermission(userId, "clm:approval:return")) actions.add("RETURN");
        if (nodePolicy.enabled() && hasPermission(userId, "clm:approval:edit")) actions.add("EDIT");
        if (hasPermission(userId, "clm:approval:collaborate")) {
            actions.add("TRANSFER");
            actions.add("COPY");
            actions.add("ADD_SIGN");
        }
        resp.setAvailableActions(actions);
        return resp;
    }

    private boolean hasPermission(Long userId, String permission) {
        return userId != null && permissionApi.hasAnyPermissions(userId, permission);
    }

    @Transactional(rollbackFor = Exception.class)
    public ApprovalEditRespVO edit(ApprovalEditReqVO reqVO) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        ApprovalEditRespVO idempotent = resolveExistingEdit(reqVO.getRequestId(), reqVO.getTaskId(),
                reqVO.getBaseRevisionId(), userId);
        if (idempotent != null) return idempotent;
        Task task = bpmTaskService.validateTask(userId, reqVO.getTaskId());
        ApprovalEditContext context = validateEditContext(task, reqVO.getContractId(), reqVO.getBaseRevisionId());
        ApprovalNodeEditPolicy policy = resolveEditPolicy(task);
        if (!policy.enabled()) throw exception(APPROVAL_NODE_EDIT_DENIED);

        ContractRevisionSaveRespVO revision = revisionService.saveForApproval(reqVO);
        ContractRevisionCompareRespVO difference = revisionService.compare(reqVO.getBaseRevisionId(), revision.getRevisionId());
        boolean major = validateAndClassify(difference, policy);
        return completeEdit(context.binding(), reqVO.getTaskId(), reqVO.getRequestId(),
                reqVO.getBaseRevisionId(), revision.getRevisionId(), revision.getRevisionNo(),
                reqVO.getChangeReason(), major, "FIELDS");
    }

    @Transactional(rollbackFor = Exception.class)
    public ApprovalEditRespVO editDocument(ApprovalDocumentEditReqVO reqVO, String fileName,
                                           String mimeType, byte[] content) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        ApprovalEditRespVO idempotent = resolveExistingEdit(reqVO.getRequestId(), reqVO.getTaskId(),
                reqVO.getBaseRevisionId(), userId);
        if (idempotent != null) return idempotent;
        validateApprovalDocument(fileName, content);

        Task task = bpmTaskService.validateTask(userId, reqVO.getTaskId());
        ApprovalEditContext context = validateEditContext(task, reqVO.getContractId(), reqVO.getBaseRevisionId());
        ApprovalNodeEditPolicy policy = resolveEditPolicy(task);
        if (!policy.enabled()) throw exception(APPROVAL_NODE_EDIT_DENIED);
        if (!policy.allows("document")) throw exception(APPROVAL_EDIT_FIELD_DENIED, "document");

        Long currentDocumentVersionId = context.contract().getCurrentDocumentVersionId();
        if (currentDocumentVersionId == null) throw exception(DOCUMENT_VERSION_NOT_EXISTS);
        DocumentVersionDO parent = documentService.getRequiredDocumentVersion(currentDocumentVersionId);
        if (!Objects.equals(parent.getContractId(), context.contract().getId())) {
            throw exception(DOCUMENT_VERSION_NOT_EXISTS);
        }
        Map<String, Object> uploadDetail = new LinkedHashMap<>();
        uploadDetail.put("approvalTaskId", reqVO.getTaskId());
        uploadDetail.put("approvalCaseId", context.binding().getId());
        uploadDetail.put("baseRevisionId", reqVO.getBaseRevisionId());
        documentService.createVersionFromBytes(context.contract().getId(), parent.getDocumentId(), parent.getId(),
                fileName, mimeType, content, cn.iocoder.yudao.module.clm.enums.document.ClmDocumentSourceTypeEnum.UPLOAD,
                reqVO.getChangeReason(), userId, uploadDetail, true);

        ContractDO updatedContract = requiredContract(context.contract().getId());
        Long resultRevisionId = updatedContract.getCurrentRevisionId();
        if (resultRevisionId == null || Objects.equals(resultRevisionId, reqVO.getBaseRevisionId())) {
            throw exception(CONTRACT_REVISION_CONFLICT, resultRevisionId);
        }
        ContractRevisionDO resultRevision = revisionService.getRequiredRevision(resultRevisionId);
        ContractRevisionCompareRespVO difference = revisionService.compare(reqVO.getBaseRevisionId(), resultRevisionId);
        boolean major = validateAndClassify(difference, policy);
        return completeEdit(context.binding(), reqVO.getTaskId(), reqVO.getRequestId(),
                reqVO.getBaseRevisionId(), resultRevisionId, resultRevision.getRevisionNo(),
                reqVO.getChangeReason(), major, "DOCUMENT");
    }

    @Transactional(rollbackFor = Exception.class)
    public void approve(ApprovalDecisionReqVO reqVO) {
        DecisionContext ctx = prepareDecision(reqVO, "APPROVE");
        if (ctx.idempotent) return;
        bpmTaskService.approveTask(ctx.userId, new BpmTaskApproveReqVO().setId(reqVO.getTaskId()).setReason(reqVO.getReason()));
        refreshTaskDeliveries(ctx.binding, reqVO.getTaskId(), "审批任务已处理");
    }

    @Transactional(rollbackFor = Exception.class)
    public void reject(ApprovalDecisionReqVO reqVO) {
        DecisionContext ctx = prepareDecision(reqVO, "REJECT");
        if (ctx.idempotent) return;
        bpmTaskService.rejectTask(ctx.userId, new BpmTaskRejectReqVO().setId(reqVO.getTaskId()).setReason(reqVO.getReason()));
        refreshTaskDeliveries(ctx.binding, reqVO.getTaskId(), "审批任务已拒绝");
    }

    @Transactional(rollbackFor = Exception.class)
    public void returnTask(ApprovalDecisionReqVO reqVO) {
        if (StrUtil.isBlank(reqVO.getTargetActivityId())) throw exception(APPROVAL_RETURN_TARGET_REQUIRED);
        DecisionContext ctx = prepareDecision(reqVO, "RETURN");
        if (ctx.idempotent) return;
        bpmTaskService.returnTask(ctx.userId, new BpmTaskReturnReqVO().setId(reqVO.getTaskId())
                .setTargetTaskDefinitionKey(reqVO.getTargetActivityId()).setReason(reqVO.getReason()));
        refreshTaskDeliveries(ctx.binding, reqVO.getTaskId(), "审批任务已退回");
    }

    @Transactional(rollbackFor = Exception.class)
    public void transfer(BpmTaskTransferReqVO reqVO) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        WorkflowBindingDO binding = validateRunningClmTask(userId, reqVO.getId());
        bpmTaskService.transferTask(userId, reqVO);
        integrationService.cancelByTaskId(reqVO.getId(), "审批任务已转交");
        integrationService.enqueue("CLM-TODO-" + reqVO.getId() + "-" + reqVO.getAssigneeUserId(),
                "TODO", reqVO.getAssigneeUserId(), binding.getContractId(), reqVO.getId(),
                "/clm/approval?taskId=" + reqVO.getId());
        auditTaskCollaboration(binding, reqVO.getId(), ClmAuditActionEnum.APPROVAL_TRANSFER,
                Map.of("assigneeUserId", reqVO.getAssigneeUserId(), "reason", reqVO.getReason()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void copy(BpmTaskCopyReqVO reqVO) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        WorkflowBindingDO binding = validateRunningClmTask(userId, reqVO.getId());
        bpmTaskService.copyTask(userId, reqVO);
        for (Long copyUserId : reqVO.getCopyUserIds()) {
            integrationService.enqueue("CLM-COPY-" + reqVO.getId() + "-" + copyUserId,
                    "TODO", copyUserId, binding.getContractId(), reqVO.getId(),
                    "/clm/approval?contractId=" + binding.getContractId());
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("copyUserIds", reqVO.getCopyUserIds());
        detail.put("reason", reqVO.getReason());
        auditTaskCollaboration(binding, reqVO.getId(), ClmAuditActionEnum.APPROVAL_COPY, detail);
    }

    @Transactional(rollbackFor = Exception.class)
    public void addSign(BpmTaskSignCreateReqVO reqVO) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        WorkflowBindingDO binding = validateRunningClmTask(userId, reqVO.getId());
        bpmTaskService.createSignTask(userId, reqVO);
        enqueueRunningTodos(binding);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("userIds", reqVO.getUserIds());
        detail.put("signType", reqVO.getType());
        detail.put("reason", reqVO.getReason());
        auditTaskCollaboration(binding, reqVO.getId(), ClmAuditActionEnum.APPROVAL_ADD_SIGN, detail);
    }

    @Transactional(rollbackFor = Exception.class)
    public void withdrawTask(String taskId) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        HistoricTaskInstance task = bpmTaskService.getHistoricTask(taskId);
        if (task == null) throw exception(APPROVAL_TASK_NOT_CLM);
        WorkflowBindingDO binding = requiredBinding(task.getProcessInstanceId());
        if (!ClmWorkflowBindingStatusEnum.RUNNING.getStatus().equals(binding.getStatus())) {
            throw exception(APPROVAL_CASE_NOT_RUNNING);
        }
        List<Task> invalidatedTasks = bpmTaskService.getRunningTaskListByProcessInstanceId(
                binding.getProcessInstanceId(), true, null);
        bpmTaskService.withdrawTask(userId, taskId);
        invalidatedTasks.forEach(item -> integrationService.cancelByTaskId(item.getId(), "前一审批人已撤回已办"));
        enqueueRunningTodos(binding);
        auditTaskCollaboration(binding, taskId, ClmAuditActionEnum.APPROVAL_WITHDRAW_TASK,
                Collections.emptyMap());
    }

    @Transactional(rollbackFor = Exception.class)
    public void withdrawCase(ApprovalCaseWithdrawReqVO reqVO) {
        contractWorkflowService.withdrawByStarter(reqVO.getApprovalCaseId(), reqVO.getReason());
    }

    public ApprovalHistoryRespVO getHistory(Long contractId) {
        ContractDO contract = requiredContract(contractId);
        contractAccessService.assertCanView(contract, SecurityFrameworkUtils.getLoginUserId());
        List<ApprovalTaskRevisionBindingDO> decisions = decisionMapper.selectListByContractId(contractId);
        Map<Long, List<ApprovalTaskRevisionBindingDO>> byCase = new HashMap<>();
        for (ApprovalTaskRevisionBindingDO item : decisions) byCase.computeIfAbsent(item.getApprovalCaseId(), k -> new ArrayList<>()).add(item);
        List<ApprovalHistoryCaseRespVO> result = new ArrayList<>();
        List<WorkflowBindingDO> bindings = workflowBindingMapper.selectListByContractId(contractId);
        Set<Long> supersededCaseIds = new HashSet<>();
        for (WorkflowBindingDO binding : bindings) if (binding.getSupersedesCaseId() != null) supersededCaseIds.add(binding.getSupersedesCaseId());
        for (WorkflowBindingDO binding : bindings) {
            ApprovalHistoryCaseRespVO row = new ApprovalHistoryCaseRespVO(); row.setApprovalCase(binding);
            List<ApprovalTaskRevisionBindingDO> caseDecisions = byCase.getOrDefault(binding.getId(), Collections.emptyList());
            row.setDecisions(caseDecisions); row.setOpinions(toOpinions(caseDecisions));
            row.setId(binding.getId()); row.setStatus(statusCode(binding.getStatus()));
            row.setCancelReason(supersededCaseIds.contains(binding.getId()) ? "REVISION_SUPERSEDED" : binding.getCancelReason());
            row.setSubmittedRevisionId(binding.getSubmittedRevisionId()); row.setCurrentRevisionId(binding.getCurrentRevisionId());
            row.setApprovedRevisionId(binding.getApprovedRevisionId()); row.setSupersedesCaseId(binding.getSupersedesCaseId());
            row.setProcessInstanceId(binding.getProcessInstanceId()); row.setStartTime(binding.getCreateTime()); row.setEndTime(binding.getFinishedTime());
            row.setDisplayStatus(supersededCaseIds.contains(binding.getId()) ? "REVISION_SUPERSEDED" : row.getStatus());
            result.add(row);
        }
        ApprovalHistoryRespVO resp = new ApprovalHistoryRespVO(); resp.setContractId(contractId); resp.setCases(result); return resp;
    }

    private DecisionContext prepareDecision(ApprovalDecisionReqVO reqVO, String action) {
        ApprovalTaskRevisionBindingDO existing = decisionMapper.selectByTaskId(reqVO.getTaskId());
        if (existing != null) {
            boolean same = Objects.equals(existing.getRequestId(), reqVO.getRequestId())
                    && Objects.equals(existing.getAction(), action)
                    && Objects.equals(existing.getDecisionRevisionId(), reqVO.getRevisionId());
            if (same) return new DecisionContext(SecurityFrameworkUtils.getLoginUserId(), true, null);
            throw exception(APPROVAL_TASK_ALREADY_DECIDED);
        }
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        Task task = bpmTaskService.validateTask(userId, reqVO.getTaskId());
        WorkflowBindingDO binding = requiredBinding(task.getProcessInstanceId());
        if (!ClmWorkflowBindingStatusEnum.RUNNING.getStatus().equals(binding.getStatus())) {
            throw exception(APPROVAL_CASE_NOT_RUNNING);
        }
        ContractRevisionDO revision = revisionService.getRequiredRevision(reqVO.getRevisionId());
        if (!Objects.equals(revision.getContractId(), binding.getContractId())) throw exception(CONTRACT_REVISION_CONTRACT_MISMATCH);
        Long currentRevisionId = binding.getCurrentRevisionId() == null
                ? binding.getSubmittedRevisionId() : binding.getCurrentRevisionId();
        if (!Objects.equals(reqVO.getRevisionId(), currentRevisionId)) {
            throw exception(CONTRACT_REVISION_CONFLICT, currentRevisionId);
        }
        decisionMapper.insert(new ApprovalTaskRevisionBindingDO().setTaskId(reqVO.getTaskId())
                .setApprovalCaseId(binding.getId()).setContractId(binding.getContractId())
                .setDecisionRevisionId(reqVO.getRevisionId()).setAction(action)
                .setReason(StrUtil.nullToEmpty(reqVO.getReason())).setRequestId(reqVO.getRequestId()));
        workflowBindingMapper.updateById(new WorkflowBindingDO().setId(binding.getId())
                .setCurrentRevisionId(reqVO.getRevisionId()));
        return new DecisionContext(userId, false, binding);
    }

    private ApprovalNodeEditPolicy resolveEditPolicy(Task task) {
        PermissionPolicyVersionDO policy = policyMapper.selectPublished();
        return policy == null ? ApprovalNodeEditPolicy.denied("未发布数据权限规则")
                : ApprovalNodeEditPolicy.parse(policy.getNodeEditPolicyJson(), task.getTaskDefinitionKey());
    }

    private WorkflowBindingDO validateRunningClmTask(Long userId, String taskId) {
        Task task = bpmTaskService.validateTask(userId, taskId);
        WorkflowBindingDO binding = requiredBinding(task.getProcessInstanceId());
        if (!ClmWorkflowBindingStatusEnum.RUNNING.getStatus().equals(binding.getStatus())) {
            throw exception(APPROVAL_CASE_NOT_RUNNING);
        }
        return binding;
    }

    private void auditTaskCollaboration(WorkflowBindingDO binding, String taskId,
                                        ClmAuditActionEnum action, Map<String, Object> actionDetail) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("approvalCaseId", binding.getId());
        detail.put("processInstanceId", binding.getProcessInstanceId());
        detail.put("taskId", taskId);
        detail.putAll(actionDetail);
        auditService.record(ClmAuditAggregateTypeEnum.CONTRACT, binding.getContractId(), binding.getContractId(),
                action, detail);
    }

    private void refreshTaskDeliveries(WorkflowBindingDO binding, String handledTaskId, String reason) {
        integrationService.cancelByTaskId(handledTaskId, reason);
        enqueueRunningTodos(binding);
    }

    private void enqueueRunningTodos(WorkflowBindingDO binding) {
        if (binding == null || binding.getProcessInstanceId() == null) return;
        for (Task task : bpmTaskService.getRunningTaskListByProcessInstanceId(
                binding.getProcessInstanceId(), true, null)) {
            if (task.getAssignee() == null) continue;
            Long assignee;
            try {
                assignee = Long.valueOf(task.getAssignee());
            } catch (NumberFormatException ignored) {
                continue;
            }
            integrationService.enqueue("CLM-TODO-" + task.getId() + "-" + assignee,
                    "TODO", assignee, binding.getContractId(), task.getId(),
                    "/clm/approval?taskId=" + task.getId());
        }
    }

    private boolean validateAndClassify(ContractRevisionCompareRespVO difference,
                                        ApprovalNodeEditPolicy policy) {
        boolean major = false;
        for (String field : difference.getChangedFields()) {
            if (!policy.allows(field)) throw exception(APPROVAL_EDIT_FIELD_DENIED, field);
            major |= policy.isMajor(field);
        }
        if (Boolean.TRUE.equals(difference.getPartiesChanged())) {
            if (!policy.allows("parties")) throw exception(APPROVAL_EDIT_FIELD_DENIED, "parties");
            major |= policy.isMajor("parties");
        }
        if (Boolean.TRUE.equals(difference.getDocumentChanged())) {
            if (!policy.allows("document")) throw exception(APPROVAL_EDIT_FIELD_DENIED, "document");
            major |= policy.isMajor("document");
        }
        return major;
    }

    private ApprovalEditContext validateEditContext(Task task, Long contractId, Long baseRevisionId) {
        WorkflowBindingDO binding = requiredBinding(task.getProcessInstanceId());
        if (!ClmWorkflowBindingStatusEnum.RUNNING.getStatus().equals(binding.getStatus())) {
            throw exception(APPROVAL_CASE_NOT_RUNNING);
        }
        if (!Objects.equals(binding.getContractId(), contractId)) {
            throw exception(CONTRACT_REVISION_CONTRACT_MISMATCH);
        }
        ContractDO contract = requiredContract(binding.getContractId());
        Long currentRevisionId = binding.getCurrentRevisionId() == null
                ? binding.getSubmittedRevisionId() : binding.getCurrentRevisionId();
        if (!Objects.equals(currentRevisionId, baseRevisionId)
                || !Objects.equals(contract.getCurrentRevisionId(), baseRevisionId)) {
            throw exception(CONTRACT_REVISION_CONFLICT, contract.getCurrentRevisionId());
        }
        return new ApprovalEditContext(binding, contract);
    }

    private ApprovalEditRespVO resolveExistingEdit(String requestId, String taskId, Long baseRevisionId, Long userId) {
        ApprovalEditRequestDO existing = editRequestMapper.selectByRequestId(requestId);
        if (existing == null) return null;
        boolean same = Objects.equals(existing.getTaskId(), taskId)
                && Objects.equals(existing.getBaseRevisionId(), baseRevisionId)
                && Objects.equals(existing.getCreator(), String.valueOf(userId));
        if (!same) throw exception(APPROVAL_EDIT_REQUEST_CONFLICT);
        return buildEditResponse(existing);
    }

    private ApprovalEditRespVO completeEdit(WorkflowBindingDO binding, String taskId, String requestId,
                                            Long baseRevisionId, Long resultRevisionId, Integer resultRevisionNo,
                                            String reason, boolean major, String editType) {
        Long resultCaseId = binding.getId();
        String processInstanceId = binding.getProcessInstanceId();
        if (major) {
            ContractSubmitRespVO restart = contractWorkflowService.supersedeAndResubmit(binding,
                    resultRevisionId, requestId + ":restart", reason);
            resultCaseId = restart.getApprovalCaseId();
            processInstanceId = restart.getProcessInstanceId();
        } else {
            workflowBindingMapper.updateById(new WorkflowBindingDO().setId(binding.getId())
                    .setCurrentRevisionId(resultRevisionId));
        }
        ApprovalEditRequestDO record = new ApprovalEditRequestDO().setTaskId(taskId)
                .setApprovalCaseId(binding.getId()).setContractId(binding.getContractId())
                .setBaseRevisionId(baseRevisionId).setResultRevisionId(resultRevisionId)
                .setResultApprovalCaseId(resultCaseId).setRequestId(requestId)
                .setChangeLevel(major ? "MAJOR" : "MINOR").setReason(StrUtil.nullToEmpty(reason));
        editRequestMapper.insert(record);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("taskId", taskId); detail.put("baseRevisionId", baseRevisionId);
        detail.put("resultRevisionId", resultRevisionId); detail.put("changeLevel", record.getChangeLevel());
        detail.put("resultApprovalCaseId", resultCaseId); detail.put("editType", editType);
        auditService.record(ClmAuditAggregateTypeEnum.CONTRACT, binding.getContractId(), binding.getContractId(),
                ClmAuditActionEnum.APPROVAL_EDIT, detail);
        ApprovalEditRespVO response = new ApprovalEditRespVO();
        response.setRevisionId(resultRevisionId); response.setRevisionNo(resultRevisionNo);
        response.setMajorChange(major); response.setApprovalCaseId(resultCaseId);
        response.setProcessInstanceId(processInstanceId); return response;
    }

    private void validateApprovalDocument(String fileName, byte[] content) {
        if (ArrayUtil.isEmpty(content)) throw exception(DOCUMENT_FILE_EMPTY);
        if (content.length > MAX_APPROVAL_DOCUMENT_SIZE) {
            throw exception(DOCUMENT_FILE_TOO_LARGE, MAX_APPROVAL_DOCUMENT_SIZE_MB);
        }
        String extension = StrUtil.emptyToDefault(FileUtil.extName(StrUtil.nullToEmpty(fileName)), "")
                .toLowerCase(Locale.ROOT);
        if (!APPROVAL_DOCUMENT_EXTENSIONS.contains(extension)) {
            throw exception(DOCUMENT_FILE_TYPE_NOT_ALLOWED, extension);
        }
    }

    private ApprovalEditRespVO buildEditResponse(ApprovalEditRequestDO record) {
        ContractRevisionDO revision = revisionService.getRequiredRevision(record.getResultRevisionId());
        WorkflowBindingDO binding = workflowBindingMapper.selectById(record.getResultApprovalCaseId());
        ApprovalEditRespVO response = new ApprovalEditRespVO();
        response.setRevisionId(revision.getId()); response.setRevisionNo(revision.getRevisionNo());
        response.setMajorChange("MAJOR".equals(record.getChangeLevel()));
        response.setApprovalCaseId(record.getResultApprovalCaseId());
        response.setProcessInstanceId(binding == null ? null : binding.getProcessInstanceId());
        return response;
    }

    private WorkflowBindingDO requiredBinding(String processInstanceId) {
        WorkflowBindingDO binding = workflowBindingMapper.selectByProcessInstanceId(processInstanceId);
        if (binding == null) throw exception(WORKFLOW_BINDING_NOT_EXISTS);
        return binding;
    }
    private ContractDO requiredContract(Long id) {
        ContractDO contract = contractMapper.selectById(id); if (contract == null) throw exception(CONTRACT_NOT_EXISTS); return contract;
    }
    private List<Map<String, Object>> parseListMap(String json) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (StrUtil.isBlank(json)) return result;
        for (Map row : JsonUtils.parseArray(json, Map.class)) result.add(new LinkedHashMap<>(row));
        return result;
    }
    private List<Map<String, Object>> flattenParties(List<Map<String, Object>> parties) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : parties) {
            Map<String, Object> flattened = new LinkedHashMap<>(row);
            if (row.get("party") instanceof Map<?, ?> party) {
                for (Map.Entry<?, ?> entry : party.entrySet()) flattened.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            result.add(flattened);
        }
        return result;
    }
    private List<ApprovalOpinionRespVO> toOpinions(List<ApprovalTaskRevisionBindingDO> decisions) {
        List<ApprovalOpinionRespVO> result = new ArrayList<>();
        for (ApprovalTaskRevisionBindingDO decision : decisions) {
            ApprovalOpinionRespVO vo = new ApprovalOpinionRespVO(); vo.setTaskId(decision.getTaskId());
            vo.setUserName(decision.getCreator()); vo.setAction(decision.getAction()); vo.setReason(decision.getReason());
            vo.setRevisionId(decision.getDecisionRevisionId()); vo.setCreateTime(decision.getCreateTime()); result.add(vo);
        }
        return result;
    }
    private String statusCode(Integer status) {
        ClmWorkflowBindingStatusEnum value = ClmWorkflowBindingStatusEnum.valueOf(status);
        return value == null ? "UNKNOWN" : value.name();
    }
    private record ApprovalEditContext(WorkflowBindingDO binding, ContractDO contract) {}
    private record DecisionContext(Long userId, boolean idempotent, WorkflowBindingDO binding) {}
}
