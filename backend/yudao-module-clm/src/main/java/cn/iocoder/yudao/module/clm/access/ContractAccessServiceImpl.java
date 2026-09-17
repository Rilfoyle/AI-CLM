package cn.iocoder.yudao.module.clm.access;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.clm.collaboration.CollaborationCaseMapper;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractParticipantDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.workflow.WorkflowBindingDO;
import cn.iocoder.yudao.module.clm.dal.mysql.contract.ContractParticipantMapper;
import cn.iocoder.yudao.module.clm.dal.mysql.workflow.WorkflowBindingMapper;
import cn.iocoder.yudao.module.clm.enums.contract.ClmApprovalStatusEnum;
import cn.iocoder.yudao.module.clm.enums.contract.ClmLifecycleStatusEnum;
import cn.iocoder.yudao.module.clm.permission.UserScopeMapper;
import jakarta.annotation.Resource;
import org.flowable.engine.HistoryService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.clm.enums.ErrorCodeConstants.CONTRACT_ACCESS_DENIED;
import static cn.iocoder.yudao.module.clm.enums.ErrorCodeConstants.CONTRACT_LOCKED_BY_APPROVAL;
import static cn.iocoder.yudao.module.clm.enums.ErrorCodeConstants.CONTRACT_NOT_EXISTS;

/**
 * 合同对象级权限 Service 实现类
 */
@Service
public class ContractAccessServiceImpl implements ContractAccessService {

    @Resource
    private ContractParticipantMapper contractParticipantMapper;
    @Resource
    private WorkflowBindingMapper workflowBindingMapper;
    @Resource
    private CollaborationCaseMapper collaborationCaseMapper;
    @Resource
    private UserScopeMapper userScopeMapper;

    @Resource
    private HistoryService historyService;

    @Override
    public boolean canView(ContractDO contract, Long userId) {
        if (contract == null || userId == null) {
            return false;
        }
        return isOwner(contract, userId)
                || hasParticipantFlag(contract, userId, ContractParticipantDO::getCanView)
                || collaborationCaseMapper.hasUserRelation(contract.getId(), userId)
                || workflowBindingMapper.hasCopyRelation(contract.getId(), userId)
                || hasViewScope(contract, userId)
                || isProcessParticipant(contract, userId);
    }

    @Override
    public boolean canEdit(ContractDO contract, Long userId) {
        if (!hasEditPrincipal(contract, userId)) {
            return false;
        }
        return !ClmApprovalStatusEnum.RUNNING.getStatus().equals(contract.getApprovalStatus())
                && isLifecycleEditable(contract);
    }

    @Override
    public boolean canDownload(ContractDO contract, Long userId) {
        if (contract == null || userId == null) {
            return false;
        }
        return isOwner(contract, userId)
                || hasParticipantFlag(contract, userId, ContractParticipantDO::getCanDownload)
                || collaborationCaseMapper.hasUserRelation(contract.getId(), userId)
                || workflowBindingMapper.hasCopyRelation(contract.getId(), userId)
                || hasViewScope(contract, userId)
                || isProcessParticipant(contract, userId);
    }

    @Override
    public boolean canManage(ContractDO contract, Long userId) {
        if (contract == null || userId == null) {
            return false;
        }
        return isOwner(contract, userId)
                || hasParticipantFlag(contract, userId, ContractParticipantDO::getCanManage)
                || hasManageScope(contract, userId);
    }

    @Override
    public boolean hasEditPrincipal(ContractDO contract, Long userId) {
        if (contract == null || userId == null) {
            return false;
        }
        return isOwner(contract, userId)
                || hasParticipantFlag(contract, userId, ContractParticipantDO::getCanEdit)
                || collaborationCaseMapper.hasActiveLegalAssignment(contract.getId(), userId);
    }

