package cn.iocoder.yudao.module.clm.reconciliation;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import cn.iocoder.yudao.module.clm.dal.dataobject.workflow.WorkflowBindingDO;
import cn.iocoder.yudao.module.clm.dal.mysql.workflow.WorkflowBindingMapper;
import cn.iocoder.yudao.module.clm.governance.GovernanceIssueService;
import cn.iocoder.yudao.module.clm.service.workflow.ContractWorkflowService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceImplTest {

    @InjectMocks private ReconciliationServiceImpl service;
    @Mock private ReconciliationRunMapper runMapper;
    @Mock private WorkflowBindingMapper bindingMapper;
    @Mock private BpmProcessInstanceService processInstanceService;
    @Mock private ContractWorkflowService contractWorkflowService;
    @Mock private GovernanceIssueService governanceIssueService;

    @Test
    void confirm_persistsBusinessEvidenceWithoutTriggeringTechnicalActions() {
        ReconciliationRunDO run = run(1L, List.of(
                Map.of("bindingId", 20L, "contractId", 30L, "result", "ISSUE_OPENED")));
        when(runMapper.selectByIdForUpdate(1L)).thenReturn(run);
        ReconciliationConfirmReqVO reqVO = confirmReq(1L, 20L, "  已核对权威状态  ");

        assertTrue(service.confirm(reqVO, 99L));

        ArgumentCaptor<ReconciliationRunDO> captor = ArgumentCaptor.forClass(ReconciliationRunDO.class);
        verify(runMapper).updateById(captor.capture());
        List<Map> report = JsonUtils.parseArray(captor.getValue().getReportJson(), Map.class);
        assertEquals("CONFIRMED", report.get(0).get("confirmationStatus"));
        assertEquals("99", String.valueOf(report.get(0).get("confirmedBy")));
        assertNotNull(report.get(0).get("confirmedTime"));
        assertEquals("已核对权威状态", report.get(0).get("confirmationOpinion"));
        verify(contractWorkflowService, never()).handleProcessResult(any(), any(), any());
        verify(governanceIssueService, never()).open(any(), any(), any(), any(), any(), any(), any());
        verify(bindingMapper, never()).updateById(any(WorkflowBindingDO.class));
    }

    @Test
    void confirm_isIdempotentAndKeepsOriginalEvidence() {
        ReconciliationRunDO run = run(1L, List.of(Map.of(
                "bindingId", 20L,
                "result", "CHECK_FAILED",
                "confirmationStatus", "CONFIRMED",
                "confirmedBy", 88L,
                "confirmedTime", "2026-08-27T10:00:00",
                "confirmationOpinion", "首次确认")));
        when(runMapper.selectByIdForUpdate(1L)).thenReturn(run);

        assertTrue(service.confirm(confirmReq(1L, 20L, "覆盖意见"), 99L));

        verify(runMapper, never()).updateById(any(ReconciliationRunDO.class));
    }

    @Test
    void confirm_rejectsConsistentRow() {
        when(runMapper.selectByIdForUpdate(1L)).thenReturn(run(1L, List.of(
                Map.of("bindingId", 20L, "result", "CONSISTENT"))));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.confirm(confirmReq(1L, 20L, "无需确认"), 99L));

        assertEquals("只有差异或异常项可以进行业务确认", ex.getMessage());
        verify(runMapper, never()).updateById(any(ReconciliationRunDO.class));
    }

    private ReconciliationRunDO run(Long id, List<Map<String, Object>> report) {
        return new ReconciliationRunDO().setId(id).setReportJson(JsonUtils.toJsonString(report));
    }

    private ReconciliationConfirmReqVO confirmReq(Long runId, Long bindingId, String opinion) {
        ReconciliationConfirmReqVO reqVO = new ReconciliationConfirmReqVO();
        reqVO.setRunId(runId);
        reqVO.setBindingId(bindingId);
        reqVO.setOpinion(opinion);
        return reqVO;
    }
}
