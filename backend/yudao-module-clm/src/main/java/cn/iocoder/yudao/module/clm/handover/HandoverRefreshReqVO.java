package cn.iocoder.yudao.module.clm.handover;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class HandoverRefreshReqVO {
    @NotNull(message = "离职用户不能为空")
    private Long sourceUserId;
}
