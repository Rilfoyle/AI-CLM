package cn.iocoder.yudao.module.clm.service.workflow;

import cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService;
import cn.iocoder.yudao.module.clm.access.ContractAccessService;
import cn.iocoder.yudao.module.clm.controller.admin.contract.vo.ContractApprovalPreviewRespVO;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.contracttype.ContractTypeDO;
import cn.iocoder.yudao.module.clm.routing.RoutingRuleVersionDO;
import cn.iocoder.yudao.module.clm.routing.RoutingService;
import cn.iocoder.yudao.module.clm.service.contract.ContractService;
import cn.iocoder.yudao.module.clm.service.contracttype.ContractTypeService;
import org.flowable.engine.repository.ProcessDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContractWorkflowRoutingTest {

    @InjectMocks
    private ContractWorkflowServiceImpl service;
    @Mock
    private ContractService contractService;
    @Mock
    private ContractAccessService contractAccessService;
    @Mock
    private RoutingService routingService;
    @Mock
    private BpmProcessDefinitionService bpmProcessDefinitionService;
    @Mock
    private ContractTypeService contractTypeService;

    @Test
    void approvalPreview_usesResolvedRoute() {
        ContractDO contract = new ContractDO().setId(1L).setTypeVersionId(10L);
        RoutingRuleVersionDO route = new RoutingRuleVersionDO()
                .setId(20L).setProcessDefinitionKey("route-selected-process");
        ProcessDefinition definition = mock(ProcessDefinition.class);
        when(contractService.getRequiredContract(1L)).thenReturn(contract);
        when(routingService.resolveUnique(contract)).thenReturn(route);
        when(bpmProcessDefinitionService.getActiveProcessDefinition("route-selected-process"))
                .thenReturn(definition);
        when(definition.getId()).thenReturn("process-definition-id");
        when(definition.getName()).thenReturn("路由选中的审批流");

        ContractApprovalPreviewRespVO preview = service.getApprovalPreview(1L);

        assertEquals("route-selected-process", preview.getProcessDefinitionKey());
        assertEquals("process-definition-id", preview.getProcessDefinitionId());
        assertEquals("路由选中的审批流", preview.getProcessDefinitionName());
        verify(routingService).resolveUnique(contract);
        verifyNoInteractions(contractTypeService);
    }

    @Test
    void processVariables_includeCustomDataForSimpleConditions() {
        ContractDO contract = baseContract();
        contract.setCustomData(Map.of("procurementCategory", "设备", "budgetCode", "B-2026"));

        Map<String, Object> variables = ContractWorkflowServiceImpl.buildProcessVariables(
                contract, new ContractTypeDO().setCode("PURCHASE"), 2L, "HT-2026-001", 3L);

        assertEquals("设备", variables.get("procurementCategory"));
        assertEquals("B-2026", variables.get("budgetCode"));
    }

    @Test
    void processVariables_reservedFieldsCannotBeOverriddenByCustomData() {
        ContractDO contract = baseContract();
        Map<String, Object> customData = new LinkedHashMap<>();
        customData.put("contractId", 999L);
        customData.put("bindingId", 999L);
        customData.put("amount", 999D);
        customData.put("ownerDeptId", 999L);
        customData.put("contractTypeCode", "CUSTOM");
        customData.put("contractTitle", "伪造标题");
        customData.put("contractNo", "CUSTOM-NO");
        customData.put("revisionId", 999L);
        customData.put("nonReserved", "kept");
        contract.setCustomData(customData);

        Map<String, Object> variables = ContractWorkflowServiceImpl.buildProcessVariables(
                contract, new ContractTypeDO().setCode("PURCHASE"), 2L, "HT-2026-001", 3L);

        assertEquals(1L, variables.get("contractId"));
        assertEquals(2L, variables.get("bindingId"));
        assertEquals(100.5D, variables.get("amount"));
        assertEquals(100L, variables.get("ownerDeptId"));
        assertEquals("PURCHASE", variables.get("contractTypeCode"));
        assertEquals("真实合同", variables.get("contractTitle"));
        assertEquals("HT-2026-001", variables.get("contractNo"));
        assertEquals(3L, variables.get("revisionId"));
        assertEquals("kept", variables.get("nonReserved"));
    }

    private static ContractDO baseContract() {
        return new ContractDO().setId(1L).setTitle("真实合同").setAmount(new BigDecimal("100.50"))
                .setOwnerDeptId(100L);
    }
}
