package cn.iocoder.yudao.module.clm.governance;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class GovernanceIssuePageReqVO extends PageParam {
    private String issueType;
    private String status;
    private Long contractId;
    private Long ownerUserId;
}
