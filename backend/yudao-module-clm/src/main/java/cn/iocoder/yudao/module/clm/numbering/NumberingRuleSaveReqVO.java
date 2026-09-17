package cn.iocoder.yudao.module.clm.numbering;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class NumberingRuleSaveReqVO {
    private Long id;
    @NotBlank(message = "编码规则编码不能为空")
    @Size(max = 64, message = "编码规则编码长度不能超过 64")
    private String ruleCode;
    private Long contractTypeId;
    @Size(max = 32, message = "编号前缀长度不能超过 32")
    private String prefix;
    @NotNull(message = "日期格式不能为空")
    @Size(max = 32, message = "日期格式长度不能超过 32")
    private String datePattern;
    @Size(max = 8, message = "分隔符长度不能超过 8")
    private String separator;
    @NotNull(message = "序列长度不能为空")
    @Min(value = 1, message = "序列长度不能小于 1")
    @Max(value = 12, message = "序列长度不能大于 12")
    private Integer sequenceLength;
    @NotBlank(message = "重置周期不能为空")
    private String resetPeriod;
    @NotNull(message = "是否包含我方主体简称不能为空")
    private Boolean includePartyShortName;
    /** 仅样例预览使用，不写入规则版本。 */
    private String ourPartyShortName;
}
