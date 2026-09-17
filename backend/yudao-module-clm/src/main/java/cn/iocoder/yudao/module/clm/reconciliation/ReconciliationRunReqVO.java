package cn.iocoder.yudao.module.clm.reconciliation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReconciliationRunReqVO {
    @NotBlank @Size(max = 128) private String runKey;
    @Pattern(regexp = "MANUAL|SCHEDULED|REPLAY") private String triggerType = "MANUAL";
}
