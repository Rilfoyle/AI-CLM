package cn.iocoder.yudao.module.clm.ai;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@TableName("clm_ai_review_run")
@KeySequence("clm_ai_review_run_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AiReviewRunDO extends TenantBaseDO {
    @TableId private Long id;
    private Long contractId;
    private Long revisionId;
    private String runType;
    private String providerCode;
    private String status;
    private String inputFingerprint;
    private String resultJson;
    private String errorMessage;
    private LocalDateTime finishedTime;
}
