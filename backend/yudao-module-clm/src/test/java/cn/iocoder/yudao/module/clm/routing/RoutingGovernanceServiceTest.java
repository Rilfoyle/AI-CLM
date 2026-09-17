package cn.iocoder.yudao.module.clm.routing;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils;
import cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService;
import cn.iocoder.yudao.module.clm.service.contracttype.ContractTypeService;
import cn.iocoder.yudao.module.clm.service.audit.ClmAuditService;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.repository.ProcessDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.module.clm.routing.RoutingGovernanceErrors.CONDITION_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoutingGovernanceServiceTest {
    @InjectMocks private RoutingGovernanceService service;
    @Mock private RoutingRuleVersionMapper ruleMapper;
    @Mock private ContractTypeService contractTypeService;
    @Mock private BpmProcessDefinitionService processDefinitionService;
    @Mock private ProcessDefinition processDefinition;
    @Mock private ClmAuditService auditService;

    @Test
    void precheck_reportsMultipleMatchesWithoutMutatingRules() {
        when(ruleMapper.selectPublishedCandidates(10L)).thenReturn(List.of(
                rule(1L, "LOW", "{\"maxAmount\":100}"),
                rule(2L, "HIGH", "{\"minAmount\":50}")));
        RoutingPrecheckReqVO req = new RoutingPrecheckReqVO();
        req.setContractTypeId(10L); req.setAmount(new BigDecimal("75")); req.setCurrency("CNY");

        RoutingPrecheckRespVO result = service.precheck(req);

        assertEquals("MULTIPLE", result.getResult());
        assertEquals(2, result.getMatchedCount());
        assertEquals(List.of(1L, 2L), result.getMatchedRuleVersionIds());
    }

    @Test
    void saveDraft_rejectsUnknownConditionKey() {
        RoutingRuleSaveReqVO req = validReq();
        req.setCondition(new LinkedHashMap<>(Map.of("script", "anything")));

        ServiceException ex = assertThrows(ServiceException.class, () -> service.saveDraft(req));

        assertEquals(CONDITION_INVALID.getCode(), ex.getCode());
    }

    @Test
    void publish_validatesBpmAssigneeConfigAndInactivatesOldVersion() {
        RoutingRuleVersionDO draft = rule(2L, "LEGAL", "{}").setVersionNo(2).setStatus("DRAFT");
        RoutingRuleVersionDO old = rule(1L, "LEGAL", "{}").setVersionNo(1).setStatus("PUBLISHED");
        when(ruleMapper.selectByIdForUpdate(2L)).thenReturn(draft);
        when(ruleMapper.selectListByRuleCode("LEGAL")).thenReturn(List.of(draft, old));
        when(processDefinitionService.getActiveProcessDefinition("process")).thenReturn(processDefinition);
        when(processDefinition.getId()).thenReturn("definition-1");
        when(processDefinitionService.getProcessDefinitionBpmnModel("definition-1")).thenReturn(modelWithAssignee());

        service.publish(2L);

        verify(ruleMapper).updateById(argThat((RoutingRuleVersionDO row) -> Long.valueOf(1L).equals(row.getId())
                && "INACTIVE".equals(row.getStatus())));
        verify(ruleMapper).updateById(argThat((RoutingRuleVersionDO row) -> Long.valueOf(2L).equals(row.getId())
                && "PUBLISHED".equals(row.getStatus()) && row.getEffectiveTime() != null));
    }

    private RoutingRuleVersionDO rule(Long id, String code, String condition) {
        return new RoutingRuleVersionDO().setId(id).setRuleCode(code).setName(code)
                .setContractTypeId(10L).setPriority(100).setConditionJson(condition)
                .setProcessDefinitionKey("process").setStatus("PUBLISHED");
    }

    private RoutingRuleSaveReqVO validReq() {
        RoutingRuleSaveReqVO req = new RoutingRuleSaveReqVO();
        req.setRuleCode("LEGAL"); req.setName("法务路由"); req.setContractTypeId(10L); req.setPriority(100);
        req.setCondition(Map.of()); req.setProcessDefinitionKey("process");
        return req;
    }

    private BpmnModel modelWithAssignee() {
        BpmnModel model = new BpmnModel();
        Process process = new Process();
        process.setId("process");
        UserTask task = new UserTask();
        task.setId("legal-review"); task.setName("法务审批");
        BpmnModelUtils.addCandidateElements(30, "1", task);
        process.addFlowElement(task);
        model.addProcess(process);
        return model;
    }
}
