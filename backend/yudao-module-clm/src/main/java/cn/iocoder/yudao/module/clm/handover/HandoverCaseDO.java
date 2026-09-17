package cn.iocoder.yudao.module.clm.handover;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@TableName("clm_handover_case")
@KeySequence("clm_handover_case_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class HandoverCaseDO extends TenantBaseDO {
    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_CANCELED = "CANCELED";

    @TableId private Long id;
    private Long sourceUserId;
    private Long targetUserId;
    private String status;
    private String reason;
    private Long handledBy;
    private LocalDateTime finishedTime;
}
