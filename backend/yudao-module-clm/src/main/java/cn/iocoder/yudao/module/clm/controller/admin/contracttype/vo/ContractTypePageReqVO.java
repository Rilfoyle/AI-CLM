package cn.iocoder.yudao.module.clm.controller.admin.contracttype.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - CLM 合同类型分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ContractTypePageReqVO extends PageParam {

    @Schema(description = "类型编码", example = "SALES")
    private String code;

    @Schema(description = "类型名称", example = "销售")
    private String name;

    @Schema(description = "状态", example = "0")
    private Integer status;

}
