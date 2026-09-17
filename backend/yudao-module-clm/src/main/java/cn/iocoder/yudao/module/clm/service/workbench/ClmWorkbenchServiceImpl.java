package cn.iocoder.yudao.module.clm.service.workbench;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import cn.iocoder.yudao.module.clm.collaboration.CollaborationCaseDO;
import cn.iocoder.yudao.module.clm.approval.ApprovalTaskRevisionBindingDO;
import cn.iocoder.yudao.module.clm.approval.ApprovalTaskRevisionBindingMapper;
import cn.iocoder.yudao.module.clm.controller.admin.workbench.vo.ClmWorkbenchSummaryRespVO;
import cn.iocoder.yudao.module.clm.collaboration.CollaborationCaseMapper;
import cn.iocoder.yudao.module.clm.collaboration.CollaborationPageReqVO;
import cn.iocoder.yudao.module.clm.controller.admin.contract.vo.ContractPageReqVO;
import cn.iocoder.yudao.module.clm.controller.admin.workbench.vo.ClmWorkbenchItemRespVO;
import cn.iocoder.yudao.module.clm.controller.admin.workbench.vo.ClmWorkbenchItemsReqVO;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.workflow.WorkflowBindingDO;
import cn.iocoder.yudao.module.clm.dal.mysql.contract.ContractMapper;
import cn.iocoder.yudao.module.clm.dal.mysql.workflow.WorkflowBindingMapper;
import cn.iocoder.yudao.module.clm.enums.workflow.ClmWorkflowBindingStatusEnum;
import cn.iocoder.yudao.module.clm.governance.GovernanceIssueMapper;
import cn.iocoder.yudao.module.clm.governance.GovernanceIssueDO;
import cn.iocoder.yudao.module.clm.governance.GovernanceIssuePageReqVO;
import jakarta.annotation.Resource;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertSet;

@Service
@Validated
public class ClmWorkbenchServiceImpl implements ClmWorkbenchService {

    @Resource
    private ContractMapper contractMapper;
    @Resource
    private WorkflowBindingMapper workflowBindingMapper;
    @Resource
    private CollaborationCaseMapper collaborationCaseMapper;
    @Resource
    private GovernanceIssueMapper governanceIssueMapper;
    @Resource
    private TaskService taskService;
    @Resource
    private ApprovalTaskRevisionBindingMapper approvalDecisionMapper;

    @Override
    public ClmWorkbenchSummaryRespVO getSummary(Long userId) {
        ClmWorkbenchSummaryRespVO summary = new ClmWorkbenchSummaryRespVO();
        summary.setDraftCount(countVisibleDraftContracts(userId));
        summary.setStartedRunningCount(workflowBindingMapper.selectCount(
                new LambdaQueryWrapperX<WorkflowBindingDO>()
                        .eq(WorkflowBindingDO::getPurpose, WorkflowBindingDO.PURPOSE_APPROVAL)
                        .eq(WorkflowBindingDO::getStatus, ClmWorkflowBindingStatusEnum.RUNNING.getStatus())
                        .eq(WorkflowBindingDO::getCreator, String.valueOf(userId))));
        summary.setApprovalTodoCount(countApprovalTodo(userId));

        summary.setCollaborationTodoCount(collaborationCaseMapper.countTodo(userId));
        summary.setGovernanceIssueCount(governanceIssueMapper.countOpen(userId));
        return summary;
    }

    @Override
    public PageResult<ClmWorkbenchItemRespVO> getItems(ClmWorkbenchItemsReqVO reqVO, Long userId) {
        if (userId == null) {
            return PageResult.empty();
        }
        return switch (reqVO.getType()) {
            case "DRAFT" -> getDraftItems(reqVO, userId);
            case "COLLABORATION" -> getCollaborationItems(reqVO, userId);
            case "APPROVAL" -> getApprovalItems(reqVO, userId);
            case "APPROVAL_DONE" -> getApprovalDoneItems(reqVO, userId);
            case "STARTED" -> getStartedItems(reqVO, userId);
            case "COPIED" -> getCopiedItems(reqVO, userId);
            case "GOVERNANCE" -> getGovernanceItems(reqVO, userId);
            default -> PageResult.empty(); // 参数校验会在进入 Service 前拒绝该分支
        };
    }

    private PageResult<ClmWorkbenchItemRespVO> getDraftItems(ClmWorkbenchItemsReqVO reqVO, Long userId) {
        ContractPageReqVO query = new ContractPageReqVO();
        query.setPageNo(reqVO.getPageNo());
        query.setPageSize(reqVO.getPageSize());
        query.setStageCode("DRAFT");
        query.setScope("HANDLED");
        PageResult<ContractDO> page = contractMapper.selectPage(query, userId);
        List<ClmWorkbenchItemRespVO> items = new ArrayList<>(page.getList().size());
        for (ContractDO contract : page.getList()) {
            items.add(contractItem("DRAFT", String.valueOf(contract.getId()), contract,
                    contract.getCurrentRevisionId(), null, "DRAFT", contract.getUpdateTime()));
        }
        return new PageResult<>(items, page.getTotal());
    }

