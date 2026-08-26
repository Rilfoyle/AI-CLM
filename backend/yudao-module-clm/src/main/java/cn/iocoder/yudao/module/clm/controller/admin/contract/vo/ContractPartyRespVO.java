package cn.iocoder.yudao.module.clm.controller.admin.contract.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

@Schema(description = "管理后台 - CLM 合同签约方 Response VO")
@Data
public class ContractPartyRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "签约方编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long partyId;

    @Schema(description = "签约方名称（来自快照）", example = "芋道科技")
    private String partyName;

    @Schema(description = "角色", requiredMode = Schema.RequiredMode.REQUIRED, example = "OUR_SIDE")
    private String roleCode;

    @Schema(description = "排序", example = "0")
    private Integer sort;

    @Schema(description = "签约方快照")
    private Map<String, Object> partySnapshot;

}
