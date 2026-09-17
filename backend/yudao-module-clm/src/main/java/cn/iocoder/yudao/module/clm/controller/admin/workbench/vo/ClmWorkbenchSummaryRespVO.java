package cn.iocoder.yudao.module.clm.controller.admin.workbench.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - CLM 角色工作台汇总 Response VO")
@Data
public class ClmWorkbenchSummaryRespVO {

    @Schema(description = "当前用户可继续处理的草稿数")
    private Long draftCount;

    @Schema(description = "当前用户待处理的法务协同数")
    private Long collaborationTodoCount;

    @Schema(description = "当前用户待处理的合同审批任务数")
    private Long approvalTodoCount;

    @Schema(description = "当前用户发起且仍在审批的合同数")
    private Long startedRunningCount;

    @Schema(description = "当前用户有权处理的治理异常数")
    private Long governanceIssueCount;

}
