package cn.iocoder.yudao.module.clm.handover;

import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.workflow.WorkflowBindingDO;
import cn.iocoder.yudao.module.clm.dal.mysql.contract.ContractMapper;
import cn.iocoder.yudao.module.clm.dal.mysql.workflow.WorkflowBindingMapper;
import cn.iocoder.yudao.module.clm.enums.audit.ClmAuditActionEnum;
import cn.iocoder.yudao.module.clm.enums.audit.ClmAuditAggregateTypeEnum;
import cn.iocoder.yudao.module.clm.enums.workflow.ClmWorkflowBindingStatusEnum;
import cn.iocoder.yudao.module.clm.service.audit.ClmAuditService;
import cn.iocoder.yudao.module.clm.service.contract.ContractParticipantService;
import jakarta.annotation.Resource;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.clm.handover.HandoverErrors.ITEM_NOT_EXISTS;
import static cn.iocoder.yudao.module.clm.handover.HandoverErrors.ITEM_TYPE_INVALID;

@Service
public class HandoverTransferExecutor {

    @Resource private HandoverItemMapper itemMapper;
    @Resource private ContractMapper contractMapper;
    @Resource private ContractParticipantService participantService;
    @Resource private WorkflowBindingMapper workflowBindingMapper;
    @Resource private TaskService taskService;
    @Resource private ClmAuditService auditService;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public HandoverItemDO transfer(Long itemId, Long targetUserId, Long administratorUserId, String reason) {
        HandoverItemDO item = itemMapper.selectByIdForUpdate(itemId);
        if (item == null) {
            throw exception(ITEM_NOT_EXISTS, itemId);
        }
        if (HandoverItemDO.STATUS_TRANSFERRED.equals(item.getStatus())) {
            return item;
        }
        if (HandoverItemDO.TYPE_CONTRACT.equals(item.getItemType())) {
            return transferContract(item, targetUserId, administratorUserId, reason);
        }
        if (HandoverItemDO.TYPE_ACTIVE_TASK.equals(item.getItemType())) {
            return transferTask(item, targetUserId, administratorUserId, reason);
        }
        throw exception(ITEM_TYPE_INVALID, item.getItemType());
    }

    private HandoverItemDO transferContract(HandoverItemDO item, Long targetUserId,
                                             Long administratorUserId, String reason) {
        ContractDO contract = item.getContractId() == null
                ? null : contractMapper.selectByIdForUpdate(item.getContractId());
        if (contract == null) {
            return finish(item, targetUserId, administratorUserId, reason,
                    HandoverItemDO.STATUS_SKIPPED, "合同已删除或不存在");
        }
        Long currentOwner = contract.getOwnerUserId();
        if (Objects.equals(currentOwner, targetUserId)) {
            participantService.changeOwner(contract.getId(), item.getOriginalAssignee(), targetUserId);
            return finish(item, targetUserId, administratorUserId, reason,
                    HandoverItemDO.STATUS_TRANSFERRED, "目标用户已是合同负责人");
        }
        if (!Objects.equals(currentOwner, item.getOriginalAssignee())) {
            return finish(item, targetUserId, administratorUserId, reason,
                    HandoverItemDO.STATUS_SKIPPED, "合同负责人已发生变化");
        }
        contractMapper.updateById(new ContractDO().setId(contract.getId()).setOwnerUserId(targetUserId));
        participantService.changeOwner(contract.getId(), item.getOriginalAssignee(), targetUserId);
        return finish(item, targetUserId, administratorUserId, reason,
                HandoverItemDO.STATUS_TRANSFERRED, "合同负责人已交接");
    }

    private HandoverItemDO transferTask(HandoverItemDO item, Long targetUserId,
                                         Long administratorUserId, String reason) {
        Task task = taskService.createTaskQuery().taskId(item.getTaskId())
                .taskTenantId(FlowableUtils.getTenantId()).active().singleResult();
        if (task == null) {
            return finish(item, targetUserId, administratorUserId, reason,
                    HandoverItemDO.STATUS_SKIPPED, "审批任务已不再活动");
        }
        WorkflowBindingDO binding = workflowBindingMapper.selectByProcessInstanceId(task.getProcessInstanceId());
        boolean clmRunningTask = binding != null
                && WorkflowBindingDO.PURPOSE_APPROVAL.equals(binding.getPurpose())
                && ClmWorkflowBindingStatusEnum.RUNNING.getStatus().equals(binding.getStatus())
                && Objects.equals(binding.getContractId(), item.getContractId());
        if (!clmRunningTask) {
            return finish(item, targetUserId, administratorUserId, reason,
                    HandoverItemDO.STATUS_SKIPPED, "审批任务已不属于运行中的合同审批");
        }
        String originalAssignee = String.valueOf(item.getOriginalAssignee());
        String targetAssignee = String.valueOf(targetUserId);
        if (targetAssignee.equals(task.getAssignee())) {
            return finish(item, targetUserId, administratorUserId, reason,
                    HandoverItemDO.STATUS_TRANSFERRED, "目标用户已是当前审批人");
        }
        if (!originalAssignee.equals(task.getAssignee())) {
            return finish(item, targetUserId, administratorUserId, reason,
                    HandoverItemDO.STATUS_SKIPPED, "审批任务当前处理人已发生变化");
        }
        // 这是管理员受控重分配：直接操作仍活动的 runtime task，不冒充离职用户调用普通转交。
        taskService.setAssignee(task.getId(), targetAssignee);
        return finish(item, targetUserId, administratorUserId, reason,
                HandoverItemDO.STATUS_TRANSFERRED, "活动审批任务已重分配");
    }

    private HandoverItemDO finish(HandoverItemDO item, Long targetUserId, Long administratorUserId,
                                  String reason, String status, String resultMessage) {
        item.setTargetUserId(targetUserId).setStatus(status).setResultMessage(resultMessage);
        itemMapper.updateById(item);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("handoverItemId", item.getId());
        detail.put("itemType", item.getItemType());
        detail.put("taskId", item.getTaskId());
        detail.put("originalAssignee", item.getOriginalAssignee());
        detail.put("targetUserId", targetUserId);
        detail.put("administratorUserId", administratorUserId);
        detail.put("reason", reason);
        detail.put("result", status);
        detail.put("resultMessage", resultMessage);
        auditService.record(ClmAuditAggregateTypeEnum.HANDOVER, item.getCaseId(), item.getContractId(),
                ClmAuditActionEnum.HANDOVER_REASSIGN, detail);
        return item;
    }
}
