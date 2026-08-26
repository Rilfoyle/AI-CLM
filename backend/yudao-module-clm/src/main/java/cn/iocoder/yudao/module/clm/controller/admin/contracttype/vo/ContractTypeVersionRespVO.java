package cn.iocoder.yudao.module.clm.controller.admin.contracttype.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - CLM 合同类型版本 Response VO")
@Data
public class ContractTypeVersionRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    private Long id;

    @Schema(description = "合同类型编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long typeId;

    @Schema(description = "版本号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer versionNo;

    @Schema(description = "状态：0 草稿 1 已发布", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer status;

    @Schema(description = "FormCreate 表单配置 JSON")
    private String formConf;

    @Schema(description = "FormCreate 字段规则数组（每项为 rule JSON 串）")
    private List<String> formFields;

    @Schema(description = "审批流程定义 KEY", requiredMode = Schema.RequiredMode.REQUIRED, example = "clm_contract_approval_v1")
    private String processDefinitionKey;

    @Schema(description = "版本说明")
    private String remark;

    @Schema(description = "发布时间")
    private LocalDateTime publishedTime;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
