package cn.iocoder.yudao.module.clm.collaboration;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@TableName("clm_collaboration_case")
@KeySequence("clm_collaboration_case_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CollaborationCaseDO extends TenantBaseDO {

    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_CHANGE_REQUESTED = "CHANGE_REQUESTED";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_CANCELED = "CANCELED";

    @TableId
    private Long id;
    private Long contractId;
    private Long requestedRevisionId;
    private Long completedRevisionId;
    private Long initiatorUserId;
    private Long legalUserId;
    private String status;
    private String reason;
    private String conclusion;
    private LocalDateTime finishedTime;

    public boolean isActive() {
        return STATUS_RUNNING.equals(status) || STATUS_CHANGE_REQUESTED.equals(status);
    }

}
