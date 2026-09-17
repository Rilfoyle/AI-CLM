package cn.iocoder.yudao.module.clm.routing;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmUserTaskApproveTypeEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils;
import cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.clm.service.contracttype.ContractTypeService;
import cn.iocoder.yudao.module.clm.service.audit.ClmAuditService;
import cn.iocoder.yudao.module.clm.enums.audit.ClmAuditActionEnum;
import cn.iocoder.yudao.module.clm.enums.audit.ClmAuditAggregateTypeEnum;
import jakarta.annotation.Resource;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.clm.routing.RoutingGovernanceErrors.*;

@Service
public class RoutingGovernanceService {
    @Resource private RoutingRuleVersionMapper ruleMapper;
    @Resource private ContractTypeService contractTypeService;
    @Resource private BpmProcessDefinitionService processDefinitionService;
    @Resource private ClmAuditService auditService;

    public PageResult<RoutingRuleRespVO> page(RoutingRulePageReqVO reqVO) {
        PageResult<RoutingRuleVersionDO> page = ruleMapper.selectPage(reqVO);
        List<RoutingRuleRespVO> rows = new ArrayList<>(page.getList().size());
        for (RoutingRuleVersionDO rule : page.getList()) rows.add(toResp(rule));
        return new PageResult<>(rows, page.getTotal());
    }

