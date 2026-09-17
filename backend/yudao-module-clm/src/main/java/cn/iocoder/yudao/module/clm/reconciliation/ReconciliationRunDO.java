package cn.iocoder.yudao.module.clm.reconciliation;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@TableName("clm_reconciliation_run")
@KeySequence("clm_reconciliation_run_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ReconciliationRunDO extends TenantBaseDO {
    @TableId private Long id;
    private String runKey;
    private String triggerType;
    private String status;
    private Integer scannedCount;
    private Integer issueCount;
    private Integer repairedCount;
    private String reportJson;
    private LocalDateTime finishedTime;
}
