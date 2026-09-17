package cn.iocoder.yudao.module.clm.approval;

import cn.iocoder.yudao.module.bpm.service.task.BpmTaskService;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskTransferReqVO;
import cn.iocoder.yudao.module.clm.dal.dataobject.workflow.WorkflowBindingDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.document.DocumentVersionDO;
import cn.iocoder.yudao.module.clm.dal.mysql.contract.ContractMapper;
import cn.iocoder.yudao.module.clm.dal.mysql.workflow.WorkflowBindingMapper;
import cn.iocoder.yudao.module.clm.service.audit.ClmAuditService;
import cn.iocoder.yudao.module.clm.service.workflow.ContractWorkflowService;
import cn.iocoder.yudao.module.clm.revision.ContractRevisionDO;
import cn.iocoder.yudao.module.clm.revision.ContractRevisionService;
import cn.iocoder.yudao.module.clm.revision.ContractRevisionCompareRespVO;
import cn.iocoder.yudao.module.clm.permission.PermissionPolicyMapper;
import cn.iocoder.yudao.module.clm.permission.PermissionPolicyVersionDO;
import cn.iocoder.yudao.module.clm.service.document.DocumentService;
import cn.iocoder.yudao.module.clm.enums.workflow.ClmWorkflowBindingStatusEnum;
import cn.iocoder.yudao.module.clm.integration.IntegrationService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApprovalServiceTest {
    @InjectMocks private ApprovalService service;
    @Mock private BpmTaskService bpmTaskService;
    @Mock private WorkflowBindingMapper workflowBindingMapper;
    @Mock private ApprovalTaskRevisionBindingMapper decisionMapper;
    @Mock private ContractRevisionService revisionService;
    @Mock private ContractWorkflowService contractWorkflowService;
    @Mock private ClmAuditService auditService;
    @Mock private IntegrationService integrationService;
    @Mock private ContractMapper contractMapper;
    @Mock private DocumentService documentService;
    @Mock private PermissionPolicyMapper policyMapper;
    @Mock private ApprovalEditRequestMapper editRequestMapper;
    @Mock private PermissionApi permissionApi;
    @Mock private Task task;

    @Test
    void approve_bindsExactRevisionBeforeDelegatingToBpm() {
        ApprovalDecisionReqVO req = new ApprovalDecisionReqVO();
        req.setTaskId("task-1"); req.setRevisionId(30L); req.setRequestId("request-1"); req.setReason("同意");
        when(bpmTaskService.validateTask(null, "task-1")).thenReturn(task);
        when(task.getProcessInstanceId()).thenReturn("process-1");
        when(workflowBindingMapper.selectByProcessInstanceId("process-1"))
                .thenReturn(new WorkflowBindingDO().setId(10L).setContractId(20L)
                        .setSubmittedRevisionId(30L).setCurrentRevisionId(30L)
                        .setStatus(ClmWorkflowBindingStatusEnum.RUNNING.getStatus()));
        when(revisionService.getRequiredRevision(30L)).thenReturn(new ContractRevisionDO().setId(30L).setContractId(20L));

        service.approve(req);

        InOrder order = inOrder(decisionMapper, workflowBindingMapper, bpmTaskService);
        order.verify(decisionMapper).insert(argThat((ApprovalTaskRevisionBindingDO row) -> Long.valueOf(30L).equals(row.getDecisionRevisionId())));
        order.verify(workflowBindingMapper).updateById(argThat((WorkflowBindingDO row) -> Long.valueOf(30L).equals(row.getCurrentRevisionId())));
        order.verify(bpmTaskService).approveTask(isNull(), any());
    }

    @Test
    void approve_sameRequestIsIdempotent() {
        ApprovalDecisionReqVO req = new ApprovalDecisionReqVO();
        req.setTaskId("task-1"); req.setRevisionId(30L); req.setRequestId("request-1");
        when(decisionMapper.selectByTaskId("task-1")).thenReturn(new ApprovalTaskRevisionBindingDO()
                .setTaskId("task-1").setDecisionRevisionId(30L).setRequestId("request-1").setAction("APPROVE"));
        service.approve(req);
        verifyNoInteractions(bpmTaskService);
        verify(decisionMapper, never()).insert(any(ApprovalTaskRevisionBindingDO.class));
    }

    @Test
    void transfer_onlyDelegatesAfterClmRunningCaseValidation() {
        BpmTaskTransferReqVO req = new BpmTaskTransferReqVO();
        req.setId("task-2"); req.setAssigneeUserId(99L); req.setReason("需要业务负责人判断");
        when(bpmTaskService.validateTask(null, "task-2")).thenReturn(task);
        when(task.getProcessInstanceId()).thenReturn("process-2");
        when(workflowBindingMapper.selectByProcessInstanceId("process-2")).thenReturn(new WorkflowBindingDO()
                .setId(11L).setContractId(21L).setProcessInstanceId("process-2")
                .setStatus(ClmWorkflowBindingStatusEnum.RUNNING.getStatus()));

        service.transfer(req);

        InOrder order = inOrder(bpmTaskService, auditService);
        order.verify(bpmTaskService).validateTask(null, "task-2");
        order.verify(bpmTaskService).transferTask(null, req);
        order.verify(auditService).record(any(), eq(21L), eq(21L), any(), anyMap());
    }

    @Test
    void withdrawCase_delegatesToContractWorkflowFacade() {
        ApprovalCaseWithdrawReqVO req = new ApprovalCaseWithdrawReqVO();
        req.setApprovalCaseId(12L); req.setReason("补充商务条款");

        service.withdrawCase(req);

        verify(contractWorkflowService).withdrawByStarter(12L, "补充商务条款");
    }

    @Test
    void editDocument_createsImmutableRevisionAndKeepsMinorCase() {
        ApprovalDocumentEditReqVO req = new ApprovalDocumentEditReqVO();
        req.setTaskId("task-3"); req.setRequestId("request-doc-1"); req.setContractId(20L);
        req.setBaseRevisionId(30L); req.setChangeReason("修正正文表述");
        WorkflowBindingDO binding = new WorkflowBindingDO().setId(10L).setContractId(20L)
                .setSubmittedRevisionId(30L).setCurrentRevisionId(30L).setProcessInstanceId("process-3")
                .setStatus(ClmWorkflowBindingStatusEnum.RUNNING.getStatus());
        ContractDO before = new ContractDO().setId(20L).setCurrentRevisionId(30L).setCurrentDocumentVersionId(50L);
        ContractDO after = new ContractDO().setId(20L).setCurrentRevisionId(31L).setCurrentDocumentVersionId(51L);
        when(bpmTaskService.validateTask(null, "task-3")).thenReturn(task);
        when(task.getProcessInstanceId()).thenReturn("process-3");
        when(task.getTaskDefinitionKey()).thenReturn("legalReview");
        when(workflowBindingMapper.selectByProcessInstanceId("process-3")).thenReturn(binding);
        when(contractMapper.selectById(20L)).thenReturn(before, after);
        when(policyMapper.selectPublished()).thenReturn(new PermissionPolicyVersionDO().setNodeEditPolicyJson(
                "{\"default\":{\"enabled\":true,\"editableFields\":[\"document\"],\"majorFields\":[]}}"));
        when(documentService.getRequiredDocumentVersion(50L)).thenReturn(new DocumentVersionDO()
                .setId(50L).setDocumentId(5L).setContractId(20L));
        when(revisionService.getRequiredRevision(31L)).thenReturn(new ContractRevisionDO()
                .setId(31L).setContractId(20L).setRevisionNo(2));
        ContractRevisionCompareRespVO difference = new ContractRevisionCompareRespVO();
        difference.setChangedFields(java.util.Collections.emptyList()); difference.setPartiesChanged(false);
        difference.setDocumentChanged(true);
        when(revisionService.compare(30L, 31L)).thenReturn(difference);

        ApprovalEditRespVO response = service.editDocument(req, "修订稿.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", new byte[]{1, 2, 3});

        verify(documentService).createVersionFromBytes(eq(20L), eq(5L), eq(50L), eq("修订稿.docx"),
                anyString(), any(byte[].class), any(), eq("修正正文表述"), isNull(), anyMap(), eq(true));
        verify(workflowBindingMapper).updateById(argThat((WorkflowBindingDO row) ->
                Long.valueOf(31L).equals(row.getCurrentRevisionId())));
        verify(editRequestMapper).insert(argThat((ApprovalEditRequestDO row) -> "MINOR".equals(row.getChangeLevel())
                && Long.valueOf(31L).equals(row.getResultRevisionId())));
        verifyNoInteractions(contractWorkflowService);
        org.junit.jupiter.api.Assertions.assertEquals(31L, response.getRevisionId());
        org.junit.jupiter.api.Assertions.assertFalse(response.getMajorChange());
    }
}
