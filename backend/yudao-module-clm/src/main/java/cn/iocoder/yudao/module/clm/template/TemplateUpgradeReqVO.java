package cn.iocoder.yudao.module.clm.template;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TemplateUpgradeReqVO {
    @NotNull(message = "合同编号不能为空")
    private Long contractId;
    @NotNull(message = "基线修订不能为空")
    private Long baseRevisionId;
    @NotNull(message = "目标范本版本不能为空")
    private Long targetTemplateVersionId;
}
