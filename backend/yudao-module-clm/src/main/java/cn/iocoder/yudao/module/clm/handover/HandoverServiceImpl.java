package cn.iocoder.yudao.module.clm.handover;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.workflow.WorkflowBindingDO;
import cn.iocoder.yudao.module.clm.dal.mysql.contract.ContractMapper;
import cn.iocoder.yudao.module.clm.dal.mysql.workflow.WorkflowBindingMapper;
import cn.iocoder.yudao.module.clm.enums.audit.ClmAuditActionEnum;
import cn.iocoder.yudao.module.clm.enums.audit.ClmAuditAggregateTypeEnum;
import cn.iocoder.yudao.module.clm.enums.workflow.ClmWorkflowBindingStatusEnum;
import cn.iocoder.yudao.module.clm.service.audit.ClmAuditService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.clm.handover.HandoverErrors.CASE_NOT_EXISTS;
import static cn.iocoder.yudao.module.clm.handover.HandoverErrors.CONTRACT_NOT_EXISTS;
import static cn.iocoder.yudao.module.clm.handover.HandoverErrors.ITEM_NOT_EXISTS;
import static cn.iocoder.yudao.module.clm.handover.HandoverErrors.REASON_REQUIRED;
import static cn.iocoder.yudao.module.clm.handover.HandoverErrors.SOURCE_USER_NOT_EXISTS;
import static cn.iocoder.yudao.module.clm.handover.HandoverErrors.TARGET_USER_INVALID;

@Service
public class HandoverServiceImpl implements HandoverService {

