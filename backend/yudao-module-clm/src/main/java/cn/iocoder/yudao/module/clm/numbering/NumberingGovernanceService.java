package cn.iocoder.yudao.module.clm.numbering;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.clm.service.contracttype.ContractTypeService;
import cn.iocoder.yudao.module.clm.service.audit.ClmAuditService;
import cn.iocoder.yudao.module.clm.enums.audit.ClmAuditActionEnum;
import cn.iocoder.yudao.module.clm.enums.audit.ClmAuditAggregateTypeEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.clm.numbering.NumberingGovernanceErrors.*;

@Service
public class NumberingGovernanceService {
    private static final Set<String> DATE_PATTERNS = Set.of("", "yyyy", "yyyyMM", "yyyyMMdd");
    private static final Set<String> RESET_PERIODS = Set.of("NONE", "YEAR", "MONTH");
    private static final Set<String> SEPARATORS = Set.of("", "-", "_", "/", ".");

    @Resource private NumberingRuleVersionMapper ruleMapper;
    @Resource private ContractTypeService contractTypeService;
    @Resource private ClmAuditService auditService;

    public PageResult<NumberingRuleRespVO> page(NumberingRulePageReqVO reqVO) {
        PageResult<NumberingRuleVersionDO> page = ruleMapper.selectPage(reqVO);
        List<NumberingRuleRespVO> rows = new ArrayList<>(page.getList().size());
        for (NumberingRuleVersionDO rule : page.getList()) rows.add(toResp(rule));
        return new PageResult<>(rows, page.getTotal());
    }

