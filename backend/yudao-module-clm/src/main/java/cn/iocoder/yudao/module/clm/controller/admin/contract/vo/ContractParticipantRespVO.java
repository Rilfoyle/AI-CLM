package cn.iocoder.yudao.module.clm.controller.admin.contract.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - CLM 合同参与人 Response VO")
@Data
public class ContractParticipantRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "合同编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long contractId;

    @Schema(description = "主体类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "USER")
    private String principalType;

    @Schema(description = "主体编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long principalId;

    @Schema(description = "主体名称", example = "芋道")
    private String principalName;

    @Schema(description = "角色：OWNER / COLLABORATOR / VIEWER", requiredMode = Schema.RequiredMode.REQUIRED, example = "COLLABORATOR")
    private String roleCode;

    @Schema(description = "可查看", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean canView;

    @Schema(description = "可编辑", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean canEdit;

    @Schema(description = "可下载", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean canDownload;

    @Schema(description = "可管理参与人", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean canManage;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
