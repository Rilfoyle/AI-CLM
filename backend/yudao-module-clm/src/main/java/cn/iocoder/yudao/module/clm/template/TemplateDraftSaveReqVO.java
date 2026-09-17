package cn.iocoder.yudao.module.clm.template;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class TemplateDraftSaveReqVO {
    private Long templateId;
    private Long versionId;
    @NotBlank(message = "范本编码不能为空")
    @Size(max = 64, message = "范本编码长度不能超过 64")
    private String code;
    @NotBlank(message = "范本名称不能为空")
    @Size(max = 128, message = "范本名称长度不能超过 128")
    private String name;
    @NotNull(message = "适用合同类型不能为空")
    private Long contractTypeId;
    @Size(max = 512, message = "范本描述长度不能超过 512")
    private String description;
    @Size(max = 255, message = "版本说明长度不能超过 255")
    private String remark;
    private MultipartFile file;
}