    private PageResult<ClmWorkbenchItemRespVO> getCollaborationItems(ClmWorkbenchItemsReqVO reqVO, Long userId) {
        CollaborationPageReqVO query = new CollaborationPageReqVO();
        query.setPageNo(reqVO.getPageNo());
        query.setPageSize(reqVO.getPageSize());
        query.setView(CollaborationPageReqVO.VIEW_TODO);
        PageResult<CollaborationCaseDO> page = collaborationCaseMapper.selectPage(query, userId);
        List<ClmWorkbenchItemRespVO> items = new ArrayList<>(page.getList().size());
        for (CollaborationCaseDO collaboration : page.getList()) {
            ContractDO contract = contractMapper.selectById(collaboration.getContractId());
            items.add(contractItem("COLLABORATION", String.valueOf(collaboration.getId()), contract,
                    collaboration.getRequestedRevisionId(), null, collaboration.getStatus(),
                    collaboration.getUpdateTime()));
        }
        return new PageResult<>(items, page.getTotal());
    }

    private PageResult<ClmWorkbenchItemRespVO> getApprovalItems(ClmWorkbenchItemsReqVO reqVO, Long userId) {
        List<WorkflowBindingDO> bindings = workflowBindingMapper.selectList(
                new LambdaQueryWrapperX<WorkflowBindingDO>()
                        .eq(WorkflowBindingDO::getPurpose, WorkflowBindingDO.PURPOSE_APPROVAL)
                        .eq(WorkflowBindingDO::getStatus, ClmWorkflowBindingStatusEnum.RUNNING.getStatus())
                        .isNotNull(WorkflowBindingDO::getProcessInstanceId));
        Map<String, WorkflowBindingDO> bindingByProcess = new HashMap<>();
        for (WorkflowBindingDO binding : bindings) {
            bindingByProcess.put(binding.getProcessInstanceId(), binding);
        }
        if (bindingByProcess.isEmpty()) {
            return PageResult.empty();
        }
        List<Task> tasks = taskService.createTaskQuery()
                .processInstanceIdIn(bindingByProcess.keySet())
                .taskAssignee(String.valueOf(userId))
                .taskTenantId(FlowableUtils.getTenantId())
                .active().orderByTaskCreateTime().desc().list();
        List<ClmWorkbenchItemRespVO> items = new ArrayList<>(tasks.size());
        for (Task task : tasks) {
            WorkflowBindingDO binding = bindingByProcess.get(task.getProcessInstanceId());
            if (binding == null) {
                continue;
            }
            ContractDO contract = contractMapper.selectById(binding.getContractId());
            LocalDateTime updatedTime = toLocalDateTime(task.getCreateTime());
            items.add(contractItem("APPROVAL", task.getId(), contract,
                    binding.getCurrentRevisionId() == null ? binding.getSubmittedRevisionId()
                            : binding.getCurrentRevisionId(),
                    task.getId(), "TODO", updatedTime));
        }
        return paginate(items, reqVO);
    }

    private PageResult<ClmWorkbenchItemRespVO> getApprovalDoneItems(ClmWorkbenchItemsReqVO reqVO, Long userId) {
        List<ApprovalTaskRevisionBindingDO> decisions = approvalDecisionMapper.selectListByActor(userId);
        List<ClmWorkbenchItemRespVO> items = new ArrayList<>(decisions.size());
        for (ApprovalTaskRevisionBindingDO decision : decisions) {
            ContractDO contract = contractMapper.selectById(decision.getContractId());
            items.add(contractItem("APPROVAL_DONE", String.valueOf(decision.getId()), contract,
                    decision.getDecisionRevisionId(), null, decision.getAction(), decision.getCreateTime()));
        }
        return paginate(items, reqVO);
    }

    private PageResult<ClmWorkbenchItemRespVO> getStartedItems(ClmWorkbenchItemsReqVO reqVO, Long userId) {
        List<WorkflowBindingDO> bindings = workflowBindingMapper.selectList(
                new LambdaQueryWrapperX<WorkflowBindingDO>()
                        .eq(WorkflowBindingDO::getPurpose, WorkflowBindingDO.PURPOSE_APPROVAL)
                        .eq(WorkflowBindingDO::getCreator, String.valueOf(userId))
                        .orderByDesc(WorkflowBindingDO::getId));
        List<ClmWorkbenchItemRespVO> items = new ArrayList<>(bindings.size());
        for (WorkflowBindingDO binding : bindings) {
            ContractDO contract = contractMapper.selectById(binding.getContractId());
            items.add(contractItem("STARTED", String.valueOf(binding.getId()), contract,
                    binding.getCurrentRevisionId() == null ? binding.getSubmittedRevisionId()
                            : binding.getCurrentRevisionId(),
                    null, statusName(binding.getStatus()), binding.getUpdateTime()));
        }
        return paginate(items, reqVO);
    }