    public RoutingRuleRespVO get(Long id) {
        RoutingRuleVersionDO rule = ruleMapper.selectById(id);
        if (rule == null) throw exception(RULE_NOT_EXISTS);
        return toResp(rule);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long saveDraft(RoutingRuleSaveReqVO reqVO) {
        normalizeAndValidate(reqVO);
        if (reqVO.getContractTypeId() != null) contractTypeService.getRequiredContractType(reqVO.getContractTypeId());
        if (reqVO.getId() != null) {
            RoutingRuleVersionDO existing = ruleMapper.selectByIdForUpdate(reqVO.getId());
            if (existing == null) throw exception(RULE_NOT_EXISTS);
            if (!"DRAFT".equals(existing.getStatus())) throw exception(RULE_NOT_DRAFT);
            if (!Objects.equals(existing.getRuleCode(), reqVO.getRuleCode())) throw exception(CODE_IMMUTABLE);
            if (!Objects.equals(existing.getContractTypeId(), reqVO.getContractTypeId())) throw exception(SCOPE_IMMUTABLE);
            ruleMapper.updateById(apply(reqVO, new RoutingRuleVersionDO().setId(existing.getId())));
            auditService.record(ClmAuditAggregateTypeEnum.ROUTING_RULE, existing.getId(), null,
                    ClmAuditActionEnum.ROUTING_RULE_DRAFT_SAVE,
                    Map.of("ruleCode", existing.getRuleCode(), "versionNo", existing.getVersionNo()));
            return existing.getId();
        }
        List<RoutingRuleVersionDO> versions = ruleMapper.selectListByRuleCode(reqVO.getRuleCode());
        RoutingRuleVersionDO draft = versions.stream().filter(v -> "DRAFT".equals(v.getStatus())).findFirst().orElse(null);
        if (draft != null) throw exception(DRAFT_EXISTS, reqVO.getRuleCode());
        if (!versions.isEmpty() && !Objects.equals(versions.get(0).getContractTypeId(), reqVO.getContractTypeId())) {
            throw exception(SCOPE_IMMUTABLE);
        }
        RoutingRuleVersionDO created = apply(reqVO, new RoutingRuleVersionDO())
                .setVersionNo(ruleMapper.selectMaxVersionNo(reqVO.getRuleCode()) + 1)
                .setStatus("DRAFT");
        ruleMapper.insert(created);
        auditService.record(ClmAuditAggregateTypeEnum.ROUTING_RULE, created.getId(), null,
                ClmAuditActionEnum.ROUTING_RULE_DRAFT_SAVE,
                Map.of("ruleCode", created.getRuleCode(), "versionNo", created.getVersionNo()));
        return created.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id) {
        RoutingRuleVersionDO rule = ruleMapper.selectByIdForUpdate(id);
        if (rule == null) throw exception(RULE_NOT_EXISTS);
        if ("PUBLISHED".equals(rule.getStatus())) return;
        if (!"DRAFT".equals(rule.getStatus())) throw exception(RULE_NOT_DRAFT);
        RoutingConditionSupport.parse(rule.getConditionJson());
        validateProcessDefinition(rule.getProcessDefinitionKey());
        for (RoutingRuleVersionDO old : ruleMapper.selectListByRuleCode(rule.getRuleCode())) {
            if ("PUBLISHED".equals(old.getStatus()) && !Objects.equals(old.getId(), id)) {
                ruleMapper.updateById(new RoutingRuleVersionDO().setId(old.getId()).setStatus("INACTIVE"));
            }
        }
        ruleMapper.updateById(new RoutingRuleVersionDO().setId(id).setStatus("PUBLISHED")
                .setEffectiveTime(LocalDateTime.now()));
        auditService.record(ClmAuditAggregateTypeEnum.ROUTING_RULE, id, null,
                ClmAuditActionEnum.ROUTING_RULE_PUBLISH,
                Map.of("ruleCode", rule.getRuleCode(), "versionNo", rule.getVersionNo()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void disable(Long id) {
        RoutingRuleVersionDO rule = ruleMapper.selectByIdForUpdate(id);
        if (rule == null) throw exception(RULE_NOT_EXISTS);
        if (!"INACTIVE".equals(rule.getStatus())) {
            ruleMapper.updateById(new RoutingRuleVersionDO().setId(id).setStatus("INACTIVE"));
        }
        auditService.record(ClmAuditAggregateTypeEnum.ROUTING_RULE, id, null,
                ClmAuditActionEnum.ROUTING_RULE_DISABLE,
                Map.of("ruleCode", rule.getRuleCode(), "versionNo", rule.getVersionNo()));
    }

    public RoutingPrecheckRespVO precheck(RoutingPrecheckReqVO reqVO) {
        contractTypeService.getRequiredContractType(reqVO.getContractTypeId());
        List<RoutingRuleVersionDO> candidates = new ArrayList<>(ruleMapper.selectPublishedCandidates(reqVO.getContractTypeId()));
        if (reqVO.getCandidateVersionId() != null) {
            RoutingRuleVersionDO candidate = ruleMapper.selectById(reqVO.getCandidateVersionId());
            if (candidate == null) throw exception(RULE_NOT_EXISTS);
            if (candidate.getContractTypeId() != null
                    && !Objects.equals(candidate.getContractTypeId(), reqVO.getContractTypeId())) {
                throw exception(PRECHECK_SCOPE_MISMATCH);
            }
            candidates.removeIf(rule -> Objects.equals(rule.getRuleCode(), candidate.getRuleCode()));
            candidates.add(candidate);
        }
        ContractDO sample = new ContractDO().setTypeId(reqVO.getContractTypeId())
                .setOwnerDeptId(reqVO.getOwnerDeptId())
                .setAmount(reqVO.getAmount() == null ? BigDecimal.ZERO : reqVO.getAmount())
                .setCurrency(StrUtil.blankToDefault(reqVO.getCurrency(), "CNY").toUpperCase());
        List<RoutingRuleVersionDO> matched = candidates.stream()
                .filter(rule -> RoutingConditionSupport.matches(rule.getConditionJson(), sample)).toList();
        RoutingPrecheckRespVO resp = new RoutingPrecheckRespVO();
        resp.setMatchedCount(matched.size());
        resp.setResult(matched.isEmpty() ? "ZERO" : matched.size() == 1 ? "UNIQUE" : "MULTIPLE");
        resp.setMatchedRuleVersionIds(matched.stream().map(RoutingRuleVersionDO::getId).toList());
        resp.setProcessDefinitionKeys(matched.stream().map(RoutingRuleVersionDO::getProcessDefinitionKey).toList());
        return resp;
    }

    private RoutingRuleVersionDO apply(RoutingRuleSaveReqVO reqVO, RoutingRuleVersionDO target) {
        return target.setRuleCode(reqVO.getRuleCode()).setName(reqVO.getName())
                .setContractTypeId(reqVO.getContractTypeId()).setPriority(reqVO.getPriority())
                .setConditionJson(JsonUtils.toJsonString(reqVO.getCondition()))
                .setProcessDefinitionKey(reqVO.getProcessDefinitionKey());
    }

    private RoutingRuleRespVO toResp(RoutingRuleVersionDO rule) {
        RoutingRuleRespVO resp = BeanUtils.toBean(rule, RoutingRuleRespVO.class);
        resp.setCondition(RoutingConditionSupport.parse(rule.getConditionJson()));
        return resp;
    }

    private void normalizeAndValidate(RoutingRuleSaveReqVO reqVO) {
        reqVO.setRuleCode(StrUtil.trim(reqVO.getRuleCode()));
        reqVO.setName(StrUtil.trim(reqVO.getName()));
        reqVO.setProcessDefinitionKey(StrUtil.trim(reqVO.getProcessDefinitionKey()));
        if (StrUtil.isBlank(reqVO.getRuleCode())) throw exception(CONDITION_INVALID, "路由编码不能为空");
        if (StrUtil.isBlank(reqVO.getName())) throw exception(CONDITION_INVALID, "路由名称不能为空");
        if (reqVO.getPriority() == null || reqVO.getPriority() < 0 || reqVO.getPriority() > 10000) {
            throw exception(CONDITION_INVALID, "优先级必须为 0 到 10000");
        }
        reqVO.setCondition(RoutingConditionSupport.normalize(reqVO.getCondition()));
        if (StrUtil.isBlank(reqVO.getProcessDefinitionKey())) {
            throw exception(PROCESS_DEFINITION_INVALID, reqVO.getProcessDefinitionKey());
        }
    }

    private void validateProcessDefinition(String key) {
        ProcessDefinition definition = processDefinitionService.getActiveProcessDefinition(key);
        if (definition == null) throw exception(PROCESS_DEFINITION_INVALID, key);
        BpmnModel model = processDefinitionService.getProcessDefinitionBpmnModel(definition.getId());
        if (model == null) throw exception(PROCESS_DEFINITION_INVALID, key);
        List<UserTask> tasks = BpmnModelUtils.getBpmnModelElements(model, UserTask.class);
        if (tasks.isEmpty()) throw exception(PROCESS_DEFINITION_INVALID, key);
        for (UserTask task : tasks) {
            Integer approveType = BpmnModelUtils.parseApproveType(task);
            boolean automatic = Objects.equals(approveType, BpmUserTaskApproveTypeEnum.AUTO_APPROVE.getType())
                    || Objects.equals(approveType, BpmUserTaskApproveTypeEnum.AUTO_REJECT.getType());
            if (!automatic && BpmnModelUtils.parseCandidateStrategy(task) == null) {
                throw exception(PROCESS_DEFINITION_INVALID, key);
            }
        }
    }
}
