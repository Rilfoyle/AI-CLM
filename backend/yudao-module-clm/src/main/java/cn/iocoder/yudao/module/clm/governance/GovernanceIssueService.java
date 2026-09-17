package cn.iocoder.yudao.module.clm.governance;

import cn.iocoder.yudao.framework.common.pojo.PageResult;

import java.util.Map;

public interface GovernanceIssueService {
    PageResult<GovernanceIssueDO> getPage(GovernanceIssuePageReqVO reqVO);
    Long open(String issueType, String sourceRef, Long contractId, Long bindingId,
              Long ownerUserId, String summary, Map<String, Object> detail);
    void resolve(Long id, Long userId);
}
