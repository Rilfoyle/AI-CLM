package cn.iocoder.yudao.module.clm.controller.admin.workflow.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - CLM 流程绑定 Response VO")
@Data
public class WorkflowBindingRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "合同编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long contractId;

    @Schema(description = "用途", requiredMode = Schema.RequiredMode.REQUIRED, example = "APPROVAL")
    private String purpose;

    @Schema(description = "流程定义 KEY", requiredMode = Schema.RequiredMode.REQUIRED, example = "clm_contract_approval_v1")
    private String processDefinitionKey;

    @Schema(description = "流程定义编号")
    private String processDefinitionId;

    @Schema(description = "流程实例编号")
    private String processInstanceId;

    @Schema(description = "绑定的正文版本编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long documentVersionId;

    @Schema(description = "绑定的正文版本号", example = "1")
    private Integer documentVersionNo;

    @Schema(description = "绑定的合同类型版本编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long contractTypeVersionId;

    @Schema(description = "绑定版本的 SHA-256", requiredMode = Schema.RequiredMode.REQUIRED)
    private String checksumSha256;

    @Schema(description = "状态：0 准备中 1 审批中 2 通过 3 驳回 4 取消", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer status;

    @Schema(description = "审批结果说明")
    private String resultReason;

    @Schema(description = "提交人编号")
    private String creator;

    @Schema(description = "提交人名称")
    private String creatorName;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

    @Schema(description = "结束时间")
    private LocalDateTime finishedTime;

}
