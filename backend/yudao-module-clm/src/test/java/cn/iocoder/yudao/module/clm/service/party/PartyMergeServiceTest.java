package cn.iocoder.yudao.module.clm.service.party;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.clm.controller.admin.party.vo.PartyDuplicateGroupRespVO;
import cn.iocoder.yudao.module.clm.controller.admin.party.vo.PartyMergeReqVO;
import cn.iocoder.yudao.module.clm.controller.admin.party.vo.PartyMergeRespVO;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractPartyDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.party.PartyDO;
import cn.iocoder.yudao.module.clm.dal.mysql.contract.ContractMapper;
import cn.iocoder.yudao.module.clm.dal.mysql.contract.ContractPartyMapper;
import cn.iocoder.yudao.module.clm.dal.mysql.party.PartyMapper;
import cn.iocoder.yudao.module.clm.enums.audit.ClmAuditActionEnum;
import cn.iocoder.yudao.module.clm.enums.audit.ClmAuditAggregateTypeEnum;
import cn.iocoder.yudao.module.clm.enums.contract.ClmApprovalStatusEnum;
import cn.iocoder.yudao.module.clm.enums.contract.ClmLifecycleStatusEnum;
import cn.iocoder.yudao.module.clm.service.audit.ClmAuditService;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.clm.service.party.PartyMergeErrors.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PartyMergeServiceTest {

    @InjectMocks private PartyMergeService service;
    @Mock private PartyMapper partyMapper;
    @Mock private ContractPartyMapper contractPartyMapper;
    @Mock private ContractMapper contractMapper;
    @Mock private ClmAuditService auditService;

    @Test
    void duplicateCandidates_prioritizesCreditCodeThenNormalizedName() {
        PartyDO codeA = party(1L, false, 1, "同名甲", " 91310000abc ");
        PartyDO codeB = party(2L, false, 1, "不同名", "91310000ABC");
        PartyDO nameA = party(3L, false, 1, " Acme　Ltd ", "CODE-3");
        PartyDO nameB = party(4L, false, 1, "ＡＣＭＥ LTD", "CODE-4");
        PartyDO nameClaimedByCode = party(5L, false, 1, "同名甲", "CODE-5");
        when(partyMapper.selectDuplicateCandidates(CommonStatusEnum.ENABLE.getStatus(), null, null))
                .thenReturn(List.of(codeA, codeB, nameA, nameB, nameClaimedByCode));

        List<PartyDuplicateGroupRespVO> result = service.getDuplicateCandidates(null, null);

        assertEquals(2, result.size());
        assertEquals(PartyMergeService.MATCH_CREDIT_CODE, result.get(0).getMatchType());
        assertEquals("91310000ABC", result.get(0).getMatchValue());
        assertEquals(1L, result.get(0).getRecommendedTargetPartyId());
        assertEquals(List.of(1L, 2L), result.get(0).getParties().stream().map(item -> item.getId()).toList());
        assertEquals(PartyMergeService.MATCH_NORMALIZED_NAME, result.get(1).getMatchType());
        assertEquals("acmeltd", result.get(1).getMatchValue());
        assertEquals(List.of(3L, 4L), result.get(1).getParties().stream().map(item -> item.getId()).toList());
    }

    @Test
    void merge_rewritesOnlyDraftAndCollaboratingLiveReferences() {
        PartyDO target = party(1L, false, 1, "保留主体", "91310000TARGET001").setShortName("TARGET");
        PartyDO source = party(2L, false, 1, "重复主体", "91310000SOURCE001").setRemark("旧备注");
        stubLockedParties(source, target);
        List<ContractPartyDO> sourceLinks = List.of(
                link(1000L, 100L, 2L), link(1001L, 101L, 2L),
                link(1002L, 102L, 2L), link(1003L, 103L, 2L));
        stubLinks(2L, sourceLinks, 1L, List.of());
        when(contractMapper.selectByIds(eq(Set.of(100L, 101L, 102L, 103L)))).thenReturn(List.of(
                contract(100L, ClmLifecycleStatusEnum.DRAFT.getStatus(),
                        ClmApprovalStatusEnum.NOT_SUBMITTED.getStatus(), "DRAFT"),
                contract(101L, ClmLifecycleStatusEnum.DRAFT.getStatus(),
                        ClmApprovalStatusEnum.NOT_SUBMITTED.getStatus(), "COLLABORATING"),
                contract(102L, ClmLifecycleStatusEnum.DRAFT.getStatus(),
                        ClmApprovalStatusEnum.RUNNING.getStatus(), "APPROVING"),
                contract(103L, ClmLifecycleStatusEnum.APPROVED.getStatus(),
                        ClmApprovalStatusEnum.APPROVED.getStatus(), "APPROVED")));

        PartyMergeRespVO result = service.merge(req(2L, 1L));

        assertEquals(2, result.getRewrittenContractCount());
        assertEquals(2, result.getRewrittenLinkCount());
        assertEquals(0, result.getDeduplicatedLinkCount());
        assertEquals(2, result.getProtectedContractCount());
        assertFalse(result.getIdempotent());
        ArgumentCaptor<ContractPartyDO> linkCaptor = ArgumentCaptor.forClass(ContractPartyDO.class);
        verify(contractPartyMapper, times(2)).updateById(linkCaptor.capture());
        assertEquals(List.of(1000L, 1001L), linkCaptor.getAllValues().stream().map(ContractPartyDO::getId).toList());
        assertTrue(linkCaptor.getAllValues().stream().allMatch(link -> Long.valueOf(1L).equals(link.getPartyId())
                && link.getPartySnapshot().contains("保留主体") && link.getPartySnapshot().contains("TARGET")));
        verify(contractPartyMapper, never()).deleteById(anyLong());
        verify(partyMapper).updateById(argThat((PartyDO update) -> Long.valueOf(2L).equals(update.getId())
                && CommonStatusEnum.DISABLE.getStatus().equals(update.getStatus())
                && update.getRemark().contains("旧备注") && update.getRemark().endsWith("mergedToPartyId=1")));
        verify(auditService).record(eq(ClmAuditAggregateTypeEnum.PARTY), eq(1L), isNull(),
                eq(ClmAuditActionEnum.PARTY_MERGE), anyMap());
    }

    @Test
    void merge_deduplicatesSameRoleTargetLinkInEditableContract() {
        PartyDO target = party(1L, false, 1, "保留主体", "TARGET");
        PartyDO source = party(2L, false, 1, "重复主体", "SOURCE");
        stubLockedParties(source, target);
        ContractPartyDO sourceLink = link(1000L, 100L, 2L);
        ContractPartyDO targetLink = link(1001L, 100L, 1L);
        stubLinks(2L, List.of(sourceLink), 1L, List.of(targetLink));
        when(contractMapper.selectByIds(eq(Set.of(100L)))).thenReturn(List.of(
                contract(100L, ClmLifecycleStatusEnum.DRAFT.getStatus(),
                        ClmApprovalStatusEnum.NOT_SUBMITTED.getStatus(), "DRAFT")));

        PartyMergeRespVO result = service.merge(req(2L, 1L));

        assertEquals(1, result.getRewrittenContractCount());
        assertEquals(0, result.getRewrittenLinkCount());
        assertEquals(1, result.getDeduplicatedLinkCount());
        verify(contractPartyMapper).deleteById(1000L);
        verify(contractPartyMapper, never()).updateById(any(ContractPartyDO.class));
    }

    @Test
    void merge_repeatedRequestIsSafeAndDoesNotAuditTwice() {
        PartyDO target = party(1L, false, 1, "保留主体", "TARGET")
                .setStatus(CommonStatusEnum.DISABLE.getStatus());
        PartyDO source = party(2L, false, 1, "重复主体", "SOURCE")
                .setStatus(CommonStatusEnum.DISABLE.getStatus()).setRemark("历史备注\nmergedToPartyId=1");
        stubLockedParties(source, target);

        PartyMergeRespVO result = service.merge(req(2L, 1L));

        assertTrue(result.getIdempotent());
        assertEquals(0, result.getRewrittenContractCount());
        verifyNoInteractions(contractPartyMapper, contractMapper, auditService);
        verify(partyMapper, never()).updateById(any(PartyDO.class));
    }

    @Test
    void merge_rejectsSameParty() {
        assertServiceException(() -> service.merge(req(1L, 1L)), PARTY_MERGE_SAME_PARTY);
        verifyNoInteractions(partyMapper);
    }

    @Test
    void merge_rejectsInternalExternalMismatch() {
        PartyDO target = party(1L, true, 1, "我方", "TARGET");
        PartyDO source = party(2L, false, 1, "相对方", "SOURCE");
        stubLockedParties(source, target);

        assertServiceException(() -> service.merge(req(2L, 1L)), PARTY_MERGE_INTERNAL_FLAG_MISMATCH);
        verify(partyMapper, never()).updateById(any(PartyDO.class));
    }

    @Test
    void merge_rejectsCompanyIndividualMismatch() {
        PartyDO target = party(1L, false, 1, "企业", "TARGET");
        PartyDO source = party(2L, false, 2, "个人", "SOURCE");
        stubLockedParties(source, target);

        assertServiceException(() -> service.merge(req(2L, 1L)), PARTY_MERGE_PARTY_TYPE_MISMATCH);
        verify(partyMapper, never()).updateById(any(PartyDO.class));
    }

    @Test
    void merge_rejectsDisabledTarget() {
        PartyDO target = party(1L, false, 1, "停用目标", "TARGET")
                .setStatus(CommonStatusEnum.DISABLE.getStatus());
        PartyDO source = party(2L, false, 1, "源主体", "SOURCE");
        stubLockedParties(source, target);

        assertServiceException(() -> service.merge(req(2L, 1L)), PARTY_MERGE_TARGET_DISABLED);
        verify(partyMapper, never()).updateById(any(PartyDO.class));
    }

    private void stubLockedParties(PartyDO source, PartyDO target) {
        when(partyMapper.selectByIdForUpdate(1L)).thenReturn(target);
        when(partyMapper.selectByIdForUpdate(2L)).thenReturn(source);
    }

    private void stubLinks(Long sourceId, List<ContractPartyDO> sourceLinks,
                           Long targetId, List<ContractPartyDO> targetLinks) {
        when(contractPartyMapper.selectList(
                org.mockito.ArgumentMatchers.<SFunction<ContractPartyDO, ?>>any(), eq(sourceId)))
                .thenReturn(sourceLinks);
        when(contractPartyMapper.selectList(
                org.mockito.ArgumentMatchers.<SFunction<ContractPartyDO, ?>>any(), eq(targetId)))
                .thenReturn(targetLinks);
    }

    private static PartyMergeReqVO req(Long sourceId, Long targetId) {
        PartyMergeReqVO reqVO = new PartyMergeReqVO();
        reqVO.setSourcePartyId(sourceId);
        reqVO.setTargetPartyId(targetId);
        return reqVO;
    }

    private static PartyDO party(Long id, boolean internal, int partyType, String name, String creditCode) {
        return new PartyDO().setId(id).setInternalFlag(internal).setPartyType(partyType).setName(name)
                .setUnifiedCreditCode(creditCode).setStatus(CommonStatusEnum.ENABLE.getStatus()).setRemark("");
    }

    private static ContractPartyDO link(Long id, Long contractId, Long partyId) {
        return new ContractPartyDO().setId(id).setContractId(contractId).setPartyId(partyId)
                .setRoleCode("COUNTERPARTY").setSort(1).setPartySnapshot("{}");
    }

    private static ContractDO contract(Long id, Integer lifecycle, Integer approval, String stage) {
        return new ContractDO().setId(id).setLifecycleStatus(lifecycle).setApprovalStatus(approval).setStageCode(stage);
    }
}
