package cn.iocoder.yudao.module.clm.handover;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class HandoverReassignReqVO {
    @NotNull(message = "交接异常不能为空")
    private Long caseId;
    @NotNull(message = "接手用户不能为空")
    private Long targetUserId;
    @NotBlank(message = "交接原因不能为空")
    @Size(max = 1000, message = "交接原因不能超过 1000 字")
    private String reason;
    private List<Long> itemIds;
}
