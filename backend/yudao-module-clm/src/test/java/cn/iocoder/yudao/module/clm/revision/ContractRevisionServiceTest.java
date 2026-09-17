package cn.iocoder.yudao.module.clm.revision;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.clm.access.ContractAccessService;
import cn.iocoder.yudao.module.clm.commitment.CommitmentMapper;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.party.PartyDO;
import cn.iocoder.yudao.module.clm.dal.mysql.contract.ContractMapper;
import cn.iocoder.yudao.module.clm.dal.mysql.contract.ContractPartyMapper;
import cn.iocoder.yudao.module.clm.dal.mysql.party.PartyMapper;
import cn.iocoder.yudao.module.clm.dal.mysql.document.DocumentMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static cn.iocoder.yudao.module.clm.enums.ErrorCodeConstants.CONTRACT_REVISION_CONFLICT;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContractRevisionServiceTest {
    @InjectMocks private ContractRevisionServiceImpl service;
    @Mock private ContractRevisionMapper revisionMapper;
    @Mock private ContractMapper contractMapper;
    @Mock private ContractPartyMapper contractPartyMapper;
    @Mock private PartyMapper partyMapper;
    @Mock private DocumentMapper documentMapper;
    @Mock private CommitmentMapper commitmentMapper;
    @Mock private ContractAccessService contractAccessService;

    @Test
    void createSnapshot_rejectsStaleBase() {
        when(contractMapper.selectByIdForUpdate(1L))
                .thenReturn(new ContractDO().setId(1L).setCurrentRevisionId(10L));
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.createSnapshot(1L, 9L, "MANUAL_EDIT", "stale"));
        assertEquals(CONTRACT_REVISION_CONFLICT.getCode(), ex.getCode());
        verify(revisionMapper, never()).insert(any(ContractRevisionDO.class));
    }

    @Test
    void createInitialRevision_insertsImmutableSnapshotAndAdvancesPointer() {
        ContractDO contract = new ContractDO().setId(1L).setTitle("采购合同").setTypeId(2L)
                .setTypeVersionId(3L).setOwnerUserId(4L).setCurrency("CNY").setSourceMode("UPLOAD");
        when(contractMapper.selectByIdForUpdate(1L)).thenReturn(contract);
        when(contractPartyMapper.selectListByContractId(1L)).thenReturn(Collections.emptyList());
        when(commitmentMapper.selectListByContractId(1L)).thenReturn(Collections.emptyList());
        when(documentMapper.selectListByContractId(1L)).thenReturn(Collections.emptyList());
        when(revisionMapper.insert(any(ContractRevisionDO.class))).thenAnswer(invocation -> {
            ((ContractRevisionDO) invocation.getArgument(0)).setId(99L); return 1;
        });

        assertEquals(99L, service.createInitialRevision(1L, 8L, "UPLOAD_CREATE"));
        verify(revisionMapper).insert(argThat((ContractRevisionDO row) -> row.getRevisionNo() == 1
                && Long.valueOf(8L).equals(row.getTemplateVersionId())
                && "UPLOAD_CREATE".equals(row.getChangeSource())));
        verify(contractMapper).updateById(argThat((ContractDO row) -> Long.valueOf(99L).equals(row.getCurrentRevisionId())));
    }

    @Test
    void save_inheritsTemplateProvenanceWhenDocumentBodyIsUnchanged() {
        ContractDO contract = new ContractDO().setId(1L).setCurrentRevisionId(10L).setTitle("采购合同")
                .setTypeId(2L).setTypeVersionId(3L).setOwnerUserId(4L).setCurrency("CNY")
                .setSourceMode("TEMPLATE");
        ContractRevisionDO baseRevision = new ContractRevisionDO().setId(10L).setContractId(1L)
                .setRevisionNo(1).setTemplateVersionId(8L);
        when(contractMapper.selectByIdForUpdate(1L)).thenReturn(contract);
        when(contractMapper.selectById(1L)).thenReturn(contract);
        when(revisionMapper.selectById(10L)).thenReturn(baseRevision);
        when(revisionMapper.selectLatest(1L)).thenReturn(baseRevision);
        when(partyMapper.selectByIds(any())).thenReturn(List.of(
                new PartyDO().setId(11L).setName("我方主体"),
                new PartyDO().setId(12L).setName("相对方")));
        when(contractPartyMapper.selectListByContractId(1L)).thenReturn(Collections.emptyList());
        when(commitmentMapper.selectListByContractId(1L)).thenReturn(Collections.emptyList());
        when(documentMapper.selectListByContractId(1L)).thenReturn(Collections.emptyList());
        when(revisionMapper.insert(any(ContractRevisionDO.class))).thenAnswer(invocation -> {
            ((ContractRevisionDO) invocation.getArgument(0)).setId(99L); return 1;
        });
        when(revisionMapper.selectById(99L)).thenReturn(new ContractRevisionDO().setId(99L).setRevisionNo(2));
        ContractRevisionSaveReqVO req = new ContractRevisionSaveReqVO();
        req.setContractId(1L); req.setBaseRevisionId(10L); req.setName("采购合同");
        req.setCurrency("CNY"); req.setOurPartyId(11L); req.setCounterpartyIds(List.of(12L));
        req.setChangeReason("补充结构化字段");

        ContractRevisionSaveRespVO result = service.save(req);

        assertEquals(99L, result.getRevisionId());
        assertEquals(2, result.getRevisionNo());
        verify(revisionMapper).insert(argThat((ContractRevisionDO row) -> Long.valueOf(8L).equals(row.getTemplateVersionId())
                && Long.valueOf(10L).equals(row.getBaseRevisionId())
                && "MANUAL_EDIT".equals(row.getChangeSource())));
    }

    @Test
    void createSnapshot_inheritsTemplateProvenanceForCommitmentChange() {
        stubExistingTemplateSnapshot();

        assertEquals(99L, service.createSnapshot(1L, 10L, "COMMITMENT_NONE", "确认无重大承诺"));

        verify(revisionMapper).insert(argThat((ContractRevisionDO row) -> Long.valueOf(8L).equals(row.getTemplateVersionId())
                && "COMMITMENT_NONE".equals(row.getChangeSource())));
    }

    @Test
    void createSnapshot_clearsTemplateProvenanceForDocumentChange() {
        stubExistingTemplateSnapshot();

        assertEquals(99L, service.createSnapshot(1L, 10L, "DOCUMENT_UPLOAD", "上传新正文"));

        verify(revisionMapper).insert(argThat((ContractRevisionDO row) -> row.getTemplateVersionId() == null
                && "DOCUMENT_UPLOAD".equals(row.getChangeSource())));
    }

    @Test
    void createSnapshot_explicitActorSupportsAuthenticatedDocumentCallback() {
        stubExistingTemplateSnapshot();

        assertEquals(99L, service.createSnapshot(1L, 10L, "ONLINE_EDIT", "ONLYOFFICE 保存", 7L));

        verify(revisionMapper).insert(argThat((ContractRevisionDO row) -> "7".equals(row.getCreator())
                && "7".equals(row.getUpdater()) && row.getTemplateVersionId() == null));
        verify(contractMapper).updateById(argThat((ContractDO row) -> "7".equals(row.getUpdater())
                && Long.valueOf(99L).equals(row.getCurrentRevisionId())));
    }

    private void stubExistingTemplateSnapshot() {
        ContractDO contract = new ContractDO().setId(1L).setCurrentRevisionId(10L).setTitle("采购合同")
                .setTypeId(2L).setTypeVersionId(3L).setCurrentDocumentVersionId(20L);
        ContractRevisionDO baseRevision = new ContractRevisionDO().setId(10L).setContractId(1L)
                .setRevisionNo(1).setTemplateVersionId(8L).setMainDocumentVersionId(20L);
        when(contractMapper.selectByIdForUpdate(1L)).thenReturn(contract);
        when(revisionMapper.selectById(10L)).thenReturn(baseRevision);
        when(revisionMapper.selectLatest(1L)).thenReturn(baseRevision);
        when(contractPartyMapper.selectListByContractId(1L)).thenReturn(Collections.emptyList());
        when(commitmentMapper.selectListByContractId(1L)).thenReturn(Collections.emptyList());
        when(documentMapper.selectListByContractId(1L)).thenReturn(Collections.emptyList());
        when(revisionMapper.insert(any(ContractRevisionDO.class))).thenAnswer(invocation -> {
            ((ContractRevisionDO) invocation.getArgument(0)).setId(99L); return 1;
        });
    }
}
