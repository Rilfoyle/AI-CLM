package cn.iocoder.yudao.module.clm.approval;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ApprovalDecisionReqVO {
    @NotBlank(message = "任务编号不能为空")
    private String taskId;
    @NotNull(message = "决定修订编号不能为空")
    private Long revisionId;
    private String reason;
    @NotBlank(message = "请求幂等键不能为空")
    @Size(max = 64, message = "请求幂等键长度不能超过 64 个字符")
    private String requestId;
    private String targetActivityId;
}
