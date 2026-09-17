package cn.iocoder.yudao.module.clm.routing;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.clm.governance.GovernanceIssueService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static cn.iocoder.yudao.module.clm.enums.ErrorCodeConstants.ROUTING_RULE_AMBIGUOUS;
import static cn.iocoder.yudao.module.clm.enums.ErrorCodeConstants.ROUTING_RULE_NO_MATCH;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoutingServiceTest {
    @InjectMocks private RoutingService service;
    @Mock private RoutingRuleVersionMapper ruleMapper;
    @Mock private GovernanceIssueService governanceIssueService;

    @Test
    void resolveUnique_returnsOnlyMatchingRule() {
        ContractDO contract = new ContractDO().setTypeId(1L).setAmount(new BigDecimal("100"));
        RoutingRuleVersionDO low = rule(1L, "{\"maxAmount\":200}");
        RoutingRuleVersionDO high = rule(2L, "{\"minAmount\":201}");
        when(ruleMapper.selectPublishedCandidates(1L)).thenReturn(List.of(low, high));
        assertSame(low, service.resolveUnique(contract));
    }

    @Test
    void resolveUnique_rejectsAmbiguousRules() {
        ContractDO contract = new ContractDO().setTypeId(1L).setAmount(BigDecimal.TEN);
        when(ruleMapper.selectPublishedCandidates(1L)).thenReturn(List.of(rule(1L, "{}"), rule(2L, "{}")));
        ServiceException ex = assertThrows(ServiceException.class, () -> service.resolveUnique(contract));
        assertEquals(ROUTING_RULE_AMBIGUOUS.getCode(), ex.getCode());
    }

    @Test
    void resolveUnique_opensGovernanceIssueInsteadOfInventingFallbackRule() {
        ContractDO contract = new ContractDO().setId(9L).setTypeId(1L).setCurrentRevisionId(5L);
        when(ruleMapper.selectPublishedCandidates(1L)).thenReturn(List.of());

        ServiceException ex = assertThrows(ServiceException.class, () -> service.resolveUnique(contract));

        assertEquals(ROUTING_RULE_NO_MATCH.getCode(), ex.getCode());
        org.mockito.Mockito.verify(governanceIssueService).open(
                org.mockito.ArgumentMatchers.eq("ROUTE_ZERO"),
                org.mockito.ArgumentMatchers.eq("routing:9:5"),
                org.mockito.ArgumentMatchers.eq(9L), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyMap());
        org.mockito.Mockito.verify(ruleMapper, org.mockito.Mockito.never()).insert(
                org.mockito.ArgumentMatchers.any(RoutingRuleVersionDO.class));
    }

    private RoutingRuleVersionDO rule(Long id, String condition) {
        return new RoutingRuleVersionDO().setId(id).setConditionJson(condition).setProcessDefinitionKey("process");
    }
}
