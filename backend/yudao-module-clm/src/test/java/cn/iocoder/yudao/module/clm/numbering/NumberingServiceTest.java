package cn.iocoder.yudao.module.clm.numbering;

import com.baomidou.mybatisplus.annotation.TableField;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractPartyDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.party.PartyDO;
import cn.iocoder.yudao.module.clm.dal.mysql.contract.ContractMapper;
import cn.iocoder.yudao.module.clm.dal.mysql.contract.ContractPartyMapper;
import cn.iocoder.yudao.module.clm.dal.mysql.party.PartyMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NumberingServiceTest {
    @InjectMocks private NumberingService service;
    @Mock private ContractMapper contractMapper;
    @Mock private NumberingRuleVersionMapper ruleMapper;
    @Mock private NumberSequenceMapper sequenceMapper;
    @Mock private ContractPartyMapper contractPartyMapper;

    @Test
    void separatorColumn_isQuotedForMySql8() throws NoSuchFieldException {
        TableField mapping = NumberingRuleVersionDO.class.getDeclaredField("separator").getAnnotation(TableField.class);
        assertNotNull(mapping);
        assertEquals("`separator`", mapping.value());
    }
    @Mock private PartyMapper partyMapper;

    @Test
    void assignIfAbsent_reusesPermanentNumber() {
        when(contractMapper.selectByIdForUpdate(1L)).thenReturn(new ContractDO().setId(1L).setContractNo("HT-2026-0001"));
        assertEquals("HT-2026-0001", service.assignIfAbsent(1L));
        verifyNoInteractions(ruleMapper, sequenceMapper);
        verify(contractMapper, never()).updateById(any(ContractDO.class));
    }

    @Test
    void assignIfAbsent_advancesVersionedSequenceOnce() {
        when(contractMapper.selectByIdForUpdate(1L)).thenReturn(new ContractDO().setId(1L).setTypeId(10L));
        NumberingRuleVersionDO rule = new NumberingRuleVersionDO().setId(20L).setPrefix("HT-")
                .setDatePattern("yyyy").setSeparator("-").setSequenceLength(4).setResetPeriod("YEAR")
                .setIncludePartyShortName(true);
        when(ruleMapper.selectPublished(10L)).thenReturn(rule);
        when(sequenceMapper.selectForUpdate(20L, String.valueOf(LocalDate.now().getYear())))
                .thenReturn(new NumberSequenceDO().setId(30L).setCurrentValue(7L));
        when(contractPartyMapper.selectListByContractId(1L)).thenReturn(java.util.List.of(
                new ContractPartyDO().setPartyId(40L).setRoleCode("OUR_SIDE")));
        when(partyMapper.selectById(40L)).thenReturn(new PartyDO().setId(40L).setShortName("TX"));

        String number = service.assignIfAbsent(1L);

        assertEquals("HT-TX-" + LocalDate.now().getYear() + "-0008", number);
        verify(sequenceMapper).updateById(argThat((NumberSequenceDO row) -> row.getCurrentValue() == 8L));
        verify(contractMapper).updateById(argThat((ContractDO row) -> number.equals(row.getContractNo())
                && Long.valueOf(20L).equals(row.getNumberingRuleVersionId())));
    }
}