    @Resource private HandoverCaseMapper caseMapper;
    @Resource private HandoverItemMapper itemMapper;
    @Resource private ContractMapper contractMapper;
    @Resource private WorkflowBindingMapper workflowBindingMapper;
    @Resource private TaskService taskService;
    @Resource private AdminUserApi adminUserApi;
    @Resource private HandoverScopeValidator scopeValidator;
    @Resource private HandoverTransferExecutor transferExecutor;
    @Resource private ClmAuditService auditService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HandoverRefreshRespVO refresh(Long sourceUserId, Long administratorUserId) {
        if (adminUserApi.getUser(sourceUserId) == null) {
            throw exception(SOURCE_USER_NOT_EXISTS, sourceUserId);
        }
        List<ContractDO> contracts = contractMapper.selectList(new LambdaQueryWrapperX<ContractDO>()
                .eq(ContractDO::getOwnerUserId, sourceUserId).orderByAsc(ContractDO::getId));
        Set<Long> currentContractIds = new HashSet<>();
        for (ContractDO contract : contracts) {
            currentContractIds.add(contract.getId());
        }

        Map<String, WorkflowBindingDO> runningBindingByProcess = loadRunningApprovalBindings();
        List<Task> tasks = runningBindingByProcess.isEmpty() ? Collections.emptyList()
                : taskService.createTaskQuery().processInstanceIdIn(runningBindingByProcess.keySet())
                .taskAssignee(String.valueOf(sourceUserId)).taskTenantId(FlowableUtils.getTenantId())
                .active().list();
        Set<String> currentTaskIds = new HashSet<>();
        for (Task task : tasks) {
            WorkflowBindingDO binding = runningBindingByProcess.get(task.getProcessInstanceId());
            if (binding == null) {
                continue;
            }
            currentTaskIds.add(task.getId());
        }

        HandoverCaseDO handoverCase = caseMapper.selectLatestBySourceForUpdate(sourceUserId);
        boolean created = handoverCase == null;
        if (!created && HandoverCaseDO.STATUS_COMPLETED.equals(handoverCase.getStatus())
                && (!currentContractIds.isEmpty() || !currentTaskIds.isEmpty())) {
            // 完成后若同一人重新成为负责人或审批人，开启新一轮交接，保留上一轮历史。
            handoverCase = null;
            created = true;
        }
        if (created) {
            handoverCase = new HandoverCaseDO().setSourceUserId(sourceUserId)
                    .setStatus(HandoverCaseDO.STATUS_OPEN).setReason("");
            caseMapper.insert(handoverCase);
        }

        int added = 0;
        for (ContractDO contract : contracts) {
            if (itemMapper.selectContractItem(handoverCase.getId(), contract.getId()) != null) {
                continue;
            }
            itemMapper.insert(new HandoverItemDO().setCaseId(handoverCase.getId())
                    .setItemType(HandoverItemDO.TYPE_CONTRACT).setContractId(contract.getId())
                    .setOriginalAssignee(sourceUserId).setStatus(HandoverItemDO.STATUS_PENDING)
                    .setResultMessage(""));
            added++;
        }
        for (Task task : tasks) {
            WorkflowBindingDO binding = runningBindingByProcess.get(task.getProcessInstanceId());
            if (binding == null) {
                continue;
            }
            if (itemMapper.selectTaskItem(handoverCase.getId(), task.getId()) != null) {
                continue;
            }
            itemMapper.insert(new HandoverItemDO().setCaseId(handoverCase.getId())
                    .setItemType(HandoverItemDO.TYPE_ACTIVE_TASK).setContractId(binding.getContractId())
                    .setTaskId(task.getId()).setOriginalAssignee(sourceUserId)
                    .setStatus(HandoverItemDO.STATUS_PENDING).setResultMessage(""));
            added++;
        }

        List<HandoverItemDO> items = itemMapper.selectListByCaseId(handoverCase.getId());
        for (HandoverItemDO item : items) {
            if (!isOutstanding(item)) {
                continue;
            }
            boolean stillCurrent = HandoverItemDO.TYPE_CONTRACT.equals(item.getItemType())
                    ? currentContractIds.contains(item.getContractId())
                    : HandoverItemDO.TYPE_ACTIVE_TASK.equals(item.getItemType())
                    && currentTaskIds.contains(item.getTaskId());
            if (!stillCurrent) {
                itemMapper.updateById(new HandoverItemDO().setId(item.getId())
                        .setStatus(HandoverItemDO.STATUS_SKIPPED)
                        .setResultMessage("刷新时已不再属于离职用户的活动事项"));
            }
        }

        items = itemMapper.selectListByCaseId(handoverCase.getId());
        long pendingCount = items.stream().filter(this::isOutstanding).count();
        String status = pendingCount == 0 ? HandoverCaseDO.STATUS_COMPLETED : HandoverCaseDO.STATUS_OPEN;
        handoverCase.setStatus(status).setFinishedTime(pendingCount == 0 ? LocalDateTime.now() : null);
        caseMapper.updateState(handoverCase);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("sourceUserId", sourceUserId);
        audit.put("administratorUserId", administratorUserId);
        audit.put("created", created);
        audit.put("addedItemCount", added);
        audit.put("itemCount", items.size());
        audit.put("pendingCount", pendingCount);
        audit.put("status", status);
        auditService.record(ClmAuditAggregateTypeEnum.HANDOVER, handoverCase.getId(), null,
                ClmAuditActionEnum.HANDOVER_OPEN, audit);
        return new HandoverRefreshRespVO(handoverCase.getId(), status, (long) items.size(), pendingCount);
    }

    @Override
    public PageResult<HandoverCaseSummaryRespVO> getPage(HandoverPageReqVO reqVO) {
        PageResult<HandoverCaseDO> page = caseMapper.selectPage(reqVO);
        List<HandoverCaseSummaryRespVO> result = new ArrayList<>(page.getList().size());
        for (HandoverCaseDO handoverCase : page.getList()) {
            List<HandoverItemDO> items = itemMapper.selectListByCaseId(handoverCase.getId());
            HandoverCaseSummaryRespVO row = new HandoverCaseSummaryRespVO();
            row.setId(handoverCase.getId());
            row.setSourceUserId(handoverCase.getSourceUserId());
            row.setTargetUserId(handoverCase.getTargetUserId());
            row.setStatus(handoverCase.getStatus());
            row.setReason(handoverCase.getReason());
            row.setHandledBy(handoverCase.getHandledBy());
            row.setItemCount((long) items.size());
            row.setPendingCount(items.stream().filter(this::isOutstanding).count());
            row.setFinishedTime(handoverCase.getFinishedTime());
            row.setCreateTime(handoverCase.getCreateTime());
            row.setUpdateTime(handoverCase.getUpdateTime());
            result.add(row);
        }
        return new PageResult<>(result, page.getTotal());
    }

