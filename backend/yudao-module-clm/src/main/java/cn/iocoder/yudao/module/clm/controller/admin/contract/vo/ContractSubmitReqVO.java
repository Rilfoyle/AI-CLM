package cn.iocoder.yudao.module.clm.controller.admin.contract.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Schema(description = "管理后台 - CLM 合同提交审批 Request VO")
@Data
public class ContractSubmitReqVO {

    @Schema(description = "合同编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "合同编号不能为空")
    private Long id;

    @Schema(description = "提交幂等键", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "提交幂等键不能为空")
    private String submitRequestId;

    @Schema(description = "提交的基线修订编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "基线修订编号不能为空")
    private Long baseRevisionId;

    @Schema(description = "发起人自选审批人：key 为任务 Key，value 为用户编号数组")
    private Map<String, List<Long>> startUserSelectAssignees;

    @Schema(description = "提交说明")
    private String remark;

}