    private PageResult<ClmWorkbenchItemRespVO> getCopiedItems(ClmWorkbenchItemsReqVO reqVO, Long userId) {
        List<WorkflowBindingDO> bindings = workflowBindingMapper.selectListCopiedByUser(userId);
        List<ClmWorkbenchItemRespVO> items = new ArrayList<>(bindings.size());
        for (WorkflowBindingDO binding : bindings) {
            ContractDO contract = contractMapper.selectById(binding.getContractId());
            items.add(contractItem("COPIED", String.valueOf(binding.getId()), contract,
                    binding.getCurrentRevisionId() == null ? binding.getSubmittedRevisionId()
                            : binding.getCurrentRevisionId(),
                    null, statusName(binding.getStatus()), binding.getUpdateTime()));
        }
        return paginate(items, reqVO);
    }

    private PageResult<ClmWorkbenchItemRespVO> getGovernanceItems(ClmWorkbenchItemsReqVO reqVO, Long userId) {
        GovernanceIssuePageReqVO query = new GovernanceIssuePageReqVO();
        query.setPageNo(reqVO.getPageNo());
        query.setPageSize(reqVO.getPageSize());
        query.setStatus(GovernanceIssueDO.STATUS_OPEN);
        query.setOwnerUserId(userId);
        PageResult<GovernanceIssueDO> page = governanceIssueMapper.selectPage(query);
        List<ClmWorkbenchItemRespVO> items = new ArrayList<>(page.getList().size());
        for (GovernanceIssueDO issue : page.getList()) {
            ContractDO contract = issue.getContractId() == null ? null : contractMapper.selectById(issue.getContractId());
            ClmWorkbenchItemRespVO item = contractItem("GOVERNANCE", String.valueOf(issue.getId()), contract,
                    contract == null ? null : contract.getCurrentRevisionId(), null,
                    issue.getStatus(), issue.getUpdateTime());
            if (contract == null) {
                item.setContractName(issue.getSummary());
            }
            items.add(item);
        }
        return new PageResult<>(items, page.getTotal());
    }

    private ClmWorkbenchItemRespVO contractItem(String type, String id, ContractDO contract,
                                                Long revisionId, String taskId, String status,
                                                LocalDateTime updatedTime) {
        ClmWorkbenchItemRespVO item = new ClmWorkbenchItemRespVO();
        item.setId(id);
        item.setType(type);
        if (contract != null) {
            item.setContractId(contract.getId());
            item.setContractName(contract.getTitle());
            item.setContractNo(contract.getContractNo());
        }
        item.setRevisionId(revisionId);
        item.setTaskId(taskId);
        item.setStatus(status);
        item.setUpdatedTime(updatedTime);
        if (updatedTime != null) {
            item.setWaitingMinutes(Math.max(0L, Duration.between(updatedTime, LocalDateTime.now()).toMinutes()));
        }
        return item;
    }

    private PageResult<ClmWorkbenchItemRespVO> paginate(List<ClmWorkbenchItemRespVO> items,
                                                        ClmWorkbenchItemsReqVO reqVO) {
        long total = items.size();
        int from = Math.min((reqVO.getPageNo() - 1) * reqVO.getPageSize(), items.size());
        int to = Math.min(from + reqVO.getPageSize(), items.size());
        List<ClmWorkbenchItemRespVO> page = from == to
                ? Collections.emptyList() : new ArrayList<>(items.subList(from, to));
        return new PageResult<>(page, total);
    }

    private LocalDateTime toLocalDateTime(Date date) {
        return date == null ? null : LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    private long countVisibleDraftContracts(Long userId) {
        ContractPageReqVO query = new ContractPageReqVO();
        query.setPageNo(1);
        query.setPageSize(1);
        query.setStageCode("DRAFT");
        query.setScope("HANDLED");
        return contractMapper.selectPage(query, userId).getTotal();
    }

    private long countApprovalTodo(Long userId) {
        List<WorkflowBindingDO> runningBindings = workflowBindingMapper.selectList(
                new LambdaQueryWrapperX<WorkflowBindingDO>()
                        .eq(WorkflowBindingDO::getPurpose, WorkflowBindingDO.PURPOSE_APPROVAL)
                        .eq(WorkflowBindingDO::getStatus, ClmWorkflowBindingStatusEnum.RUNNING.getStatus())
                        .isNotNull(WorkflowBindingDO::getProcessInstanceId));
        Set<String> processInstanceIds = convertSet(runningBindings, WorkflowBindingDO::getProcessInstanceId);
        if (processInstanceIds.isEmpty()) {
            return 0L;
        }
        return taskService.createTaskQuery()
                .processInstanceIdIn(processInstanceIds)
                .taskAssignee(String.valueOf(userId))
                .taskTenantId(FlowableUtils.getTenantId())
                .active()
                .count();
    }

    private String statusName(Integer status) {
        ClmWorkflowBindingStatusEnum value = ClmWorkflowBindingStatusEnum.valueOf(status);
        return value == null ? "UNKNOWN" : value.name();
    }

}