    @Override
    public HandoverDetailRespVO get(Long id) {
        HandoverCaseDO handoverCase = requiredCase(id);
        List<HandoverItemDO> items = itemMapper.selectListByCaseId(id);
        Map<Long, ContractDO> contracts = loadContracts(items);
        HandoverDetailRespVO response = new HandoverDetailRespVO();
        response.setId(handoverCase.getId());
        response.setSourceUserId(handoverCase.getSourceUserId());
        response.setTargetUserId(handoverCase.getTargetUserId());
        response.setStatus(handoverCase.getStatus());
        response.setReason(handoverCase.getReason());
        response.setHandledBy(handoverCase.getHandledBy());
        response.setFinishedTime(handoverCase.getFinishedTime());
        List<HandoverItemRespVO> rows = new ArrayList<>(items.size());
        for (HandoverItemDO item : items) {
            ContractDO contract = contracts.get(item.getContractId());
            HandoverItemRespVO row = new HandoverItemRespVO();
            row.setId(item.getId());
            row.setItemType(item.getItemType());
            row.setContractId(item.getContractId());
            row.setContractNo(contract == null ? null : contract.getContractNo());
            row.setContractName(contract == null ? null : contract.getTitle());
            row.setTaskId(item.getTaskId());
            row.setOriginalAssignee(item.getOriginalAssignee());
            row.setTargetUserId(item.getTargetUserId());
            row.setStatus(item.getStatus());
            row.setResultMessage(item.getResultMessage());
            rows.add(row);
        }
        response.setItems(rows);
        return response;
    }

    @Override
    public HandoverReassignRespVO reassign(HandoverReassignReqVO reqVO, Long administratorUserId) {
        HandoverCaseDO handoverCase = requiredCase(reqVO.getCaseId());
        if (StrUtil.isBlank(reqVO.getReason())) {
            throw exception(REASON_REQUIRED);
        }
        AdminUserRespDTO target = adminUserApi.getUser(reqVO.getTargetUserId());
        if (target == null || !CommonStatusEnum.isEnable(target.getStatus())
                || Objects.equals(handoverCase.getSourceUserId(), target.getId())) {
            throw exception(TARGET_USER_INVALID);
        }

        List<HandoverItemDO> allItems = itemMapper.selectListByCaseId(handoverCase.getId());
        List<HandoverItemDO> selected = selectItems(allItems, reqVO.getItemIds());
        List<HandoverItemDO> executable = selected.stream().filter(this::isOutstanding).toList();
        Map<Long, ContractDO> contracts = loadContracts(executable);
        for (HandoverItemDO item : executable) {
            if (item.getContractId() == null || !contracts.containsKey(item.getContractId())) {
                throw exception(CONTRACT_NOT_EXISTS, item.getContractId());
            }
        }
        scopeValidator.assertCanOwnContracts(target.getId(), new ArrayList<>(contracts.values()));

        handoverCase.setTargetUserId(target.getId()).setReason(reqVO.getReason())
                .setHandledBy(administratorUserId).setStatus(HandoverCaseDO.STATUS_PROCESSING)
                .setFinishedTime(null);
        caseMapper.updateState(handoverCase);

        Map<Long, HandoverItemDO> resultById = new HashMap<>();
        for (HandoverItemDO item : selected) {
            if (!isOutstanding(item)) {
                resultById.put(item.getId(), item);
                continue;
            }
            try {
                resultById.put(item.getId(), transferExecutor.transfer(item.getId(), target.getId(),
                        administratorUserId, reqVO.getReason()));
            } catch (RuntimeException ex) {
                HandoverItemDO failed = new HandoverItemDO().setId(item.getId())
                        .setTargetUserId(target.getId()).setStatus(HandoverItemDO.STATUS_FAILED)
                        .setResultMessage("交接执行失败，请刷新后重试");
                itemMapper.updateById(failed);
                item.setTargetUserId(target.getId()).setStatus(HandoverItemDO.STATUS_FAILED)
                        .setResultMessage("交接执行失败，请刷新后重试");
                auditFailure(handoverCase, item, target.getId(), administratorUserId, reqVO.getReason());
                resultById.put(item.getId(), item);
            }
        }

        long pendingCount = itemMapper.countOutstanding(handoverCase.getId());
        String status = pendingCount == 0 ? HandoverCaseDO.STATUS_COMPLETED : HandoverCaseDO.STATUS_OPEN;
        handoverCase.setStatus(status).setFinishedTime(pendingCount == 0 ? LocalDateTime.now() : null);
        caseMapper.updateState(handoverCase);

        HandoverReassignRespVO response = new HandoverReassignRespVO();
        response.setCaseId(handoverCase.getId());
        response.setStatus(status);
        response.setPendingCount(pendingCount);
        List<HandoverReassignRespVO.ItemResult> resultItems = new ArrayList<>(selected.size());
        for (HandoverItemDO selectedItem : selected) {
            HandoverItemDO item = resultById.getOrDefault(selectedItem.getId(), selectedItem);
            HandoverReassignRespVO.ItemResult result = new HandoverReassignRespVO.ItemResult();
            result.setItemId(item.getId());
            result.setItemType(item.getItemType());
            result.setStatus(item.getStatus());
            result.setResultMessage(item.getResultMessage());
            resultItems.add(result);
        }
        response.setItems(resultItems);
        return response;
    }

