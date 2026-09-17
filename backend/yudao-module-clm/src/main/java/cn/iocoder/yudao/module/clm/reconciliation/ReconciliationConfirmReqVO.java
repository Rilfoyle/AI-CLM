package cn.iocoder.yudao.module.clm.reconciliation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReconciliationConfirmReqVO {

    @NotNull
    private Long runId;

    @NotNull
    private Long bindingId;

    @NotBlank
    @Size(max = 1000)
    private String opinion;
}
