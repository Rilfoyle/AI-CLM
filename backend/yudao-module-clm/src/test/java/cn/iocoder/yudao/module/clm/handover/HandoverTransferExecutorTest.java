package cn.iocoder.yudao.module.clm.handover;

import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.workflow.WorkflowBindingDO;
import cn.iocoder.yudao.module.clm.dal.mysql.contract.ContractMapper;
import cn.iocoder.yudao.module.clm.dal.mysql.workflow.WorkflowBindingMapper;
import cn.iocoder.yudao.module.clm.enums.audit.ClmAuditActionEnum;
import cn.iocoder.yudao.module.clm.enums.audit.ClmAuditAggregateTypeEnum;
import cn.iocoder.yudao.module.clm.enums.workflow.ClmWorkflowBindingStatusEnum;
import cn.iocoder.yudao.module.clm.service.audit.ClmAuditService;
import cn.iocoder.yudao.module.clm.service.contract.ContractParticipantService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HandoverTransferExecutorTest {

    @Mock private HandoverItemMapper itemMapper;
    @Mock private ContractMapper contractMapper;
    @Mock private ContractParticipantService participantService;
    @Mock private WorkflowBindingMapper workflowBindingMapper;
    @Mock private TaskService taskService;
    @Mock private TaskQuery taskQuery;
    @Mock private Task task;
    @Mock private ClmAuditService auditService;
    @InjectMocks private HandoverTransferExecutor executor;

    @Test
    void transferTask_rejectsTaskThatIsNoLongerActive() {
        HandoverItemDO item = activeTaskItem();
        when(itemMapper.selectByIdForUpdate(1L)).thenReturn(item);
        mockTaskQuery(null);

        HandoverItemDO result = executor.transfer(1L, 9L, 99L, "员工离职");

        assertEquals(HandoverItemDO.STATUS_SKIPPED, result.getStatus());
        assertEquals(7L, result.getOriginalAssignee());
        verify(taskService, never()).setAssignee(anyString(), anyString());
    }

    @Test
    void transferTask_rejectsAssigneeDrift() {
        HandoverItemDO item = activeTaskItem();
        when(itemMapper.selectByIdForUpdate(1L)).thenReturn(item);
        mockTaskQuery(task);
        when(task.getProcessInstanceId()).thenReturn("process-1");
        when(task.getAssignee()).thenReturn("8");
        when(workflowBindingMapper.selectByProcessInstanceId("process-1")).thenReturn(runningBinding());

        HandoverItemDO result = executor.transfer(1L, 9L, 99L, "员工离职");

        assertEquals(HandoverItemDO.STATUS_SKIPPED, result.getStatus());
        assertEquals("审批任务当前处理人已发生变化", result.getResultMessage());
        verify(taskService, never()).setAssignee(anyString(), anyString());
    }

    @Test
    void transferTask_successKeepsOriginalAssigneeAndAuditsAdministrator() {
        HandoverItemDO item = activeTaskItem();
        when(itemMapper.selectByIdForUpdate(1L)).thenReturn(item);
        mockTaskQuery(task);
        when(task.getId()).thenReturn("task-1");
        when(task.getProcessInstanceId()).thenReturn("process-1");
        when(task.getAssignee()).thenReturn("7");
        when(workflowBindingMapper.selectByProcessInstanceId("process-1")).thenReturn(runningBinding());

        HandoverItemDO result = executor.transfer(1L, 9L, 99L, "员工离职");

        assertEquals(HandoverItemDO.STATUS_TRANSFERRED, result.getStatus());
        assertEquals(7L, result.getOriginalAssignee());
        assertEquals(9L, result.getTargetUserId());
        verify(taskService).setAssignee("task-1", "9");
        verify(auditService).record(eq(ClmAuditAggregateTypeEnum.HANDOVER), eq(100L), eq(20L),
                eq(ClmAuditActionEnum.HANDOVER_REASSIGN),
                anyMapWithOriginalAndAdministrator(7L, 99L));
    }

    @Test
    void transferContract_updatesOwnerAndOwnerParticipantAtomically() {
        HandoverItemDO item = new HandoverItemDO().setId(1L).setCaseId(100L)
                .setItemType(HandoverItemDO.TYPE_CONTRACT).setContractId(20L)
                .setOriginalAssignee(7L).setStatus(HandoverItemDO.STATUS_PENDING);
        when(itemMapper.selectByIdForUpdate(1L)).thenReturn(item);
        when(contractMapper.selectByIdForUpdate(20L)).thenReturn(
                new ContractDO().setId(20L).setOwnerUserId(7L));

        HandoverItemDO result = executor.transfer(1L, 9L, 99L, "员工离职");

        assertEquals(HandoverItemDO.STATUS_TRANSFERRED, result.getStatus());
        assertEquals(7L, result.getOriginalAssignee());
        verify(contractMapper).updateById(any(ContractDO.class));
        verify(participantService).changeOwner(20L, 7L, 9L);
    }

    @Test
    void transfer_alreadyTransferredIsIdempotentNoOp() {
        HandoverItemDO item = activeTaskItem().setTargetUserId(9L)
                .setStatus(HandoverItemDO.STATUS_TRANSFERRED);
        when(itemMapper.selectByIdForUpdate(1L)).thenReturn(item);

        HandoverItemDO result = executor.transfer(1L, 9L, 99L, "员工离职");

        assertEquals(HandoverItemDO.STATUS_TRANSFERRED, result.getStatus());
        assertEquals(7L, result.getOriginalAssignee());
        verifyNoInteractions(taskService, contractMapper, participantService, auditService);
    }

    private void mockTaskQuery(Task result) {
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
        when(taskQuery.taskTenantId(anyString())).thenReturn(taskQuery);
        when(taskQuery.active()).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(result);
    }

    private HandoverItemDO activeTaskItem() {
        return new HandoverItemDO().setId(1L).setCaseId(100L)
                .setItemType(HandoverItemDO.TYPE_ACTIVE_TASK).setContractId(20L).setTaskId("task-1")
                .setOriginalAssignee(7L).setStatus(HandoverItemDO.STATUS_PENDING);
    }

    private WorkflowBindingDO runningBinding() {
        return new WorkflowBindingDO().setId(30L).setContractId(20L).setPurpose(WorkflowBindingDO.PURPOSE_APPROVAL)
                .setProcessInstanceId("process-1").setStatus(ClmWorkflowBindingStatusEnum.RUNNING.getStatus());
    }

    private Map<String, Object> anyMapWithOriginalAndAdministrator(Long original, Long administrator) {
        return org.mockito.ArgumentMatchers.argThat((Map<String, Object> detail) ->
                original.equals(detail.get("originalAssignee"))
                && administrator.equals(detail.get("administratorUserId"))
                && HandoverItemDO.STATUS_TRANSFERRED.equals(detail.get("result")));
    }
}
