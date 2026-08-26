package cn.iocoder.yudao.module.clm.controller.admin.contract.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - CLM 合同复制/续签 Request VO")
@Data
public class ContractCopyReqVO {

    @Schema(description = "来源合同编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "来源合同不能为空")
    private Long sourceContractId;

    @Schema(description = "关联类型：COPY 复制 / RENEWAL 续签", requiredMode = Schema.RequiredMode.REQUIRED, example = "COPY")
    @NotEmpty(message = "关联类型不能为空")
    private String relationType;

    @Schema(description = "新合同标题，缺省为 源标题+（复制/续签）", example = "2026 年度销售合同（续签）")
    @Size(max = 255, message = "合同标题长度不能超过 255")
    private String title;

}
