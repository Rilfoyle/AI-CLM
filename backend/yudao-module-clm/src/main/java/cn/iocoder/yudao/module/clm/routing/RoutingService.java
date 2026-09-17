package cn.iocoder.yudao.module.clm.routing;

import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.clm.governance.GovernanceIssueService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.clm.enums.ErrorCodeConstants.*;

@Service
public class RoutingService {
    @Resource private RoutingRuleVersionMapper ruleMapper;
    @Resource private GovernanceIssueService governanceIssueService;

    @Transactional(rollbackFor = Exception.class)
    public RoutingRuleVersionDO resolveUnique(ContractDO contract) {
        List<RoutingRuleVersionDO> candidates = ruleMapper.selectPublishedCandidates(contract.getTypeId());
        if (candidates.isEmpty()) {
            openIssue(contract, "ROUTE_ZERO", candidates, "合同分类没有已发布的业务单据流程配置");
            throw exception(ROUTING_RULE_NO_MATCH);
        }
        List<RoutingRuleVersionDO> matched = new ArrayList<>();
        for (RoutingRuleVersionDO candidate : candidates) if (matches(candidate, contract)) matched.add(candidate);
        if (matched.isEmpty()) {
            openIssue(contract, "ROUTE_ZERO", candidates, "合同没有命中唯一业务单据流程配置");
            throw exception(ROUTING_RULE_NO_MATCH);
        }
        if (matched.size() > 1) {
            openIssue(contract, "ROUTE_MULTIPLE", matched, "合同同时命中多条业务单据流程配置");
            throw exception(ROUTING_RULE_AMBIGUOUS);
        }
        return matched.get(0);
    }

    private void openIssue(ContractDO contract, String issueType, List<RoutingRuleVersionDO> candidates,
                           String summary) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("contractTypeId", contract.getTypeId());
        detail.put("ownerDeptId", contract.getOwnerDeptId());
        detail.put("amount", contract.getAmount());
        detail.put("candidateRuleVersionIds", candidates.stream().map(RoutingRuleVersionDO::getId).toList());
        governanceIssueService.open(issueType,
                "routing:" + contract.getId() + ":" + contract.getCurrentRevisionId(),
                contract.getId(), contract.getCurrentBindingId(), contract.getOwnerUserId(), summary, detail);
    }

    boolean matches(RoutingRuleVersionDO rule, ContractDO contract) {
        return RoutingConditionSupport.matches(rule.getConditionJson(), contract);
    }
}