    private Map<String, WorkflowBindingDO> loadRunningApprovalBindings() {
        List<WorkflowBindingDO> bindings = workflowBindingMapper.selectList(
                new LambdaQueryWrapperX<WorkflowBindingDO>()
                        .eq(WorkflowBindingDO::getPurpose, WorkflowBindingDO.PURPOSE_APPROVAL)
                        .eq(WorkflowBindingDO::getStatus, ClmWorkflowBindingStatusEnum.RUNNING.getStatus())
                        .isNotNull(WorkflowBindingDO::getProcessInstanceId));
        Map<String, WorkflowBindingDO> byProcess = new HashMap<>();
        for (WorkflowBindingDO binding : bindings) {
            byProcess.put(binding.getProcessInstanceId(), binding);
        }
        return byProcess;
    }

    private List<HandoverItemDO> selectItems(List<HandoverItemDO> allItems, List<Long> requestedIds) {
        if (requestedIds == null || requestedIds.isEmpty()) {
            return allItems.stream().filter(this::isOutstanding).toList();
        }
        Set<Long> requested = new LinkedHashSet<>(requestedIds);
        Map<Long, HandoverItemDO> byId = new HashMap<>();
        for (HandoverItemDO item : allItems) {
            byId.put(item.getId(), item);
        }
        List<HandoverItemDO> selected = new ArrayList<>(requested.size());
        for (Long itemId : requested) {
            HandoverItemDO item = byId.get(itemId);
            if (item == null) {
                throw exception(ITEM_NOT_EXISTS, itemId);
            }
            selected.add(item);
        }
        return selected;
    }

    private Map<Long, ContractDO> loadContracts(List<HandoverItemDO> items) {
        Set<Long> ids = new LinkedHashSet<>();
        for (HandoverItemDO item : items) {
            if (item.getContractId() != null) {
                ids.add(item.getContractId());
            }
        }
        Map<Long, ContractDO> contracts = new LinkedHashMap<>();
        for (Long id : ids) {
            ContractDO contract = contractMapper.selectById(id);
            if (contract != null) {
                contracts.put(id, contract);
            }
        }
        return contracts;
    }

    private HandoverCaseDO requiredCase(Long id) {
        HandoverCaseDO handoverCase = caseMapper.selectById(id);
        if (handoverCase == null) {
            throw exception(CASE_NOT_EXISTS);
        }
        return handoverCase;
    }

    private boolean isOutstanding(HandoverItemDO item) {
        return HandoverItemDO.STATUS_PENDING.equals(item.getStatus())
                || HandoverItemDO.STATUS_FAILED.equals(item.getStatus());
    }

    private void auditFailure(HandoverCaseDO handoverCase, HandoverItemDO item, Long targetUserId,
                              Long administratorUserId, String reason) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("handoverItemId", item.getId());
        detail.put("itemType", item.getItemType());
        detail.put("taskId", item.getTaskId());
        detail.put("originalAssignee", item.getOriginalAssignee());
        detail.put("targetUserId", targetUserId);
        detail.put("administratorUserId", administratorUserId);
        detail.put("reason", reason);
        detail.put("result", HandoverItemDO.STATUS_FAILED);
        detail.put("resultMessage", item.getResultMessage());
        auditService.record(ClmAuditAggregateTypeEnum.HANDOVER, handoverCase.getId(), item.getContractId(),
                ClmAuditActionEnum.HANDOVER_REASSIGN, detail);
    }
}
