package cn.iocoder.yudao.module.clm.savedfilter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SavedFilterSaveReqVO {
    private Long id;
    @NotBlank @Pattern(regexp = "CONTRACT_LEDGER|APPROVAL_INBOX|COLLABORATION_CENTER")
    private String sceneCode;
    @NotBlank @Size(max = 128) private String name;
    @NotBlank @Size(max = 8000) private String filterJson;
    private Boolean defaultFlag;
}
