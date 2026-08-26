package cn.iocoder.yudao.module.clm.controller.admin.contract.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - CLM 合同参与人保存 Request VO")
@Data
public class ContractParticipantSaveReqVO {

    @Schema(description = "合同编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "合同编号不能为空")
    private Long contractId;

    @Schema(description = "参与人列表（不含 OWNER）")
    @Valid
    private List<Item> participants;

    @Schema(description = "管理后台 - CLM 合同参与人项")
    @Data
    public static class Item {

        @Schema(description = "主体类型，默认 USER", example = "USER")
        private String principalType;

        @Schema(description = "主体编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
        @NotNull(message = "主体编号不能为空")
        private Long principalId;

        @Schema(description = "角色：COLLABORATOR / VIEWER", requiredMode = Schema.RequiredMode.REQUIRED, example = "COLLABORATOR")
        @NotEmpty(message = "参与人角色不能为空")
        private String roleCode;

        @Schema(description = "可查看", example = "true")
        private Boolean canView;

        @Schema(description = "可编辑", example = "false")
        private Boolean canEdit;

        @Schema(description = "可下载", example = "false")
        private Boolean canDownload;

        @Schema(description = "可管理参与人", example = "false")
        private Boolean canManage;

    }

}
