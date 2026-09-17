package cn.iocoder.yudao.module.clm.controller.admin.party.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - CLM 签约方合并 Request VO")
@Data
public class PartyMergeReqVO {

    @Schema(description = "将被停用的源主体编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    @NotNull(message = "源主体编号不能为空")
    private Long sourcePartyId;

    @Schema(description = "合并后保留的目标主体编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "目标主体编号不能为空")
    private Long targetPartyId;
}
