package cn.iocoder.yudao.module.clm.dal.dataobject.contract;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * CLM 合同 DO
 */
@TableName(value = "clm_contract", autoResultMap = true)
@KeySequence("clm_contract_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ContractDO extends TenantBaseDO {

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 合同编号（创建后生成，租户内唯一）
     */
    private String contractNo;
    /**
     * 合同标题
     */
    private String title;
    /**
     * 合同类型编号
     */
    private Long typeId;
    /**
     * 合同类型版本编号（固定引用，不随类型最新配置漂移）
     */
    private Long typeVersionId;
    /**
     * 负责人用户编号
     */
    private Long ownerUserId;
    /**
     * 负责人部门编号
     */
    private Long ownerDeptId;
    /**
     * 合同金额
     */
    private BigDecimal amount;
    /**
     * 币种
     */
    private String currency;
    /**
     * 签订日期
     */
    private LocalDate signDate;
    /**
     * 生效日期
     */
    private LocalDate effectiveDate;
    /**
     * 到期日期
     */
    private LocalDate expiryDate;
    /**
     * 扩展字段
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> customData;
    /**
     * 合同说明
     */
    private String description;
    /**
     * 生命周期状态
     *
     * 枚举 {@link cn.iocoder.yudao.module.clm.enums.contract.ClmLifecycleStatusEnum}
     */
    private Integer lifecycleStatus;
    /**
     * 审批状态
     *
     * 枚举 {@link cn.iocoder.yudao.module.clm.enums.contract.ClmApprovalStatusEnum}
     */
    private Integer approvalStatus;
    /**
     * 当前正文版本编号（clm_document_version.id）
     */
    private Long currentDocumentVersionId;
    /**
     * 最近一次流程绑定编号（clm_workflow_binding.id）
     */
    private Long currentBindingId;
    /**
     * 来源合同编号（复制/续签）
     */
    private Long sourceContractId;
    /**
     * 与来源合同的关系
     *
     * 枚举 {@link cn.iocoder.yudao.module.clm.enums.contract.ClmContractRelationTypeEnum}
     */
    private String relationType;

}
