package cn.iocoder.yudao.module.clm.approval;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ApprovalDocumentEditReqVO {

    @NotBlank(message = "审批任务编号不能为空")
    private String taskId;

    @NotBlank(message = "请求幂等键不能为空")
    @Size(max = 64, message = "请求幂等键长度不能超过 64 个字符")
    private String requestId;

    @NotNull(message = "合同编号不能为空")
    private Long contractId;

    @NotNull(message = "基线修订编号不能为空")
    private Long baseRevisionId;

    @NotBlank(message = "修订原因不能为空")
    private String changeReason;
}
