package cn.iocoder.yudao.module.clm.numbering;

import cn.iocoder.yudao.module.clm.service.contracttype.ContractTypeService;
import cn.iocoder.yudao.module.clm.service.audit.ClmAuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NumberingGovernanceServiceTest {
    @InjectMocks private NumberingGovernanceService service;
    @Mock private NumberingRuleVersionMapper ruleMapper;
    @Mock private ContractTypeService contractTypeService;
    @Mock private ClmAuditService auditService;

    @Test
    void preview_containsControlledPartyShortNameSegment() {
        NumberingRuleSaveReqVO req = validReq();
        req.setOurPartyShortName("TX");

        NumberingPreviewRespVO result = service.preview(req);

        assertEquals("HT-TX-" + LocalDate.now().getYear() + "-0001", result.getSample());
    }

    @Test
    void publish_inactivatesOldRuleForSameScope() {
        NumberingRuleVersionDO draft = new NumberingRuleVersionDO().setId(2L).setRuleCode("PURCHASE")
                .setContractTypeId(10L).setVersionNo(2).setStatus("DRAFT").setPrefix("HT-")
                .setDatePattern("yyyy").setSeparator("-").setSequenceLength(4).setResetPeriod("YEAR")
                .setIncludePartyShortName(true);
        when(ruleMapper.selectByIdForUpdate(2L)).thenReturn(draft);
        when(ruleMapper.selectPublishedByScope(10L)).thenReturn(List.of(
                new NumberingRuleVersionDO().setId(1L).setStatus("PUBLISHED")));

        service.publish(2L);

        verify(ruleMapper).updateById(argThat((NumberingRuleVersionDO row) -> Long.valueOf(1L).equals(row.getId())
                && "INACTIVE".equals(row.getStatus())));
        verify(ruleMapper).updateById(argThat((NumberingRuleVersionDO row) -> Long.valueOf(2L).equals(row.getId())
                && "PUBLISHED".equals(row.getStatus()) && row.getEffectiveTime() != null));
    }

    private NumberingRuleSaveReqVO validReq() {
        NumberingRuleSaveReqVO req = new NumberingRuleSaveReqVO();
        req.setRuleCode("PURCHASE");
        req.setPrefix("HT-");
        req.setDatePattern("yyyy");
        req.setSeparator("-");
        req.setSequenceLength(4);
        req.setResetPeriod("YEAR");
        req.setIncludePartyShortName(true);
        return req;
    }
}
