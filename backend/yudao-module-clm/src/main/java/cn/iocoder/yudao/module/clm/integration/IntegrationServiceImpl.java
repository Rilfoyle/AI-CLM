package cn.iocoder.yudao.module.clm.integration;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.clm.service.audit.ClmAuditService;
import cn.iocoder.yudao.module.clm.enums.audit.ClmAuditActionEnum;
import cn.iocoder.yudao.module.clm.enums.audit.ClmAuditAggregateTypeEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.clm.enums.ErrorCodeConstants.INTEGRATION_DELIVERY_NOT_EXISTS;

@Service
public class IntegrationServiceImpl implements IntegrationService {

    @Resource private IntegrationRunMapper runMapper;
    @Resource private IntegrationDeliveryMapper deliveryMapper;
    @Resource private ClmAuditService auditService;

    @Override
    public IntegrationStatusRespVO getStatus() {
        return new IntegrationStatusRespVO(false, "SANDBOX",
                "未配置真实钉钉凭据；当前只记录沙箱差异、身份检查和消息投递，不调用外部服务。");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long runSandbox(IntegrationRunReqVO reqVO) {
        IntegrationRunDO existing = runMapper.selectByTypeAndKey(reqVO.getIntegrationType(), reqVO.getRunKey());
        if (existing != null) return existing.getId();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("sandbox", true);
        summary.put("configured", false);
        summary.put("externalRequestSent", false);
        summary.put("add", 0);
        summary.put("update", 0);
        summary.put("disable", 0);
        summary.put("conflict", 0);
        IntegrationRunDO run = new IntegrationRunDO().setIntegrationType(reqVO.getIntegrationType())
                .setRunKey(reqVO.getRunKey()).setMode("SANDBOX").setStatus("NOT_CONFIGURED")
                .setSummaryJson(JsonUtils.toJsonString(summary))
                .setErrorMessage("真实钉钉凭据未配置").setFinishedTime(LocalDateTime.now());
        runMapper.insert(run);
        auditService.record(ClmAuditAggregateTypeEnum.INTEGRATION, run.getId(), null,
                ClmAuditActionEnum.INTEGRATION_SANDBOX_RUN,
                Map.of("integrationType", reqVO.getIntegrationType(), "mode", "SANDBOX",
                        "configured", false, "externalRequestSent", false));
        return run.getId();
    }

    @Override
    public PageResult<IntegrationRunDO> getRunPage(PageParam pageParam, String type) {
        return runMapper.selectPage(pageParam, type);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long enqueue(String deliveryKey, String messageType, Long recipientUserId,
                        Long contractId, String taskId, String deepLinkPath) {
        IntegrationDeliveryDO existing = deliveryMapper.selectByKey(deliveryKey);
        if (existing != null) return existing.getId();
        IntegrationDeliveryDO delivery = new IntegrationDeliveryDO().setDeliveryKey(deliveryKey)
                .setChannel("DINGTALK").setMessageType(messageType).setRecipientUserId(recipientUserId)
                .setContractId(contractId).setTaskId(taskId).setDeepLinkPath(deepLinkPath)
                .setStatus("NOT_CONFIGURED").setAttemptCount(0)
                .setLastError("真实钉钉凭据未配置，未发送外部消息");
        deliveryMapper.insert(delivery);
        return delivery.getId();
    }

    @Override
    public PageResult<IntegrationDeliveryDO> getDeliveryPage(PageParam pageParam, String status) {
        return deliveryMapper.selectPage(pageParam, status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelByTaskId(String taskId, String reason) {
        if (taskId == null) return;
        List<IntegrationDeliveryDO> deliveries = deliveryMapper.selectList(
                IntegrationDeliveryDO::getTaskId, taskId);
        for (IntegrationDeliveryDO delivery : deliveries) {
            if ("CANCELED".equals(delivery.getStatus())) continue;
            deliveryMapper.updateById(new IntegrationDeliveryDO().setId(delivery.getId())
                    .setStatus("CANCELED").setNextRetryTime(null)
                    .setLastError(reason == null ? "审批任务已失效" : reason));
        }
    }

    @Override
    public void retryDelivery(Long id) {
        IntegrationDeliveryDO delivery = deliveryMapper.selectById(id);
        if (delivery == null) throw exception(INTEGRATION_DELIVERY_NOT_EXISTS);
        deliveryMapper.updateById(new IntegrationDeliveryDO().setId(id)
                .setStatus("NOT_CONFIGURED").setAttemptCount(delivery.getAttemptCount() + 1)
                .setNextRetryTime(null).setLastError("真实钉钉凭据未配置，未发送外部消息"));
    }
}
