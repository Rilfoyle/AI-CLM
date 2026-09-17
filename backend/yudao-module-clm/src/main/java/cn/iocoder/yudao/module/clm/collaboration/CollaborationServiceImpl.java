package cn.iocoder.yudao.module.clm.collaboration;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.clm.access.ContractAccessService;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.clm.dal.mysql.contract.ContractMapper;
import cn.iocoder.yudao.module.clm.enums.audit.ClmAuditActionEnum;
import cn.iocoder.yudao.module.clm.enums.audit.ClmAuditAggregateTypeEnum;
import cn.iocoder.yudao.module.clm.revision.ContractRevisionDO;
import cn.iocoder.yudao.module.clm.revision.ContractRevisionService;
import cn.iocoder.yudao.module.clm.service.audit.ClmAuditService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.clm.enums.ErrorCodeConstants.*;

@Service
@Validated
public class CollaborationServiceImpl implements CollaborationService {

    @Resource private CollaborationCaseMapper caseMapper;
    @Resource private CollaborationEventMapper eventMapper;
    @Resource private ContractMapper contractMapper;
    @Resource private ContractRevisionService revisionService;
    @Resource private ContractAccessService contractAccessService;
    @Resource private ClmAuditService auditService;
    @Resource private AdminUserApi adminUserApi;

    @Override
    public PageResult<CollaborationCaseRespVO> getPage(CollaborationPageReqVO reqVO, Long userId) {
        PageResult<CollaborationCaseDO> page = caseMapper.selectPage(reqVO, userId);
        List<CollaborationCaseRespVO> list = new ArrayList<>(page.getList().size());
        for (CollaborationCaseDO item : page.getList()) {
            list.add(toResp(item, false, userId));
        }
        return new PageResult<>(list, page.getTotal());
    }

