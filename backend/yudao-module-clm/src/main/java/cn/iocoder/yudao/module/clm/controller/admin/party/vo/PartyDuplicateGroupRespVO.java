package cn.iocoder.yudao.module.clm.controller.admin.party.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - CLM 签约方重复候选分组 Response VO")
@Data
public class PartyDuplicateGroupRespVO {

    @Schema(description = "匹配类型：UNIFIED_CREDIT_CODE / NORMALIZED_NAME", requiredMode = Schema.RequiredMode.REQUIRED)
    private String matchType;

    @Schema(description = "规范化后的匹配值", requiredMode = Schema.RequiredMode.REQUIRED)
    private String matchValue;

    @Schema(description = "建议保留的主体编号（默认为最早记录）", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long recommendedTargetPartyId;

    @Schema(description = "候选主体", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<PartyRespVO> parties;
}
