package cn.iocoder.yudao.module.clm.controller.admin.contract.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - CLM 合同提交审批 Response VO")
@Data
public class ContractSubmitRespVO {

    @Schema(description = "审批业务单编号", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long approvalCaseId;

    @Schema(description = "流程实例编号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String processInstanceId;

    @Schema(description = "首次提交分配的永久合同编号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String contractNo;

    @Schema(description = "本次提交冻结的修订编号", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long submittedRevisionId;
}
