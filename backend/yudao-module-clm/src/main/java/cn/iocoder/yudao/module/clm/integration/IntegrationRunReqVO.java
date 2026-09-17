package cn.iocoder.yudao.module.clm.integration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class IntegrationRunReqVO {
    @NotBlank @Pattern(regexp = "DINGTALK_ORG_SYNC|DINGTALK_SSO_CHECK") private String integrationType;
    @NotBlank @Size(max = 128) private String runKey;
}