    public NumberingRuleRespVO get(Long id) {
        NumberingRuleVersionDO rule = ruleMapper.selectById(id);
        if (rule == null) throw exception(RULE_NOT_EXISTS);
        return toResp(rule);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long saveDraft(NumberingRuleSaveReqVO reqVO) {
        normalizeAndValidate(reqVO);
        if (reqVO.getContractTypeId() != null) {
            contractTypeService.getRequiredContractType(reqVO.getContractTypeId());
        }
        if (reqVO.getId() != null) {
            NumberingRuleVersionDO existing = ruleMapper.selectByIdForUpdate(reqVO.getId());
            if (existing == null) throw exception(RULE_NOT_EXISTS);
            if (!"DRAFT".equals(existing.getStatus())) throw exception(RULE_NOT_DRAFT);
            if (!Objects.equals(existing.getRuleCode(), reqVO.getRuleCode())) throw exception(CODE_IMMUTABLE);
            if (!Objects.equals(existing.getContractTypeId(), reqVO.getContractTypeId())) throw exception(SCOPE_IMMUTABLE);
            ruleMapper.updateById(apply(reqVO, new NumberingRuleVersionDO().setId(existing.getId())));
            auditService.record(ClmAuditAggregateTypeEnum.NUMBERING_RULE, existing.getId(), null,
                    ClmAuditActionEnum.NUMBERING_RULE_DRAFT_SAVE,
                    Map.of("ruleCode", existing.getRuleCode(), "versionNo", existing.getVersionNo()));
            return existing.getId();
        }
        List<NumberingRuleVersionDO> versions = ruleMapper.selectListByRuleCode(reqVO.getRuleCode());
        NumberingRuleVersionDO draft = versions.stream().filter(v -> "DRAFT".equals(v.getStatus())).findFirst().orElse(null);
        if (draft != null) throw exception(DRAFT_EXISTS, reqVO.getRuleCode());
        if (!versions.isEmpty() && !Objects.equals(versions.get(0).getContractTypeId(), reqVO.getContractTypeId())) {
            throw exception(SCOPE_IMMUTABLE);
        }
        NumberingRuleVersionDO created = apply(reqVO, new NumberingRuleVersionDO())
                .setVersionNo(ruleMapper.selectMaxVersionNo(reqVO.getRuleCode()) + 1)
                .setStatus("DRAFT");
        ruleMapper.insert(created);
        auditService.record(ClmAuditAggregateTypeEnum.NUMBERING_RULE, created.getId(), null,
                ClmAuditActionEnum.NUMBERING_RULE_DRAFT_SAVE,
                Map.of("ruleCode", created.getRuleCode(), "versionNo", created.getVersionNo()));
        return created.getId();
    }

    public NumberingPreviewRespVO preview(NumberingRuleSaveReqVO reqVO) {
        normalizeAndValidate(reqVO);
        String shortName = normalizeShortName(reqVO.getOurPartyShortName());
        if (Boolean.TRUE.equals(reqVO.getIncludePartyShortName()) && StrUtil.isBlank(shortName)) {
            throw exception(PARAM_INVALID, "预览时必须提供我方主体简称");
        }
        return new NumberingPreviewRespVO(buildSample(reqVO.getPrefix(), reqVO.getDatePattern(),
                reqVO.getSeparator(), reqVO.getSequenceLength(), shortName));
    }

    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id) {
        NumberingRuleVersionDO rule = ruleMapper.selectByIdForUpdate(id);
        if (rule == null) throw exception(RULE_NOT_EXISTS);
        if ("PUBLISHED".equals(rule.getStatus())) return;
        if (!"DRAFT".equals(rule.getStatus())) throw exception(RULE_NOT_DRAFT);
        normalizeAndValidate(toReq(rule));
        for (NumberingRuleVersionDO old : ruleMapper.selectPublishedByScope(rule.getContractTypeId())) {
            if (!Objects.equals(old.getId(), id)) {
                ruleMapper.updateById(new NumberingRuleVersionDO().setId(old.getId()).setStatus("INACTIVE"));
            }
        }
        ruleMapper.updateById(new NumberingRuleVersionDO().setId(id).setStatus("PUBLISHED")
                .setEffectiveTime(LocalDateTime.now()));
        auditService.record(ClmAuditAggregateTypeEnum.NUMBERING_RULE, id, null,
                ClmAuditActionEnum.NUMBERING_RULE_PUBLISH,
                Map.of("ruleCode", rule.getRuleCode(), "versionNo", rule.getVersionNo()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void disable(Long id) {
        NumberingRuleVersionDO rule = ruleMapper.selectByIdForUpdate(id);
        if (rule == null) throw exception(RULE_NOT_EXISTS);
        if (!"INACTIVE".equals(rule.getStatus())) {
            ruleMapper.updateById(new NumberingRuleVersionDO().setId(id).setStatus("INACTIVE"));
        }
        auditService.record(ClmAuditAggregateTypeEnum.NUMBERING_RULE, id, null,
                ClmAuditActionEnum.NUMBERING_RULE_DISABLE,
                Map.of("ruleCode", rule.getRuleCode(), "versionNo", rule.getVersionNo()));
    }

    private NumberingRuleVersionDO apply(NumberingRuleSaveReqVO reqVO, NumberingRuleVersionDO target) {
        return target.setRuleCode(reqVO.getRuleCode()).setContractTypeId(reqVO.getContractTypeId())
                .setPrefix(reqVO.getPrefix()).setDatePattern(reqVO.getDatePattern())
                .setSeparator(reqVO.getSeparator()).setSequenceLength(reqVO.getSequenceLength())
                .setResetPeriod(reqVO.getResetPeriod()).setIncludePartyShortName(reqVO.getIncludePartyShortName());
    }

    private NumberingRuleRespVO toResp(NumberingRuleVersionDO rule) {
        NumberingRuleRespVO resp = BeanUtils.toBean(rule, NumberingRuleRespVO.class);
        try {
            resp.setSample(buildSample(rule.getPrefix(), rule.getDatePattern(), rule.getSeparator(),
                    rule.getSequenceLength(), Boolean.FALSE.equals(rule.getIncludePartyShortName()) ? "" : "主体简称"));
        } catch (RuntimeException ignored) {
            resp.setSample(null);
        }
        return resp;
    }

    private NumberingRuleSaveReqVO toReq(NumberingRuleVersionDO rule) {
        return BeanUtils.toBean(rule, NumberingRuleSaveReqVO.class);
    }

    private void normalizeAndValidate(NumberingRuleSaveReqVO reqVO) {
        reqVO.setRuleCode(StrUtil.trim(reqVO.getRuleCode()));
        reqVO.setPrefix(StrUtil.nullToEmpty(reqVO.getPrefix()).trim());
        reqVO.setDatePattern(StrUtil.nullToEmpty(reqVO.getDatePattern()).trim());
        reqVO.setSeparator(StrUtil.nullToEmpty(reqVO.getSeparator()));
        reqVO.setResetPeriod(StrUtil.nullToEmpty(reqVO.getResetPeriod()).trim().toUpperCase());
        if (StrUtil.isBlank(reqVO.getRuleCode())) throw exception(PARAM_INVALID, "规则编码不能为空");
        if (!reqVO.getPrefix().matches("[\\p{L}\\p{N}_-]*")) throw exception(PARAM_INVALID, "前缀只能包含文字、数字、下划线和连字符");
        if (!DATE_PATTERNS.contains(reqVO.getDatePattern())) throw exception(PARAM_INVALID, "日期格式仅支持空、yyyy、yyyyMM、yyyyMMdd");
        if (!SEPARATORS.contains(reqVO.getSeparator())) throw exception(PARAM_INVALID, "分隔符仅支持 -、_、/、. 或空");
        if (reqVO.getSequenceLength() == null || reqVO.getSequenceLength() < 1 || reqVO.getSequenceLength() > 12) {
            throw exception(PARAM_INVALID, "序列长度必须为 1 到 12");
        }
        if (!RESET_PERIODS.contains(reqVO.getResetPeriod())) throw exception(PARAM_INVALID, "重置周期仅支持 NONE、YEAR、MONTH");
        if (!Boolean.TRUE.equals(reqVO.getIncludePartyShortName())) {
            throw exception(PARAM_INVALID, "一期发布规则必须包含我方主体简称号段");
        }
        String sample = buildSample(reqVO.getPrefix(), reqVO.getDatePattern(), reqVO.getSeparator(),
                reqVO.getSequenceLength(), "主体简称");
        if (sample.length() > 64) throw exception(PARAM_INVALID, "编号样例长度不能超过 64");
    }

    private String buildSample(String prefix, String datePattern, String separator, Integer sequenceLength, String shortName) {
        String date = StrUtil.isBlank(datePattern) ? "" : LocalDate.now().format(DateTimeFormatter.ofPattern(datePattern));
        String segmentSeparator = StrUtil.nullToEmpty(separator);
        StringBuilder result = new StringBuilder(StrUtil.nullToEmpty(prefix));
        if (StrUtil.isNotBlank(shortName)) result.append(shortName).append(segmentSeparator);
        if (StrUtil.isNotBlank(date)) result.append(date).append(segmentSeparator);
        return result.append(String.format("%0" + sequenceLength + "d", 1)).toString();
    }

    private String normalizeShortName(String value) {
        String shortName = StrUtil.nullToEmpty(value).trim();
        if (StrUtil.isBlank(shortName)) return shortName;
        if (shortName.length() > 32 || !shortName.matches("[\\p{L}\\p{N}_-]+")) {
            throw exception(PARAM_INVALID, "我方主体简称只能包含文字、数字、下划线和连字符，且不超过 32 字");
        }
        return shortName;
    }
}
