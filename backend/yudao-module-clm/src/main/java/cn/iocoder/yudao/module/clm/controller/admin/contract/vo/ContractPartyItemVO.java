package cn.iocoder.yudao.module.clm.controller.admin.contract.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - CLM 合同签约方项 VO")
@Data
public class ContractPartyItemVO {

    @Schema(description = "签约方编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "签约方编号不能为空")
    private Long partyId;

    @Schema(description = "角色：OUR_SIDE 我方 / COUNTERPARTY 相对方 / OTHER 其他", requiredMode = Schema.RequiredMode.REQUIRED, example = "OUR_SIDE")
    @NotEmpty(message = "签约方角色不能为空")
    private String roleCode;

    @Schema(description = "排序", example = "0")
    private Integer sort;

}
