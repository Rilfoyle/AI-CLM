package cn.iocoder.yudao.module.clm.integration;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@TableName("clm_integration_delivery")
@KeySequence("clm_integration_delivery_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class IntegrationDeliveryDO extends TenantBaseDO {
    @TableId private Long id;
    private String deliveryKey;
    private String channel;
    private String messageType;
    private Long recipientUserId;
    private Long contractId;
    private String taskId;
    private String deepLinkPath;
    private String status;
    private Integer attemptCount;
    private LocalDateTime nextRetryTime;
    private String lastError;
}
