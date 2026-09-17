package cn.iocoder.yudao.module.clm.controller.admin.workbench.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - CLM 统一工作项分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ClmWorkbenchItemsReqVO extends PageParam {

    @NotBlank(message = "工作项类型不能为空")
    @Pattern(regexp = "DRAFT|COLLABORATION|APPROVAL|APPROVAL_DONE|STARTED|COPIED|GOVERNANCE", message = "工作项类型不合法")
    @Schema(description = "工作项类型", example = "APPROVAL")
    private String type;

}
