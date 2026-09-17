package cn.iocoder.yudao.module.clm.integration;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;

public interface IntegrationService {
    IntegrationStatusRespVO getStatus();
    Long runSandbox(IntegrationRunReqVO reqVO);
    PageResult<IntegrationRunDO> getRunPage(PageParam pageParam, String type);
    Long enqueue(String deliveryKey, String messageType, Long recipientUserId,
                 Long contractId, String taskId, String deepLinkPath);
    /** 任务失效、转交或完成时幂等取消其未发送待办。 */
    void cancelByTaskId(String taskId, String reason);
    PageResult<IntegrationDeliveryDO> getDeliveryPage(PageParam pageParam, String status);
    void retryDelivery(Long id);
}
