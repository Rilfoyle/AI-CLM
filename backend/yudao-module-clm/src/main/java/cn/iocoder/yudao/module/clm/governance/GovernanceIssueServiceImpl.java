package cn.iocoder.yudao.module.clm.governance;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.time.LocalDateTime;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.clm.enums.ErrorCodeConstants.GOVERNANCE_ISSUE_NOT_EXISTS;

@Service
public class GovernanceIssueServiceImpl implements GovernanceIssueService {

    @Resource private GovernanceIssueMapper issueMapper;

    @Override
    public PageResult<GovernanceIssueDO> getPage(GovernanceIssuePageReqVO reqVO) {
        return issueMapper.selectPage(reqVO);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Long open(String issueType, String sourceRef, Long contractId, Long bindingId,
                     Long ownerUserId, String summary, Map<String, Object> detail) {
        String normalizedRef = StrUtil.blankToDefault(sourceRef,
                issueType + ":" + (contractId == null ? "GLOBAL" : contractId));
        GovernanceIssueDO existing = issueMapper.selectOpenBySource(issueType, normalizedRef);
        if (existing != null) {
            issueMapper.updateById(new GovernanceIssueDO().setId(existing.getId())
                    .setSummary(summary).setDetailJson(detail == null ? null : JsonUtils.toJsonString(detail))
                    .setOwnerUserId(ownerUserId).setContractId(contractId).setApprovalBindingId(bindingId));
            return existing.getId();
        }
        GovernanceIssueDO issue = new GovernanceIssueDO().setIssueType(issueType)
                .setStatus(GovernanceIssueDO.STATUS_OPEN).setContractId(contractId)
                .setApprovalBindingId(bindingId).setSourceRef(normalizedRef)
                .setSummary(summary).setDetailJson(detail == null ? null : JsonUtils.toJsonString(detail))
                .setOwnerUserId(ownerUserId);
        issueMapper.insert(issue);
        return issue.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resolve(Long id, Long userId) {
        GovernanceIssueDO issue = issueMapper.selectById(id);
        if (issue == null) throw exception(GOVERNANCE_ISSUE_NOT_EXISTS);
        if (GovernanceIssueDO.STATUS_RESOLVED.equals(issue.getStatus())) return;
        issueMapper.updateById(new GovernanceIssueDO().setId(id)
                .setStatus(GovernanceIssueDO.STATUS_RESOLVED)
                .setResolvedBy(userId).setResolvedTime(LocalDateTime.now()));
    }
}