    @Override
    public boolean isProcessParticipant(ContractDO contract, Long userId) {
        if (contract == null || userId == null || contract.getId() == null) {
            return false;
        }
        List<WorkflowBindingDO> bindings = workflowBindingMapper.selectListByContractId(contract.getId());
        for (WorkflowBindingDO binding : bindings) {
            if (StrUtil.isEmpty(binding.getProcessInstanceId())) {
                continue;
            }
            if (isProcessInstanceParticipant(binding.getProcessInstanceId(), userId)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isProcessInstanceParticipant(String processInstanceId, Long userId) {
        if (StrUtil.isEmpty(processInstanceId) || userId == null) {
            return false;
        }
        String user = String.valueOf(userId);
        // 1. 流程发起人
        if (historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId).startedBy(user).count() > 0) {
            return true;
        }
        // 2. 任务处理人
        if (historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId).taskAssignee(user).count() > 0) {
            return true;
        }
        // 3. 任务涉及人（候选人、抄送等）
        return historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId).taskInvolvedUser(user).count() > 0;
    }

    @Override
    public void assertCanView(ContractDO contract, Long userId) {
        assertContractExists(contract);
        if (!canView(contract, userId)) {
            throw exception(CONTRACT_ACCESS_DENIED);
        }
    }

    @Override
    public void assertCanEdit(ContractDO contract, Long userId) {
        assertContractExists(contract);
        if (!hasEditPrincipal(contract, userId)) {
            throw exception(CONTRACT_ACCESS_DENIED);
        }
        // 有编辑主体权限但状态不允许：给出明确的锁定原因，而不是笼统的"无权访问"
        if (ClmApprovalStatusEnum.RUNNING.getStatus().equals(contract.getApprovalStatus())) {
            throw exception(CONTRACT_LOCKED_BY_APPROVAL);
        }
        if (!canEdit(contract, userId)) {
            throw exception(CONTRACT_ACCESS_DENIED);
        }
    }

    @Override
    public void assertCanDownload(ContractDO contract, Long userId) {
        assertContractExists(contract);
        if (!canDownload(contract, userId)) {
            throw exception(CONTRACT_ACCESS_DENIED);
        }
    }

    @Override
    public void assertCanManage(ContractDO contract, Long userId) {
        assertContractExists(contract);
        if (!canManage(contract, userId)) {
            throw exception(CONTRACT_ACCESS_DENIED);
        }
    }

    @Override
    public void assertCanSubmit(ContractDO contract, Long userId) {
        assertContractExists(contract);
        if (!hasEditPrincipal(contract, userId)) {
            throw exception(CONTRACT_ACCESS_DENIED);
        }
    }

    @Override
    public void assertCanViewBinding(ContractDO contract, String processInstanceId, Long userId) {
        assertContractExists(contract);
        if (canView(contract, userId) || isProcessInstanceParticipant(processInstanceId, userId)) {
            return;
        }
        throw exception(CONTRACT_ACCESS_DENIED);
    }

    // ========== 私有方法 ==========

    private void assertContractExists(ContractDO contract) {
        if (contract == null) {
            throw exception(CONTRACT_NOT_EXISTS);
        }
    }

    private boolean isOwner(ContractDO contract, Long userId) {
        return Objects.equals(contract.getOwnerUserId(), userId);
    }

    private boolean hasParticipantFlag(ContractDO contract, Long userId,
                                       Function<ContractParticipantDO, Boolean> flag) {
        if (contract.getId() == null) {
            return false;
        }
        ContractParticipantDO participant = contractParticipantMapper
                .selectByContractIdAndUserId(contract.getId(), userId);
        return participant != null && BooleanUtil.isTrue(flag.apply(participant));
    }

    private boolean hasViewScope(ContractDO contract, Long userId) {
        return contract.getOwnerDeptId() != null && contract.getTypeId() != null
                && userScopeMapper.hasViewScope(userId, contract.getOwnerDeptId(), contract.getTypeId());
    }

    private boolean hasManageScope(ContractDO contract, Long userId) {
        return contract.getOwnerDeptId() != null && contract.getTypeId() != null
                && userScopeMapper.hasManageScope(userId, contract.getOwnerDeptId(), contract.getTypeId());
    }

    private boolean isLifecycleEditable(ContractDO contract) {
        // 第一期以“获批精确修订”为终点。审批通过后只能回看，不允许继续改写同一合同；
        // 撤回、退回或拒绝会先把合同投影恢复为 DRAFT，再允许形成新修订并重提。
        return ClmLifecycleStatusEnum.DRAFT.getStatus().equals(contract.getLifecycleStatus());
    }

}
