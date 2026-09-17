package cn.iocoder.yudao.module.clm.handover;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@TableName("clm_handover_item")
@KeySequence("clm_handover_item_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class HandoverItemDO extends TenantBaseDO {
    public static final String TYPE_CONTRACT = "CONTRACT";
    public static final String TYPE_ACTIVE_TASK = "ACTIVE_TASK";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_TRANSFERRED = "TRANSFERRED";
    public static final String STATUS_SKIPPED = "SKIPPED";
    public static final String STATUS_FAILED = "FAILED";

    @TableId private Long id;
    private Long caseId;
    private String itemType;
    private Long contractId;
    private String taskId;
    private Long originalAssignee;
    private Long targetUserId;
    private String status;
    private String resultMessage;
}
