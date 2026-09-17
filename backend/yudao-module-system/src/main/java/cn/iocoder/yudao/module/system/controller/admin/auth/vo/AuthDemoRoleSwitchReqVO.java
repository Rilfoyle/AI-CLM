package cn.iocoder.yudao.module.system.controller.admin.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Schema(description = "管理后台 - 本地演示角色切换 Request VO")
@Data
public class AuthDemoRoleSwitchReqVO {

    @Schema(description = "目标产品角色", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "clm_business")
    @NotBlank(message = "目标产品角色不能为空")
    @Pattern(regexp = "clm_business|clm_legal|clm_contract_admin|clm_system_admin",
            message = "目标产品角色不支持")
    private String roleCode;

}
