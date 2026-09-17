package cn.iocoder.yudao.module.clm.template;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.module.clm.document.ClmDocumentStorage;
import cn.iocoder.yudao.module.clm.service.contracttype.ContractTypeService;
import cn.iocoder.yudao.module.clm.service.audit.ClmAuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TemplateServiceTest {
    @InjectMocks private TemplateService service;
    @Mock private TemplateMapper templateMapper;
    @Mock private TemplateVersionMapper versionMapper;
    @Mock private ContractTypeService contractTypeService;
    @Mock private ClmDocumentStorage documentStorage;
    @Mock private ClmAuditService auditService;

    @Test
    void saveDraft_storesNewTemplateFileInPrivateStorage() throws Exception {
        byte[] bytes = "docx-content".getBytes();
        TemplateDraftSaveReqVO req = new TemplateDraftSaveReqVO();
        req.setCode("PURCHASE"); req.setName("采购合同范本"); req.setContractTypeId(10L); req.setRemark("v1");
        req.setFile(new MockMultipartFile("file", "purchase.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", bytes));
        when(templateMapper.selectByCode("PURCHASE")).thenReturn(null);
        when(templateMapper.insert(any(TemplateDO.class))).thenAnswer(invocation -> {
            ((TemplateDO) invocation.getArgument(0)).setId(1L); return 1;
        });
        when(versionMapper.selectDraftByTemplateId(1L)).thenReturn(null);
        when(versionMapper.selectMaxVersionNo(1L)).thenReturn(0);
        when(documentStorage.store(bytes, DigestUtil.sha256Hex(bytes))).thenReturn("blob-1");
        when(versionMapper.insert(any(TemplateVersionDO.class))).thenAnswer(invocation -> {
            ((TemplateVersionDO) invocation.getArgument(0)).setId(2L); return 1;
        });

        assertEquals(2L, service.saveDraft(req));
        verify(versionMapper).insert(argThat((TemplateVersionDO version) -> "DRAFT".equals(version.getStatus())
                && "blob-1".equals(version.getFileKey())
                && DigestUtil.sha256Hex(bytes).equals(version.getChecksumSha256())));
    }

    @Test
    void publish_inactivatesPreviousPublishedVersion() {
        TemplateVersionDO draft = new TemplateVersionDO().setId(2L).setTemplateId(1L)
                .setVersionNo(2).setStatus("DRAFT").setFileKey("blob-2");
        when(versionMapper.selectByIdForUpdate(2L)).thenReturn(draft);
        when(templateMapper.selectByIdForUpdate(1L)).thenReturn(new TemplateDO().setId(1L).setCurrentVersionId(1L).setStatus(1));
        when(versionMapper.selectListByTemplateId(1L)).thenReturn(List.of(
                draft, new TemplateVersionDO().setId(1L).setTemplateId(1L).setStatus("PUBLISHED")));

        service.publish(2L);

        verify(versionMapper).updateById(argThat((TemplateVersionDO row) -> Long.valueOf(1L).equals(row.getId())
                && "INACTIVE".equals(row.getStatus())));
        verify(versionMapper).updateById(argThat((TemplateVersionDO row) -> Long.valueOf(2L).equals(row.getId())
                && "PUBLISHED".equals(row.getStatus()) && row.getPublishedTime() != null));
        verify(templateMapper).updateById(argThat((TemplateDO row) -> Long.valueOf(2L).equals(row.getCurrentVersionId())));
    }
}
