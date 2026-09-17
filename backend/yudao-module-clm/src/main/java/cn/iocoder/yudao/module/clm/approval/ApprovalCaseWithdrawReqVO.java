package cn.iocoder.yudao.module.clm.approval;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ApprovalCaseWithdrawReqVO {
    @NotNull(message = "审批业务单编号不能为空")
    private Long approvalCaseId;

    @NotBlank(message = "撤回原因不能为空")
    @Size(max = 500, message = "撤回原因不能超过 500 个字符")
    private String reason;
}
