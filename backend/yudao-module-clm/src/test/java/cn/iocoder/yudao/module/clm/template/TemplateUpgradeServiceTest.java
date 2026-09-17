package cn.iocoder.yudao.module.clm.template;

import cn.iocoder.yudao.module.clm.access.ContractAccessService;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.document.DocumentDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.document.DocumentVersionDO;
import cn.iocoder.yudao.module.clm.dal.mysql.contract.ContractMapper;
import cn.iocoder.yudao.module.clm.dal.mysql.document.DocumentMapper;
import cn.iocoder.yudao.module.clm.document.ClmDocumentStorage;
import cn.iocoder.yudao.module.clm.enums.document.ClmDocumentSourceTypeEnum;
import cn.iocoder.yudao.module.clm.revision.ContractRevisionDO;
import cn.iocoder.yudao.module.clm.revision.ContractRevisionMapper;
import cn.iocoder.yudao.module.clm.revision.ContractRevisionService;
import cn.iocoder.yudao.module.clm.service.document.DocumentService;
import cn.iocoder.yudao.module.clm.service.audit.ClmAuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TemplateUpgradeServiceTest {
    @InjectMocks private TemplateUpgradeService service;
    @Mock private ContractMapper contractMapper;
    @Mock private ContractAccessService contractAccessService;
    @Mock private ContractRevisionMapper revisionMapper;
    @Mock private ContractRevisionService revisionService;
    @Mock private TemplateMapper templateMapper;
    @Mock private TemplateVersionMapper versionMapper;
    @Mock private DocumentService documentService;
    @Mock private DocumentMapper documentMapper;
    @Mock private ClmDocumentStorage documentStorage;
    @Mock private ClmAuditService auditService;

    @Test
    void upgrade_explicitlyCreatesDocumentAndRevisionAgainstBase() {
        TemplateUpgradeReqVO req = new TemplateUpgradeReqVO();
        req.setContractId(1L); req.setBaseRevisionId(10L); req.setTargetTemplateVersionId(101L);
        ContractDO contract = new ContractDO().setId(1L).setSourceMode("TEMPLATE")
                .setCurrentRevisionId(10L).setCurrentDocumentVersionId(20L);
        when(contractMapper.selectByIdForUpdate(1L)).thenReturn(contract);
        when(revisionMapper.selectById(10L)).thenReturn(new ContractRevisionDO().setId(10L).setTemplateVersionId(100L));
        when(versionMapper.selectById(100L)).thenReturn(new TemplateVersionDO().setId(100L)
                .setTemplateId(50L).setVersionNo(1));
        when(versionMapper.selectById(101L)).thenReturn(new TemplateVersionDO().setId(101L)
                .setTemplateId(50L).setVersionNo(2).setStatus("PUBLISHED").setFileKey("blob-2")
                .setFileName("v2.docx").setMimeType("application/docx"));
        when(templateMapper.selectById(50L)).thenReturn(new TemplateDO().setId(50L).setStatus(1).setCurrentVersionId(101L));
        when(documentService.getRequiredDocumentVersion(20L)).thenReturn(new DocumentVersionDO()
                .setId(20L).setDocumentId(30L).setContractId(1L));
        when(documentStorage.load("blob-2")).thenReturn(new byte[]{1, 2});
        when(documentService.createVersionFromBytes(eq(1L), eq(30L), eq(20L), eq("v2.docx"),
                eq("application/docx"), any(byte[].class), eq(ClmDocumentSourceTypeEnum.UPLOAD),
                anyString(), isNull(), anyMap(), eq(false))).thenReturn(21L);
        when(revisionService.createSnapshot(1L, 10L, "TEMPLATE_UPGRADE", "升级至范本 v2")).thenReturn(11L);

        TemplateUpgradeRespVO result = service.upgrade(req);

        assertEquals(11L, result.getRevisionId());
        assertEquals(21L, result.getDocumentVersionId());
        verify(documentMapper).updateById(argThat((DocumentDO row) -> Long.valueOf(21L).equals(row.getCurrentVersionId())));
        verify(contractMapper).updateById(argThat((ContractDO row) -> Long.valueOf(21L).equals(row.getCurrentDocumentVersionId())));
        verify(revisionMapper).updateById(argThat((ContractRevisionDO row) -> Long.valueOf(101L).equals(row.getTemplateVersionId())));
    }
}
