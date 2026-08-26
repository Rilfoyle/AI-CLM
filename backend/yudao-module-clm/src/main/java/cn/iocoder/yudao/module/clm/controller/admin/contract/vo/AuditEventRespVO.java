package cn.iocoder.yudao.module.clm.controller.admin.contract.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - CLM 审计事件 Response VO")
@Data
public class AuditEventRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "聚合类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "CONTRACT")
    private String aggregateType;

    @Schema(description = "聚合编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long aggregateId;

    @Schema(description = "关联合同编号", example = "1")
    private Long contractId;

    @Schema(description = "动作", requiredMode = Schema.RequiredMode.REQUIRED, example = "CONTRACT_CREATE")
    private String action;

    @Schema(description = "操作人用户编号", example = "1")
    private Long actorUserId;

    @Schema(description = "操作人名称", example = "芋道")
    private String actorName;

    @Schema(description = "明细 JSON")
    private String detailJson;

    @Schema(description = "发生时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime occurredAt;

}
