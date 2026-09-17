package cn.iocoder.yudao.module.clm.permission;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PermissionPolicySaveReqVO {
    private Long id;
    @NotBlank @Size(max = 20000) private String roleCapabilitiesJson;
    @NotBlank @Size(max = 20000) private String scopeLimitsJson;
    @NotBlank @Size(max = 20000) private String nodeEditPolicyJson;
    @Size(max = 500) private String remark;
}
