package cn.iocoder.yudao.module.clm.routing;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class RoutingPrecheckReqVO {
    private Long candidateVersionId;
    @NotNull(message = "合同类型不能为空")
    private Long contractTypeId;
    private Long ownerDeptId;
    private BigDecimal amount;
    private String currency;
}
