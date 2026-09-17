package cn.iocoder.yudao.module.clm.integration;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class IntegrationStatusRespVO {
    private Boolean configured;
    private String mode;
    private String message;
}
