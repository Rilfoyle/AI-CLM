package cn.iocoder.yudao.module.clm.controller.admin.contracttype.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - CLM 合同类型新增/修改 Request VO")
@Data
public class ContractTypeSaveReqVO {

    @Schema(description = "编号", example = "1")
    private Long id;

    @Schema(description = "类型编码（租户内唯一，创建后不可改）", requiredMode = Schema.RequiredMode.REQUIRED, example = "SALES")
    @Size(max = 64, message = "类型编码长度不能超过 64")
    private String code;

    @Schema(description = "类型名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "销售合同")
    @NotEmpty(message = "类型名称不能为空")
    @Size(max = 128, message = "类型名称长度不能超过 128")
    private String name;

    @Schema(description = "描述", example = "销售类合同")
    private String description;

    @Schema(description = "状态：0 开启 1 关闭，默认 0", example = "0")
    private Integer status;

    @Schema(description = "排序", example = "0")
    private Integer sort;

    @Schema(description = "首个版本的审批流程定义 KEY，默认 clm_contract_approval_v1", example = "clm_contract_approval_v1")
    private String processDefinitionKey;

}
