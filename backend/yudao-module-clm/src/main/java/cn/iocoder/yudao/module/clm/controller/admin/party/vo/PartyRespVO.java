package cn.iocoder.yudao.module.clm.controller.admin.party.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - CLM 签约方 Response VO")
@Data
public class PartyRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "主体类型：1 企业 2 个人", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer partyType;

    @Schema(description = "名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "芋道科技")
    private String name;

    @Schema(description = "统一社会信用代码 / 证件号")
    private String unifiedCreditCode;

    @Schema(description = "是否我方主体", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    private Boolean internalFlag;

    @Schema(description = "联系人")
    private String contactName;

    @Schema(description = "联系电话")
    private String contactPhone;

    @Schema(description = "地址")
    private String address;

    @Schema(description = "状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
