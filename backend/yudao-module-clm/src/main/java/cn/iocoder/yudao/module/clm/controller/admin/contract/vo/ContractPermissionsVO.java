package cn.iocoder.yudao.module.clm.controller.admin.contract.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - CLM 合同当前用户权限 VO")
@Data
public class ContractPermissionsVO {

    @Schema(description = "可查看", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean canView;

    @Schema(description = "可编辑", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean canEdit;

    @Schema(description = "可下载", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean canDownload;

    @Schema(description = "可管理参与人", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean canManage;

    @Schema(description = "可提交审批", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean canSubmit;

    @Schema(description = "可删除", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean canDelete;

    @Schema(description = "可撤销审批", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean canCancelApproval;

    @Schema(description = "可归档定稿", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean canArchive;

    @Schema(description = "可复制/续签", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean canCopy;

}
