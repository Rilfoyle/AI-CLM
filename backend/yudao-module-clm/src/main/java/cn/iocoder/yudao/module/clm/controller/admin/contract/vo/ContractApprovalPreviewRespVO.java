package cn.iocoder.yudao.module.clm.controller.admin.contract.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - CLM 合同审批预览 Response VO")
@Data
public class ContractApprovalPreviewRespVO {

    @Schema(description = "流程定义 KEY", requiredMode = Schema.RequiredMode.REQUIRED, example = "clm_contract_approval_v1")
    private String processDefinitionKey;

    @Schema(description = "激活的流程定义编号；未部署时为空")
    private String processDefinitionId;

    @Schema(description = "流程定义名称")
    private String processDefinitionName;

}
