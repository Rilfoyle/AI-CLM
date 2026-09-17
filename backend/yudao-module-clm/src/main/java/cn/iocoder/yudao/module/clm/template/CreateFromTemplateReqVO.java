package cn.iocoder.yudao.module.clm.template;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateFromTemplateReqVO {
    @NotNull(message = "范本版本不能为空")
    private Long templateVersionId;
    @NotBlank(message = "合同名称不能为空")
    private String name;
    private Long ownerUserId;
}
