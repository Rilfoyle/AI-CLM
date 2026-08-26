package cn.iocoder.yudao.module.clm.controller.admin.contracttype.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - CLM 合同类型版本修改 Request VO")
@Data
public class ContractTypeVersionSaveReqVO {

    @Schema(description = "版本编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    @NotNull(message = "版本编号不能为空")
    private Long id;

    @Schema(description = "FormCreate 表单配置 JSON")
    private String formConf;

    @Schema(description = "FormCreate 字段规则数组（每项为 rule JSON 串）")
    private List<String> formFields;

    @Schema(description = "审批流程定义 KEY", requiredMode = Schema.RequiredMode.REQUIRED, example = "clm_contract_approval_v1")
    @NotEmpty(message = "审批流程定义 KEY 不能为空")
    private String processDefinitionKey;

    @Schema(description = "版本说明")
    private String remark;

}
