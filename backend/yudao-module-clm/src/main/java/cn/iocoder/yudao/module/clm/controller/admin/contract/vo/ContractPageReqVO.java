package cn.iocoder.yudao.module.clm.controller.admin.contract.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - CLM 合同分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ContractPageReqVO extends PageParam {

    @Schema(description = "合同标题", example = "销售")
    private String title;

    @Schema(description = "合同编号", example = "HT20260101-00001")
    private String contractNo;

    @Schema(description = "合同类型编号", example = "1")
    private Long typeId;

    @Schema(description = "合同类型编号（新接口别名）", example = "1")
    private Long contractTypeId;

    @Schema(description = "业务阶段", example = "DRAFT")
    private String stageCode;

    @Schema(description = "仅查询回收站")
    private Boolean deletedOnly;

    @Schema(description = "归属组织编号", example = "100")
    private Long orgId;

    @Schema(description = "相对方名称")
    private String counterpartyName;

    @Schema(description = "台账范围：HANDLED/PARTICIPATED/APPROVED_BY_ME/AUTHORIZED_ORG")
    @Pattern(regexp = "HANDLED|PARTICIPATED|APPROVED_BY_ME|AUTHORIZED_ORG", message = "台账查询范围不正确")
    private String scope;

    @Schema(description = "审批状态", example = "0")
    private Integer approvalStatus;

    @Schema(description = "生命周期状态", example = "1")
    private Integer lifecycleStatus;

    @Schema(description = "负责人用户编号", example = "1")
    private Long ownerUserId;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
