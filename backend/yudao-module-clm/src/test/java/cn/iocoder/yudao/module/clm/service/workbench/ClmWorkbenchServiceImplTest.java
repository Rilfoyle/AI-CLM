package cn.iocoder.yudao.module.clm.service.workbench;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.clm.dal.mysql.contract.ContractMapper;
import cn.iocoder.yudao.module.clm.approval.ApprovalTaskRevisionBindingDO;
import cn.iocoder.yudao.module.clm.approval.ApprovalTaskRevisionBindingMapper;
import cn.iocoder.yudao.module.clm.collaboration.CollaborationCaseMapper;
import cn.iocoder.yudao.module.clm.governance.GovernanceIssueMapper;
import cn.iocoder.yudao.module.clm.dal.mysql.workflow.WorkflowBindingMapper;
import cn.iocoder.yudao.module.clm.controller.admin.contract.vo.ContractPageReqVO;
import cn.iocoder.yudao.module.clm.controller.admin.workbench.vo.ClmWorkbenchItemsReqVO;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.workflow.WorkflowBindingDO;
import cn.iocoder.yudao.module.clm.enums.workflow.ClmWorkflowBindingStatusEnum;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClmWorkbenchServiceImplTest {

    @Mock
    private ContractMapper contractMapper;
    @Mock
    private WorkflowBindingMapper workflowBindingMapper;
    @Mock
    private CollaborationCaseMapper collaborationCaseMapper;
    @Mock
    private GovernanceIssueMapper governanceIssueMapper;
    @Mock
    private TaskService taskService;
    @Mock
    private TaskQuery taskQuery;
    @Mock
    private Task task;
    @Mock
    private ApprovalTaskRevisionBindingMapper approvalDecisionMapper;

    @InjectMocks
    private ClmWorkbenchServiceImpl workbenchService;

    @Test
    void getSummary_shouldUseAuthoritativeCountsAndZeroUnimplementedSources() {
        when(contractMapper.selectPage(argThat((ContractPageReqVO query) ->
                        "DRAFT".equals(query.getStageCode()) && "HANDLED".equals(query.getScope())), eq(42L)))
                .thenReturn(new PageResult<>(Collections.emptyList(), 3L));
        when(workflowBindingMapper.selectCount(any())).thenReturn(2L);
        when(workflowBindingMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(collaborationCaseMapper.countTodo(42L)).thenReturn(4L);
        when(governanceIssueMapper.countOpen(42L)).thenReturn(1L);

        var summary = workbenchService.getSummary(42L);

        assertEquals(3L, summary.getDraftCount());
        assertEquals(2L, summary.getStartedRunningCount());
        assertEquals(0L, summary.getApprovalTodoCount());
        assertEquals(4L, summary.getCollaborationTodoCount());
        assertEquals(1L, summary.getGovernanceIssueCount());
    }

    @Test
    void getItems_approvalShouldExposeFlowableTaskAndBoundRevision() {
        WorkflowBindingDO binding = new WorkflowBindingDO().setId(8L).setContractId(9L)
                .setProcessInstanceId("process-1").setSubmittedRevisionId(10L).setCurrentRevisionId(11L)
                .setStatus(ClmWorkflowBindingStatusEnum.RUNNING.getStatus());
        when(workflowBindingMapper.selectList(any())).thenReturn(List.of(binding));
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.processInstanceIdIn(any())).thenReturn(taskQuery);
        when(taskQuery.taskAssignee("42")).thenReturn(taskQuery);
        when(taskQuery.taskTenantId(anyString())).thenReturn(taskQuery);
        when(taskQuery.active()).thenReturn(taskQuery);
        when(taskQuery.orderByTaskCreateTime()).thenReturn(taskQuery);
        when(taskQuery.desc()).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of(task));
        when(task.getId()).thenReturn("task-7");
        when(task.getProcessInstanceId()).thenReturn("process-1");
        when(task.getCreateTime()).thenReturn(new Date());
        when(contractMapper.selectById(9L)).thenReturn(new ContractDO().setId(9L)
                .setTitle("采购合同").setContractNo("HT-2026-0001"));
        ClmWorkbenchItemsReqVO reqVO = new ClmWorkbenchItemsReqVO();
        reqVO.setType("APPROVAL");

        var page = workbenchService.getItems(reqVO, 42L);

        assertEquals(1L, page.getTotal());
        assertEquals("task-7", page.getList().get(0).getTaskId());
        assertEquals(11L, page.getList().get(0).getRevisionId());
        assertEquals(9L, page.getList().get(0).getContractId());
    }

    @Test
    void getItems_approvalDoneShouldOpenExactDecisionRevisionAsHistory() {
        ApprovalTaskRevisionBindingDO decision = new ApprovalTaskRevisionBindingDO().setId(12L).setContractId(9L)
                .setDecisionRevisionId(11L).setAction("APPROVE");
        decision.setCreateTime(LocalDateTime.now());
        when(approvalDecisionMapper.selectListByActor(42L)).thenReturn(List.of(decision));
        when(contractMapper.selectById(9L)).thenReturn(new ContractDO().setId(9L)
                .setTitle("采购合同").setContractNo("HT-2026-0001"));
        ClmWorkbenchItemsReqVO reqVO = new ClmWorkbenchItemsReqVO();
        reqVO.setType("APPROVAL_DONE");

        var page = workbenchService.getItems(reqVO, 42L);

        assertEquals(1L, page.getTotal());
        assertEquals("APPROVE", page.getList().get(0).getStatus());
        assertEquals(11L, page.getList().get(0).getRevisionId());
        assertEquals(null, page.getList().get(0).getTaskId());
    }

    @Test
    void getItems_copiedShouldExposeReadOnlyContractHistory() {
        when(workflowBindingMapper.selectListCopiedByUser(42L)).thenReturn(List.of(
                new WorkflowBindingDO().setId(8L).setContractId(9L)
                        .setSubmittedRevisionId(10L).setCurrentRevisionId(11L)
                        .setStatus(ClmWorkflowBindingStatusEnum.APPROVED.getStatus())));
        when(contractMapper.selectById(9L)).thenReturn(new ContractDO().setId(9L)
                .setTitle("采购合同").setContractNo("HT-2026-0001"));
        ClmWorkbenchItemsReqVO reqVO = new ClmWorkbenchItemsReqVO();
        reqVO.setType("COPIED");

        var page = workbenchService.getItems(reqVO, 42L);

        assertEquals(1L, page.getTotal());
        assertEquals("APPROVED", page.getList().get(0).getStatus());
        assertEquals(9L, page.getList().get(0).getContractId());
        assertEquals(null, page.getList().get(0).getTaskId());
    }

}
