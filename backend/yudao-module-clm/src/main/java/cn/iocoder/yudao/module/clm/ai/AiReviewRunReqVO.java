package cn.iocoder.yudao.module.clm.ai;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class AiReviewRunReqVO {
    @NotNull private Long contractId;
    @NotNull private Long revisionId;
    @Pattern(regexp = "SUMMARY|EXTRACTION|RISK_REVIEW") private String runType = "RISK_REVIEW";
}
