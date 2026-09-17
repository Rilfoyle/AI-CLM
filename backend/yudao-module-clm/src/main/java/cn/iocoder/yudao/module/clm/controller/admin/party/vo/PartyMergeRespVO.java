package cn.iocoder.yudao.module.clm.controller.admin.party.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - CLM 签约方合并 Response VO")
@Data
public class PartyMergeRespVO {

    private Long sourcePartyId;
    private Long targetPartyId;
    @Schema(description = "已切换活引用的草稿/协作中合同数")
    private Integer rewrittenContractCount;
    @Schema(description = "已改指向目标主体的关联行数")
    private Integer rewrittenLinkCount;
    @Schema(description = "因合同已引用目标主体而去重的关联行数")
    private Integer deduplicatedLinkCount;
    @Schema(description = "受审批或其他非可编辑状态保护、未改写的合同数")
    private Integer protectedContractCount;
    @Schema(description = "是否为对已完成合并的重复请求")
    private Boolean idempotent;
}
