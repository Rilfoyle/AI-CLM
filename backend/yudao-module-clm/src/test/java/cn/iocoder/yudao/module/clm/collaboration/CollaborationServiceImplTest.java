package cn.iocoder.yudao.module.clm.collaboration;

import cn.iocoder.yudao.module.clm.access.ContractAccessService;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.clm.dal.mysql.contract.ContractMapper;
import cn.iocoder.yudao.module.clm.service.audit.ClmAuditService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CollaborationServiceImplTest {

    @Mock private CollaborationCaseMapper caseMapper;
    @Mock private CollaborationEventMapper eventMapper;
    @Mock private ContractMapper contractMapper;
    @Mock private cn.iocoder.yudao.module.clm.revision.ContractRevisionService revisionService;
    @Mock private ContractAccessService contractAccessService;
    @Mock private ClmAuditService auditService;
    @Mock private AdminUserApi adminUserApi;

    @InjectMocks private CollaborationServiceImpl collaborationService;

    @Test
    void start_shouldBindCurrentRevisionAndMoveContractToCollaborating() {
        ContractDO contract = new ContractDO().setId(1L).setCurrentRevisionId(10L).setTitle("采购合同");
        when(contractMapper.selectById(1L)).thenReturn(contract);
        when(adminUserApi.getUser(8L)).thenReturn(new AdminUserRespDTO().setId(8L));
        when(caseMapper.selectActiveByContractId(1L)).thenReturn(null);
        doAnswer(invocation -> {
            CollaborationCaseDO row = invocation.getArgument(0);
            row.setId(100L);
            return 1;
        }).when(caseMapper).insert(any(CollaborationCaseDO.class));

        CollaborationStartReqVO req = new CollaborationStartReqVO();
        req.setContractId(1L);
        req.setRevisionId(10L);
        req.setLegalUserId(8L);
        req.setReason("请审查付款条款");

        assertEquals(100L, collaborationService.start(req, 7L));
        verify(contractAccessService).assertCanEdit(contract, 7L);
        ArgumentCaptor<CollaborationEventDO> event = ArgumentCaptor.forClass(CollaborationEventDO.class);
        verify(eventMapper).insert(event.capture());
        assertEquals(10L, event.getValue().getRevisionId());
        assertEquals(CollaborationEventDO.TYPE_START, event.getValue().getEventType());
        verify(contractMapper).updateById(argThat((ContractDO update) ->
                "COLLABORATING".equals(update.getStageCode())));
    }

    @Test
    void complete_shouldBindConclusionToExactCurrentRevision() {
        CollaborationCaseDO collaboration = new CollaborationCaseDO().setId(100L).setContractId(1L)
                .setRequestedRevisionId(10L).setInitiatorUserId(7L).setLegalUserId(8L)
                .setStatus(CollaborationCaseDO.STATUS_RUNNING);
        ContractDO contract = new ContractDO().setId(1L).setCurrentRevisionId(11L);
        when(caseMapper.selectById(100L)).thenReturn(collaboration);
        when(contractMapper.selectById(1L)).thenReturn(contract);
        CollaborationActionReqVO req = new CollaborationActionReqVO();
        req.setCaseId(100L);
        req.setRevisionId(11L);
        req.setContent("法务审查完成");

        collaborationService.complete(req, 8L);

        verify(caseMapper).updateById(argThat((CollaborationCaseDO update) ->
                CollaborationCaseDO.STATUS_COMPLETED.equals(update.getStatus())
                        && Long.valueOf(11L).equals(update.getCompletedRevisionId())));
        verify(contractMapper).updateById(argThat((ContractDO update) ->
                "DRAFT".equals(update.getStageCode())));
    }

    @Test
    void get_shouldDeriveExpiredConclusionFromCurrentRevision() {
        CollaborationCaseDO collaboration = new CollaborationCaseDO().setId(100L).setContractId(1L)
                .setInitiatorUserId(7L).setLegalUserId(8L)
                .setRequestedRevisionId(10L).setCompletedRevisionId(10L)
                .setStatus(CollaborationCaseDO.STATUS_COMPLETED);
        when(caseMapper.selectById(100L)).thenReturn(collaboration);
        when(contractMapper.selectById(1L)).thenReturn(new ContractDO().setId(1L).setCurrentRevisionId(11L));
        when(eventMapper.selectListByCaseId(100L)).thenReturn(Collections.emptyList());
        when(adminUserApi.getUser(7L)).thenReturn(new AdminUserRespDTO().setId(7L).setNickname("业务发起人"));
        when(adminUserApi.getUser(8L)).thenReturn(new AdminUserRespDTO().setId(8L).setNickname("法务专员"));

        CollaborationCaseRespVO result = collaborationService.get(100L, 7L);

        assertTrue(result.getConclusionExpired());
        assertEquals(11L, result.getCurrentRevisionId());
        assertEquals("业务发起人", result.getStarterUserName());
        assertEquals("法务专员", result.getLegalUserName());
        assertEquals(Collections.emptyList(), result.getAvailableActions());
    }

    @Test
    void get_shouldExposeRoleScopedActionsForChangeRequestedCase() {
        CollaborationCaseDO collaboration = new CollaborationCaseDO().setId(100L).setContractId(1L)
                .setInitiatorUserId(7L).setLegalUserId(8L).setRequestedRevisionId(10L)
                .setStatus(CollaborationCaseDO.STATUS_CHANGE_REQUESTED);
        when(caseMapper.selectById(100L)).thenReturn(collaboration);
        when(contractMapper.selectById(1L)).thenReturn(new ContractDO().setId(1L).setCurrentRevisionId(11L));
        when(eventMapper.selectListByCaseId(100L)).thenReturn(Collections.emptyList());

        CollaborationCaseRespVO result = collaborationService.get(100L, 8L);

        assertEquals(10L, result.getRevisionId());
        assertEquals(List.of("COMMENT", "REQUEST_CHANGE", "COMPLETE"), result.getAvailableActions());
    }
}
