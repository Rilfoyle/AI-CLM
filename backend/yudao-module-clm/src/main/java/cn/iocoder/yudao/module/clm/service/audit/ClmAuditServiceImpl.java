package cn.iocoder.yudao.module.clm.service.audit;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.clm.access.ContractAccessService;
import cn.iocoder.yudao.module.clm.controller.admin.contract.vo.AuditEventPageReqVO;
import cn.iocoder.yudao.module.clm.dal.dataobject.audit.AuditEventDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.clm.dal.mysql.audit.AuditEventMapper;
import cn.iocoder.yudao.module.clm.dal.mysql.contract.ContractMapper;
import cn.iocoder.yudao.module.clm.enums.audit.ClmAuditActionEnum;
import cn.iocoder.yudao.module.clm.enums.audit.ClmAuditAggregateTypeEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.clm.enums.ErrorCodeConstants.CONTRACT_NOT_EXISTS;

/**
 * CLM 审计事件 Service 实现类
 */
@Service
@Validated
public class ClmAuditServiceImpl implements ClmAuditService {

    public static final String ACTOR_SYSTEM = "SYSTEM";

    @Resource
    private AuditEventMapper auditEventMapper;
    @Resource
    private ContractMapper contractMapper;

    @Resource
    private ContractAccessService contractAccessService;

    @Override
    public Long record(ClmAuditAggregateTypeEnum type, Long aggregateId, Long contractId,
                       ClmAuditActionEnum action, Map<String, Object> detail) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        String nickname = SecurityFrameworkUtils.getLoginUserNickname();
        return record(type, aggregateId, contractId, action, detail, userId,
                StrUtil.isNotEmpty(nickname) ? nickname : ACTOR_SYSTEM);
    }

    @Override
    public Long record(ClmAuditAggregateTypeEnum type, Long aggregateId, Long contractId,
                       ClmAuditActionEnum action, Map<String, Object> detail,
                       Long actorUserId, String actorName) {
        AuditEventDO event = new AuditEventDO()
                .setAggregateType(type.getCode())
                .setAggregateId(aggregateId)
                .setContractId(contractId)
                .setAction(action.getCode())
                .setActorUserId(actorUserId)
                .setActorName(StrUtil.blankToDefault(actorName, ACTOR_SYSTEM))
                .setDetailJson(detail == null ? null : JsonUtils.toJsonString(detail))
                .setOccurredAt(LocalDateTime.now());
        auditEventMapper.insert(event);
        return event.getId();
    }

    @Override
    public PageResult<AuditEventDO> getAuditEventPage(AuditEventPageReqVO reqVO) {
        ContractDO contract = contractMapper.selectById(reqVO.getContractId());
        if (contract == null) {
            throw exception(CONTRACT_NOT_EXISTS);
        }
        contractAccessService.assertCanView(contract, SecurityFrameworkUtils.getLoginUserId());
        return auditEventMapper.selectPage(reqVO);
    }

}
