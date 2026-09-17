package cn.iocoder.yudao.module.clm.approval;

import cn.iocoder.yudao.module.clm.revision.ContractRevisionSaveReqVO;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ApprovalEditReqVO extends ContractRevisionSaveReqVO {
    @NotBlank(message = "审批任务编号不能为空")
    private String taskId;
    @NotBlank(message = "请求幂等键不能为空")
    @Size(max = 64, message = "请求幂等键长度不能超过 64 个字符")
    private String requestId;
}