    @Override
    public CollaborationCaseRespVO get(Long id, Long userId) {
        CollaborationCaseDO collaboration = getRequiredCase(id);
        assertCaseParticipant(collaboration, userId);
        return toResp(collaboration, true, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long start(CollaborationStartReqVO reqVO, Long userId) {
        ContractDO contract = getRequiredContract(reqVO.getContractId());
        contractAccessService.assertCanEdit(contract, userId);
        assertCurrentRevision(contract, reqVO.getRevisionId());
        if (adminUserApi.getUser(reqVO.getLegalUserId()) == null) {
            throw exception(PARTICIPANT_USER_NOT_EXISTS, reqVO.getLegalUserId());
        }
        if (caseMapper.selectActiveByContractId(contract.getId()) != null) {
            throw exception(COLLABORATION_ACTIVE_EXISTS);
        }
        CollaborationCaseDO collaboration = new CollaborationCaseDO()
                .setContractId(contract.getId())
                .setRequestedRevisionId(reqVO.getRevisionId())
                .setInitiatorUserId(userId)
                .setLegalUserId(reqVO.getLegalUserId())
                .setStatus(CollaborationCaseDO.STATUS_RUNNING)
                .setReason(reqVO.getReason())
                .setConclusion("");
        caseMapper.insert(collaboration);
        appendEvent(collaboration, reqVO.getRevisionId(), CollaborationEventDO.TYPE_START, userId, reqVO.getReason());
        contractMapper.updateById(new ContractDO().setId(contract.getId()).setStageCode("COLLABORATING"));
        audit(collaboration, ClmAuditActionEnum.COLLABORATION_START, reqVO.getRevisionId(), reqVO.getReason());
        return collaboration.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void comment(CollaborationActionReqVO reqVO, Long userId) {
        CollaborationCaseDO collaboration = getRequiredCase(reqVO.getCaseId());
        assertCaseParticipant(collaboration, userId);
        assertActive(collaboration);
        Long revisionId = requireRevisionOfCase(collaboration, reqVO.getRevisionId());
        appendEvent(collaboration, revisionId, CollaborationEventDO.TYPE_COMMENT, userId, reqVO.getContent());
        audit(collaboration, ClmAuditActionEnum.COLLABORATION_COMMENT, revisionId, reqVO.getContent());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void requestChange(CollaborationActionReqVO reqVO, Long userId) {
        CollaborationCaseDO collaboration = getRequiredCase(reqVO.getCaseId());
        assertLegalAssignee(collaboration, userId);
        assertActive(collaboration);
        ContractDO contract = getRequiredContract(collaboration.getContractId());
        assertCurrentRevision(contract, reqVO.getRevisionId());
        caseMapper.updateById(new CollaborationCaseDO().setId(collaboration.getId())
                .setStatus(CollaborationCaseDO.STATUS_CHANGE_REQUESTED));
        appendEvent(collaboration, reqVO.getRevisionId(), CollaborationEventDO.TYPE_REQUEST_CHANGE,
                userId, reqVO.getContent());
        audit(collaboration, ClmAuditActionEnum.COLLABORATION_REQUEST_CHANGE,
                reqVO.getRevisionId(), reqVO.getContent());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void complete(CollaborationActionReqVO reqVO, Long userId) {
        CollaborationCaseDO collaboration = getRequiredCase(reqVO.getCaseId());
        assertLegalAssignee(collaboration, userId);
        assertActive(collaboration);
        ContractDO contract = getRequiredContract(collaboration.getContractId());
        assertCurrentRevision(contract, reqVO.getRevisionId());
        LocalDateTime now = LocalDateTime.now();
        caseMapper.updateById(new CollaborationCaseDO().setId(collaboration.getId())
                .setStatus(CollaborationCaseDO.STATUS_COMPLETED)
                .setCompletedRevisionId(reqVO.getRevisionId())
                .setConclusion(reqVO.getContent()).setFinishedTime(now));
        appendEvent(collaboration, reqVO.getRevisionId(), CollaborationEventDO.TYPE_COMPLETE,
                userId, reqVO.getContent());
        contractMapper.updateById(new ContractDO().setId(contract.getId()).setStageCode("DRAFT"));
        audit(collaboration, ClmAuditActionEnum.COLLABORATION_COMPLETE,
                reqVO.getRevisionId(), reqVO.getContent());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(CollaborationActionReqVO reqVO, Long userId) {
        CollaborationCaseDO collaboration = getRequiredCase(reqVO.getCaseId());
        if (!Objects.equals(collaboration.getInitiatorUserId(), userId)) {
            throw exception(COLLABORATION_ACTION_DENIED);
        }
        assertActive(collaboration);
        if (eventMapper.countHandledEvents(collaboration.getId(), collaboration.getLegalUserId()) > 0) {
            throw exception(COLLABORATION_CANCEL_HANDLED);
        }
        LocalDateTime now = LocalDateTime.now();
        caseMapper.updateById(new CollaborationCaseDO().setId(collaboration.getId())
                .setStatus(CollaborationCaseDO.STATUS_CANCELED).setFinishedTime(now));
        Long revisionId = reqVO.getRevisionId() == null
                ? collaboration.getRequestedRevisionId() : requireRevisionOfCase(collaboration, reqVO.getRevisionId());
        appendEvent(collaboration, revisionId, CollaborationEventDO.TYPE_CANCEL, userId, reqVO.getContent());
        ContractDO contract = getRequiredContract(collaboration.getContractId());
        contractMapper.updateById(new ContractDO().setId(contract.getId()).setStageCode("DRAFT"));
        audit(collaboration, ClmAuditActionEnum.COLLABORATION_CANCEL, revisionId, reqVO.getContent());
    }

    @Override
    public boolean hasValidConclusion(Long contractId, Long revisionId) {
        return revisionId != null && caseMapper.selectValidConclusion(contractId, revisionId) != null;
    }

    private CollaborationCaseRespVO toResp(CollaborationCaseDO item, boolean withEvents, Long userId) {
        ContractDO contract = contractMapper.selectById(item.getContractId());
        CollaborationCaseRespVO vo = new CollaborationCaseRespVO();
        vo.setId(item.getId());
        vo.setContractId(item.getContractId());
        vo.setContractName(contract == null ? "" : contract.getTitle());
        vo.setContractNo(contract == null ? null : contract.getContractNo());
        vo.setRevisionId(item.getRequestedRevisionId());
        vo.setRequestedRevisionId(item.getRequestedRevisionId());
        vo.setCompletedRevisionId(item.getCompletedRevisionId());
        vo.setCurrentRevisionId(contract == null ? null : contract.getCurrentRevisionId());
        vo.setConclusionExpired(CollaborationCaseDO.STATUS_COMPLETED.equals(item.getStatus())
                && contract != null && !Objects.equals(item.getCompletedRevisionId(), contract.getCurrentRevisionId()));
        vo.setInitiatorUserId(item.getInitiatorUserId());
        vo.setLegalUserId(item.getLegalUserId());
        AdminUserRespDTO initiator = adminUserApi.getUser(item.getInitiatorUserId());
        AdminUserRespDTO legalUser = adminUserApi.getUser(item.getLegalUserId());
        vo.setStarterUserName(initiator == null ? null : initiator.getNickname());
        vo.setLegalUserName(legalUser == null ? null : legalUser.getNickname());
        vo.setStatus(item.getStatus());
        vo.setReason(item.getReason());
        vo.setConclusion(item.getConclusion());
        vo.setCreateTime(item.getCreateTime());
        vo.setUpdateTime(item.getUpdateTime());
        vo.setFinishedTime(item.getFinishedTime());
        vo.setAvailableActions(resolveAvailableActions(item, userId));
        if (withEvents) {
            List<CollaborationEventDO> events = eventMapper.selectListByCaseId(item.getId());
            vo.setEvents(events);
            vo.setComments(toComments(events));
        }
        return vo;
    }

    private List<String> resolveAvailableActions(CollaborationCaseDO item, Long userId) {
        if (!item.isActive()) return List.of();
        List<String> actions = new ArrayList<>();
        if (Objects.equals(item.getInitiatorUserId(), userId)) {
            actions.add("COMMENT");
            if (eventMapper.countHandledEvents(item.getId(), item.getLegalUserId()) == 0) {
                actions.add("CANCEL");
            }
        }
        if (Objects.equals(item.getLegalUserId(), userId)) {
            if (!actions.contains("COMMENT")) actions.add("COMMENT");
            actions.add("REQUEST_CHANGE");
            actions.add("COMPLETE");
        }
        return actions;
    }

    private List<CollaborationCommentRespVO> toComments(List<CollaborationEventDO> events) {
        List<CollaborationCommentRespVO> result = new ArrayList<>(events.size());
        for (CollaborationEventDO event : events) {
            CollaborationCommentRespVO comment = new CollaborationCommentRespVO();
            comment.setId(event.getId());
            comment.setUserId(event.getActorUserId());
            AdminUserRespDTO user = adminUserApi.getUser(event.getActorUserId());
            comment.setUserName(user == null ? null : user.getNickname());
            comment.setRevisionId(event.getRevisionId());
            comment.setContent(event.getContent());
            comment.setEventType(event.getEventType());
            comment.setCreateTime(event.getCreateTime());
            result.add(comment);
        }
        return result;
    }

    private Long requireRevisionOfCase(CollaborationCaseDO collaboration, Long revisionId) {
        if (revisionId == null) throw exception(COLLABORATION_REVISION_REQUIRED);
        ContractRevisionDO revision = revisionService.getRequiredRevision(revisionId);
        if (!Objects.equals(revision.getContractId(), collaboration.getContractId())) {
            throw exception(CONTRACT_REVISION_CONTRACT_MISMATCH);
        }
        return revisionId;
    }

    private void appendEvent(CollaborationCaseDO collaboration, Long revisionId, String type,
                             Long userId, String content) {
        eventMapper.insert(new CollaborationEventDO().setCaseId(collaboration.getId())
                .setContractId(collaboration.getContractId()).setRevisionId(revisionId)
                .setEventType(type).setActorUserId(userId).setContent(StrUtil.nullToEmpty(content)));
    }

    private void audit(CollaborationCaseDO collaboration, ClmAuditActionEnum action,
                       Long revisionId, String content) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("collaborationCaseId", collaboration.getId());
        detail.put("revisionId", revisionId);
        detail.put("content", content);
        auditService.record(ClmAuditAggregateTypeEnum.CONTRACT, collaboration.getContractId(),
                collaboration.getContractId(), action, detail);
    }

    private CollaborationCaseDO getRequiredCase(Long id) {
        CollaborationCaseDO item = caseMapper.selectById(id);
        if (item == null) throw exception(COLLABORATION_NOT_EXISTS);
        return item;
    }

    private ContractDO getRequiredContract(Long id) {
        ContractDO contract = contractMapper.selectById(id);
        if (contract == null) throw exception(CONTRACT_NOT_EXISTS);
        return contract;
    }

    private void assertCurrentRevision(ContractDO contract, Long revisionId) {
        if (revisionId == null || !Objects.equals(contract.getCurrentRevisionId(), revisionId)) {
            throw exception(CONTRACT_REVISION_CONFLICT, contract.getCurrentRevisionId());
        }
    }

    private void assertCaseParticipant(CollaborationCaseDO item, Long userId) {
        if (!Objects.equals(item.getInitiatorUserId(), userId)
                && !Objects.equals(item.getLegalUserId(), userId)) {
            throw exception(COLLABORATION_ACTION_DENIED);
        }
    }

    private void assertLegalAssignee(CollaborationCaseDO item, Long userId) {
        if (!Objects.equals(item.getLegalUserId(), userId)) {
            throw exception(COLLABORATION_ACTION_DENIED);
        }
    }

    private void assertActive(CollaborationCaseDO item) {
        if (!item.isActive()) throw exception(COLLABORATION_NOT_ACTIVE);
    }

}
