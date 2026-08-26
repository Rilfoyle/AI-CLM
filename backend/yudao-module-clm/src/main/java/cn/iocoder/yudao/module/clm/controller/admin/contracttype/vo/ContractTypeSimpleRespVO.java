package cn.iocoder.yudao.module.clm.controller.admin.contracttype.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - CLM 合同类型精简 Response VO")
@Data
public class ContractTypeSimpleRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "类型编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "SALES")
    private String code;

    @Schema(description = "类型名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "销售合同")
    private String name;

    @Schema(description = "当前发布版本编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    private Long currentVersionId;

    @Schema(description = "描述", example = "适用于对外销售业务")
    private String description;

    @Schema(description = "是否配置了范本文件", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    private Boolean hasTemplate;

}
