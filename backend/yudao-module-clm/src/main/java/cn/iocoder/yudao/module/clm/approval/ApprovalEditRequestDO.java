package cn.iocoder.yudao.module.clm.approval;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@TableName("clm_approval_edit_request")
@KeySequence("clm_approval_edit_request_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ApprovalEditRequestDO extends TenantBaseDO {
    @TableId private Long id;
    private String taskId;
    private Long approvalCaseId;
    private Long contractId;
    private Long baseRevisionId;
    private Long resultRevisionId;
    private Long resultApprovalCaseId;
    private String requestId;
    private String changeLevel;
    private String reason;
}
