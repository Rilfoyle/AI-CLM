package cn.iocoder.yudao.module.clm.permission;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class UserScopeSaveReqVO {
    @NotNull private Long userId;
    @Pattern(regexp = "clm_business|clm_legal|clm_contract_admin|clm_system_admin")
    private String roleCode;
    @NotNull private Long policyVersionId;
    private List<Long> orgIds = new ArrayList<>();
    private List<Long> contractTypeIds = new ArrayList<>();
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    @Pattern(regexp = "ACTIVE|INACTIVE") private String status = "ACTIVE";
}
