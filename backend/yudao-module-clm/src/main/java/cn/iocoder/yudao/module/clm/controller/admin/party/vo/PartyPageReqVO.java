package cn.iocoder.yudao.module.clm.controller.admin.party.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - CLM 签约方分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class PartyPageReqVO extends PageParam {

    @Schema(description = "名称", example = "芋道")
    private String name;

    @Schema(description = "主体类型", example = "1")
    private Integer partyType;

    @Schema(description = "是否我方主体", example = "true")
    private Boolean internalFlag;

    @Schema(description = "状态", example = "0")
    private Integer status;

}
