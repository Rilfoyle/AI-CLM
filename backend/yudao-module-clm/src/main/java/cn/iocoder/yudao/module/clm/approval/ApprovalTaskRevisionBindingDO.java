package cn.iocoder.yudao.module.clm.approval;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@TableName("clm_approval_task_revision_binding")
@KeySequence("clm_approval_task_revision_binding_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ApprovalTaskRevisionBindingDO extends TenantBaseDO {
    @TableId private Long id;
    private String taskId;
    private Long approvalCaseId;
    private Long contractId;
    private Long decisionRevisionId;
    private String action;
    private String reason;
    private String requestId;
}
