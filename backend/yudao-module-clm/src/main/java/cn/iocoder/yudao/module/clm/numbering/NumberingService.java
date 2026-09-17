package cn.iocoder.yudao.module.clm.numbering;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractPartyDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.party.PartyDO;
import cn.iocoder.yudao.module.clm.dal.mysql.contract.ContractMapper;
import cn.iocoder.yudao.module.clm.dal.mysql.contract.ContractPartyMapper;
import cn.iocoder.yudao.module.clm.dal.mysql.party.PartyMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.clm.numbering.NumberingGovernanceErrors.OUR_PARTY_SHORT_NAME_REQUIRED;
import static cn.iocoder.yudao.module.clm.numbering.NumberingGovernanceErrors.PARAM_INVALID;

@Service
public class NumberingService {
    @Resource private ContractMapper contractMapper;
    @Resource private NumberingRuleVersionMapper ruleMapper;
    @Resource private NumberSequenceMapper sequenceMapper;
    @Resource private ContractPartyMapper contractPartyMapper;
    @Resource private PartyMapper partyMapper;

    /** 首次提交才分配；合同锁 + 唯一索引保证同一请求重试仍返回同一编号。 */
    @Transactional(rollbackFor = Exception.class)
    public String assignIfAbsent(Long contractId) {
        ContractDO contract = contractMapper.selectByIdForUpdate(contractId);
        if (StrUtil.isNotBlank(contract.getContractNo())) return contract.getContractNo();
        NumberingRuleVersionDO rule = ruleMapper.selectPublished(contract.getTypeId());
        if (rule == null) rule = createDefaultRule();
        String periodKey = periodKey(rule.getResetPeriod());
        NumberSequenceDO sequence = sequenceMapper.selectForUpdate(rule.getId(), periodKey);
        if (sequence == null) {
            sequence = new NumberSequenceDO().setRuleVersionId(rule.getId()).setPeriodKey(periodKey).setCurrentValue(1L);
            sequenceMapper.insert(sequence);
        } else {
            sequence.setCurrentValue(sequence.getCurrentValue() + 1);
            sequenceMapper.updateById(sequence);
        }
        String date = StrUtil.isBlank(rule.getDatePattern()) ? ""
                : LocalDate.now().format(DateTimeFormatter.ofPattern(rule.getDatePattern()));
        String separator = StrUtil.nullToEmpty(rule.getSeparator());
        String sequenceText = String.format("%0" + rule.getSequenceLength() + "d", sequence.getCurrentValue());
        if (Boolean.FALSE.equals(rule.getIncludePartyShortName())) {
            throw exception(PARAM_INVALID, "正式编号规则必须包含我方主体简称号段");
        }
        String shortName = resolveOurPartyShortName(contractId);
        StringBuilder number = new StringBuilder(StrUtil.nullToEmpty(rule.getPrefix()))
                .append(shortName).append(separator);
        if (StrUtil.isNotBlank(date)) number.append(date).append(separator);
        String contractNo = number.append(sequenceText).toString();
        if (contractNo.length() > 64) throw exception(PARAM_INVALID, "正式编号长度不能超过 64");
        contractMapper.updateById(new ContractDO().setId(contractId).setContractNo(contractNo)
                .setNumberingRuleVersionId(rule.getId()));
        return contractNo;
    }

    private NumberingRuleVersionDO createDefaultRule() {
        NumberingRuleVersionDO rule = new NumberingRuleVersionDO().setRuleCode("DEFAULT")
                .setVersionNo(ruleMapper.selectMaxVersionNo("DEFAULT") + 1).setStatus("PUBLISHED").setPrefix("HT-")
                .setDatePattern("yyyy").setSeparator("-").setSequenceLength(4)
                .setResetPeriod("YEAR").setIncludePartyShortName(Boolean.TRUE).setEffectiveTime(LocalDateTime.now());
        ruleMapper.insert(rule);
        return rule;
    }

    private String periodKey(String resetPeriod) {
        if ("MONTH".equals(resetPeriod)) return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        if ("NONE".equals(resetPeriod)) return "ALL";
        return String.valueOf(LocalDate.now().getYear());
    }

    private String resolveOurPartyShortName(Long contractId) {
        List<ContractPartyDO> parties = contractPartyMapper.selectListByContractId(contractId);
        ContractPartyDO ourSide = parties.stream().filter(p -> "OUR_SIDE".equals(p.getRoleCode())).findFirst().orElse(null);
        if (ourSide == null || ourSide.getPartyId() == null) throw exception(OUR_PARTY_SHORT_NAME_REQUIRED);
        PartyDO party = partyMapper.selectById(ourSide.getPartyId());
        String shortName = party == null ? "" : StrUtil.nullToEmpty(party.getShortName()).trim();
        if (StrUtil.isBlank(shortName) || shortName.length() > 32
                || !shortName.matches("[\\p{L}\\p{N}_-]+")) {
            throw exception(OUR_PARTY_SHORT_NAME_REQUIRED);
        }
        return shortName;
    }
}
