package cn.iocoder.yudao.module.clm.dal.dataobject.audit;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * CLM 领域审计事件 DO（追加式，不覆盖）
 */
@TableName("clm_audit_event")
@KeySequence("clm_audit_event_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AuditEventDO extends TenantBaseDO {

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 聚合类型：CONTRACT / DOCUMENT / CONTRACT_TYPE
     *
     * 枚举 {@link cn.iocoder.yudao.module.clm.enums.audit.ClmAuditAggregateTypeEnum}
     */
    private String aggregateType;
    /**
     * 聚合编号
     */
    private Long aggregateId;
    /**
     * 关联合同编号（便于按合同查询）
     */
    private Long contractId;
    /**
     * 动作
     *
     * 枚举 {@link cn.iocoder.yudao.module.clm.enums.audit.ClmAuditActionEnum}
     */
    private String action;
    /**
     * 操作人用户编号（系统动作为空）
     */
    private Long actorUserId;
    /**
     * 操作人昵称
     */
    private String actorName;
    /**
     * 明细 JSON
     */
    private String detailJson;
    /**
     * 发生时间
     */
    private LocalDateTime occurredAt;

}
