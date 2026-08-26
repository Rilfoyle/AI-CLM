package cn.iocoder.yudao.module.clm.service.audit;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.clm.controller.admin.contract.vo.AuditEventPageReqVO;
import cn.iocoder.yudao.module.clm.dal.dataobject.audit.AuditEventDO;
import cn.iocoder.yudao.module.clm.enums.audit.ClmAuditActionEnum;
import cn.iocoder.yudao.module.clm.enums.audit.ClmAuditAggregateTypeEnum;

import java.util.Map;

/**
 * CLM 审计事件 Service 接口
 */
public interface ClmAuditService {

    /**
     * 记录审计事件（同事务写入）
     *
     * 操作人取当前登录用户；无登录用户时 actorName = "SYSTEM"
     *
     * @param type        聚合类型
     * @param aggregateId 聚合编号
     * @param contractId  关联合同编号，可空
     * @param action      动作
     * @param detail      明细，可空
     * @return 事件编号
     */
    Long record(ClmAuditAggregateTypeEnum type, Long aggregateId, Long contractId,
                ClmAuditActionEnum action, Map<String, Object> detail);

    /**
     * 记录审计事件（指定操作人，用于 BPM 回调等系统动作）
     *
     * @param type        聚合类型
     * @param aggregateId 聚合编号
     * @param contractId  关联合同编号，可空
     * @param action      动作
     * @param detail      明细，可空
     * @param actorUserId 操作人用户编号，可空
     * @param actorName   操作人名称
     * @return 事件编号
     */
    Long record(ClmAuditAggregateTypeEnum type, Long aggregateId, Long contractId,
                ClmAuditActionEnum action, Map<String, Object> detail,
                Long actorUserId, String actorName);

    /**
     * 获得合同的审计事件分页（要求当前用户对合同可见）
     *
     * @param reqVO 分页查询
     * @return 分页结果
     */
    PageResult<AuditEventDO> getAuditEventPage(AuditEventPageReqVO reqVO);

}
