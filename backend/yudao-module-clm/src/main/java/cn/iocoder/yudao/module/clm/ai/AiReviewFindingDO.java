package cn.iocoder.yudao.module.clm.ai;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@TableName("clm_ai_review_finding")
@KeySequence("clm_ai_review_finding_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AiReviewFindingDO extends TenantBaseDO {
    @TableId private Long id;
    private Long runId;
    private Long contractId;
    private Long revisionId;
    private String findingType;
    private String severity;
    private String title;
    private String detail;
    private String locatorJson;
    private String resolution;
    private Long resolvedBy;
    private LocalDateTime resolvedTime;
}
