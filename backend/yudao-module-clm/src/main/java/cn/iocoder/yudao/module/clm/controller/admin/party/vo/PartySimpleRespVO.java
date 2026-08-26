package cn.iocoder.yudao.module.clm.controller.admin.party.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - CLM 签约方精简 Response VO")
@Data
public class PartySimpleRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "芋道科技")
    private String name;

    @Schema(description = "主体类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer partyType;

    @Schema(description = "是否我方主体", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    private Boolean internalFlag;

    @Schema(description = "统一社会信用代码 / 证件号")
    private String unifiedCreditCode;

}
