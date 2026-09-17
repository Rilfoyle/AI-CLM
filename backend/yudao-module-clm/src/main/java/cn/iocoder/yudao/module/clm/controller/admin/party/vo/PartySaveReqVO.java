package cn.iocoder.yudao.module.clm.controller.admin.party.vo;

import cn.iocoder.yudao.framework.common.validation.InEnum;
import cn.iocoder.yudao.module.clm.enums.party.ClmPartyTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - CLM 签约方新增/修改 Request VO")
@Data
public class PartySaveReqVO {

    @Schema(description = "编号", example = "1")
    private Long id;

    @Schema(description = "主体类型：1 企业 2 个人", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "主体类型不能为空")
    @InEnum(value = ClmPartyTypeEnum.class, message = "主体类型必须是 {value}")
    private Integer partyType;

    @Schema(description = "名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "芋道科技")
    @NotEmpty(message = "名称不能为空")
    @Size(max = 255, message = "名称长度不能超过 255")
    private String name;

    @Schema(description = "我方主体简称，供合同编号使用", example = "灵犀科技")
    @Size(max = 64, message = "主体简称长度不能超过 64")
    private String shortName;

    @Schema(description = "统一社会信用代码 / 证件号", example = "91330100MA2XXXXXXX")
    private String unifiedCreditCode;

    @Schema(description = "是否我方主体", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    @NotNull(message = "是否我方主体不能为空")
    private Boolean internalFlag;

    @Schema(description = "联系人", example = "张三")
    private String contactName;

    @Schema(description = "联系电话", example = "13800000000")
    private String contactPhone;

    @Schema(description = "地址")
    private String address;

    @Schema(description = "状态：0 开启 1 关闭，默认 0", example = "0")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

}
