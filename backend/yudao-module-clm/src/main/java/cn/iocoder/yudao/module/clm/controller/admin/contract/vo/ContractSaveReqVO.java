package cn.iocoder.yudao.module.clm.controller.admin.contract.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Schema(description = "管理后台 - CLM 合同新增/修改 Request VO")
@Data
public class ContractSaveReqVO {

    @Schema(description = "编号", example = "1")
    private Long id;

    @Schema(description = "合同标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026 年度销售合同")
    @NotEmpty(message = "合同标题不能为空")
    @Size(max = 255, message = "合同标题长度不能超过 255")
    private String title;

    @Schema(description = "合同类型编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "合同类型不能为空")
    private Long typeId;

    @Schema(description = "合同金额", example = "10000.00")
    private BigDecimal amount;

    @Schema(description = "币种，默认 CNY", example = "CNY")
    private String currency;

    @Schema(description = "签订日期")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate signDate;

    @Schema(description = "生效日期")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate effectiveDate;

    @Schema(description = "到期日期")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate expiryDate;

    @Schema(description = "负责人用户编号，空则当前用户", example = "1")
    private Long ownerUserId;

    @Schema(description = "负责人部门编号，空则当前用户部门", example = "100")
    private Long ownerDeptId;

    @Schema(description = "合同说明")
    private String description;

    @Schema(description = "扩展字段")
    private Map<String, Object> customData;

    @Schema(description = "签约方列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "签约方不能为空")
    @Valid
    private List<ContractPartyItemVO> parties;

    @Schema(description = "是否用类型范本生成正文 v1（仅创建时生效，且类型配置了范本）", example = "true")
    private Boolean useTypeTemplate;

}
