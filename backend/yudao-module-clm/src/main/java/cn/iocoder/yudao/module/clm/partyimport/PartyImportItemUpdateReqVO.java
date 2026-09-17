package cn.iocoder.yudao.module.clm.partyimport;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PartyImportItemUpdateReqVO {
    @NotNull(message = "导入行编号不能为空")
    private Long id;
    @NotBlank(message = "相对方名称不能为空")
    @Size(max = 255, message = "相对方名称长度不能超过 255")
    private String name;
    @NotBlank(message = "统一社会信用代码不能为空")
    @Size(max = 64, message = "统一社会信用代码长度不能超过 64")
    private String unifiedCreditCode;
    @Size(max = 64, message = "联系人长度不能超过 64")
    private String contactName;
    @Size(max = 32, message = "联系电话长度不能超过 32")
    private String contactPhone;
}
