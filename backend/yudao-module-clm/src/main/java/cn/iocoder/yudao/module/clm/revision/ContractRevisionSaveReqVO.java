package cn.iocoder.yudao.module.clm.revision;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Schema(description = "管理后台 - 保存合同不可变修订 Request VO")
@Data
public class ContractRevisionSaveReqVO {

    @NotNull(message = "合同编号不能为空")
    private Long contractId;
    @NotNull(message = "基线修订编号不能为空")
    private Long baseRevisionId;
    @NotBlank(message = "合同名称不能为空")
    private String name;
    private BigDecimal amount;
    private String currency;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;
    @NotNull(message = "我方主体不能为空")
    private Long ourPartyId;
    @NotEmpty(message = "相对方不能为空")
    private List<Long> counterpartyIds;
    private Map<String, Object> customData;
    private String description;
    private String changeReason;
}
