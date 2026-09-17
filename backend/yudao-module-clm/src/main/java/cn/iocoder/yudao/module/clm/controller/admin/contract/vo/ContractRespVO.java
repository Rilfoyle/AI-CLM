package cn.iocoder.yudao.module.clm.controller.admin.contract.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import cn.iocoder.yudao.module.clm.controller.admin.document.vo.DocumentVersionRespVO;
import cn.iocoder.yudao.module.clm.controller.admin.workflow.vo.WorkflowBindingRespVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Schema(description = "管理后台 - CLM 合同 Response VO")
@Data
public class ContractRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "合同编号", example = "HT20260101-00001")
    private String contractNo;

    @Schema(description = "合同标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026 年度销售合同")
    private String title;

    @Schema(description = "合同类型编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long typeId;

    @Schema(description = "合同类型编码", example = "SALES")
    private String typeCode;

    @Schema(description = "合同类型名称", example = "销售合同")
    private String typeName;

    @Schema(description = "合同类型版本编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long typeVersionId;

    @Schema(description = "合同类型版本号", example = "1")
    private Integer typeVersionNo;

    @Schema(description = "负责人用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long ownerUserId;

    @Schema(description = "负责人名称", example = "芋道")
    private String ownerUserName;

    @Schema(description = "负责人部门编号", example = "100")
    private Long ownerDeptId;

    @Schema(description = "负责人部门名称", example = "研发部")
    private String ownerDeptName;

    @Schema(description = "合同金额", example = "10000.00")
    private BigDecimal amount;

    @Schema(description = "币种", example = "CNY")
    private String currency;

    @Schema(description = "签订日期")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate signDate;

    @Schema(description = "生效日期")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate effectiveDate;

    @Schema(description = "到期日期")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate expiryDate;

    @Schema(description = "合同说明")
    private String description;

    @Schema(description = "扩展字段")
    private Map<String, Object> customData;

    @Schema(description = "生命周期状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer lifecycleStatus;

    @Schema(description = "审批状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer approvalStatus;

    @Schema(description = "当前正文版本编号", example = "1")
    private Long currentDocumentVersionId;

    @Schema(description = "最近一次流程绑定编号", example = "1")
    private Long currentBindingId;

    @Schema(description = "当前不可变修订编号", example = "1")
    private Long currentRevisionId;

    @Schema(description = "当前不可变修订序号", example = "1")
    private Integer currentRevisionNo;

    @Schema(description = "起草来源：TEMPLATE / UPLOAD / MANUAL / COPY", example = "UPLOAD")
    private String sourceMode;

    @Schema(description = "业务阶段：DRAFT / COLLABORATING / APPROVING / APPROVED", example = "DRAFT")
    private String stageCode;

    @Schema(description = "是否明确确认无重大承诺")
    private Boolean noCommitmentConfirmed;

    @Schema(description = "编号规则版本编号", example = "1")
    private Long numberingRuleVersionId;

    @Schema(description = "逻辑删除标记")
    private Boolean deleted;

    @Schema(description = "来源合同编号（复制/续签）", example = "1")
    private Long sourceContractId;

    @Schema(description = "来源合同标题", example = "2025 年度销售合同")
    private String sourceContractTitle;

    @Schema(description = "来源合同编号（业务编号）", example = "HT20250101-00001")
    private String sourceContractNo;

    @Schema(description = "与来源合同的关系：COPY 复制 / RENEWAL 续签", example = "COPY")
    private String relationType;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

    @Schema(description = "更新时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime updateTime;

    @Schema(description = "创建人编号")
    private String creator;

    @Schema(description = "签约方列表")
    private List<ContractPartyRespVO> parties;

    @Schema(description = "首个相对方名称", example = "星河智能科技有限公司")
    private String counterpartyName;

    @Schema(description = "相对方名称列表")
    private List<String> counterpartyNames;

    @Schema(description = "当前正文版本")
    private DocumentVersionRespVO currentDocumentVersion;

    @Schema(description = "最近一次流程绑定")
    private WorkflowBindingRespVO currentBinding;

    @Schema(description = "当前用户权限")
    private ContractPermissionsVO permissions;

}
