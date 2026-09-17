package cn.iocoder.yudao.module.clm.governance;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@TableName("clm_governance_issue")
@KeySequence("clm_governance_issue_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class GovernanceIssueDO extends TenantBaseDO {
    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_RESOLVED = "RESOLVED";

    @TableId private Long id;
    private String issueType;
    private String status;
    private Long contractId;
    private Long approvalBindingId;
    private String sourceRef;
    private String summary;
    private String detailJson;
    private Long ownerUserId;
    private Long resolvedBy;
    private LocalDateTime resolvedTime;
}
