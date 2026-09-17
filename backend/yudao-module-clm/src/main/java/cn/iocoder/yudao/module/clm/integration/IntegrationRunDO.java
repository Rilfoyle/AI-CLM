package cn.iocoder.yudao.module.clm.integration;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@TableName("clm_integration_run")
@KeySequence("clm_integration_run_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class IntegrationRunDO extends TenantBaseDO {
    @TableId private Long id;
    private String integrationType;
    private String runKey;
    private String mode;
    private String status;
    private String summaryJson;
    private String errorMessage;
    private LocalDateTime finishedTime;
}
