package cn.iocoder.yudao.module.clm.collaboration;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - CLM 法务协同分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class CollaborationPageReqVO extends PageParam {

    public static final String VIEW_TODO = "TODO";
    public static final String VIEW_STARTED = "STARTED";
    public static final String VIEW_COMPLETED = "COMPLETED";

    @Schema(description = "视图：TODO/STARTED/COMPLETED", example = "TODO")
    @Pattern(regexp = "TODO|STARTED|COMPLETED", message = "协同视图不合法")
    private String view = VIEW_TODO;
    private String status;
    private Long contractId;

}
