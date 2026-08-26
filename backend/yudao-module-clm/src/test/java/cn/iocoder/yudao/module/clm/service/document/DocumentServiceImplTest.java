package cn.iocoder.yudao.module.clm.service.document;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.clm.access.ContractAccessService;
import cn.iocoder.yudao.module.clm.controller.admin.document.vo.DocumentRespVO;
import cn.iocoder.yudao.module.clm.dal.dataobject.audit.AuditEventDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.document.DocumentBlobDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.document.DocumentDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.document.DocumentVersionDO;
import cn.iocoder.yudao.module.clm.dal.mysql.audit.AuditEventMapper;
import cn.iocoder.yudao.module.clm.dal.mysql.contract.ContractMapper;
import cn.iocoder.yudao.module.clm.dal.mysql.document.DocumentBlobMapper;
import cn.iocoder.yudao.module.clm.dal.mysql.document.DocumentMapper;
import cn.iocoder.yudao.module.clm.dal.mysql.document.DocumentVersionMapper;
import cn.iocoder.yudao.module.clm.document.DbDocumentStorage;
import cn.iocoder.yudao.module.clm.enums.audit.ClmAuditActionEnum;
import cn.iocoder.yudao.module.clm.enums.contract.ClmApprovalStatusEnum;
import cn.iocoder.yudao.module.clm.enums.contract.ClmLifecycleStatusEnum;
import cn.iocoder.yudao.module.clm.enums.document.ClmDocumentRoleEnum;
import cn.iocoder.yudao.module.clm.enums.document.ClmDocumentSourceTypeEnum;
import cn.iocoder.yudao.module.clm.service.audit.ClmAuditServiceImpl;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.filterList;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.clm.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link DocumentServiceImpl} 的单元测试
 */
@Import({DocumentServiceImpl.class, DbDocumentStorage.class, ClmAuditServiceImpl.class})
public class DocumentServiceImplTest extends BaseDbUnitTest {

    @Resource
    private DocumentServiceImpl documentService;

    @Resource
    private ContractMapper contractMapper;
    @Resource
    private DocumentMapper documentMapper;
    @Resource
    private DocumentVersionMapper documentVersionMapper;
    @Resource
    private DocumentBlobMapper documentBlobMapper;
    @Resource
    private AuditEventMapper auditEventMapper;

    @MockitoBean
    private ContractAccessService contractAccessService;
    @MockitoBean
    private AdminUserApi adminUserApi;

    @Test
    public void testUploadDocument_twiceSameName() {
        ContractDO contract = insertContract();
        byte[] content1 = "hello v1".getBytes(StandardCharsets.UTF_8);
        byte[] content2 = "hello v2 changed".getBytes(StandardCharsets.UTF_8);

        Long v1Id = documentService.uploadDocument(contract.getId(), null, "first", "合同.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", content1);
        Long v2Id = documentService.uploadDocument(contract.getId(), ClmDocumentRoleEnum.MAIN.getCode(), "second",
                "合同.docx", null, content2);

        assertNotNull(v1Id);
        assertNotNull(v2Id);
        assertNotEquals(v1Id, v2Id);
        // 权限校验被调用
        verify(contractAccessService, times(2)).assertCanEdit(any(ContractDO.class), any());
        // v1 / v2
        DocumentVersionDO v1 = documentVersionMapper.selectById(v1Id);
        DocumentVersionDO v2 = documentVersionMapper.selectById(v2Id);
        assertEquals(1, v1.getVersionNo());
        assertEquals(2, v2.getVersionNo());
        assertEquals(v1.getDocumentId(), v2.getDocumentId()); // MAIN 唯一 document
        assertEquals(v1Id, v2.getParentVersionId());
        assertNull(v1.getParentVersionId());
        assertEquals(DigestUtil.sha256Hex(content1), v1.getChecksumSha256());
        assertEquals(DigestUtil.sha256Hex(content2), v2.getChecksumSha256());
        assertEquals(content1.length, v1.getFileSize());
        assertEquals("合同.docx", v1.getFileName());
        assertEquals(ClmDocumentSourceTypeEnum.UPLOAD.getCode(), v1.getSourceType());
        assertFalse(v1.getFrozen());
        assertEquals(DocumentServiceImpl.DEFAULT_MIME_TYPE, v2.getMimeType());
        assertEquals("first", v1.getRemark());
        // document / contract 指针
        DocumentDO document = documentMapper.selectById(v1.getDocumentId());
        assertEquals(ClmDocumentRoleEnum.MAIN.getCode(), document.getRoleCode());
        assertEquals(v2Id, document.getCurrentVersionId());
        assertEquals(1, documentMapper.selectListByContractId(contract.getId()).size());
        assertEquals(v2Id, contractMapper.selectById(contract.getId()).getCurrentDocumentVersionId());
        // blob 内容
        DocumentBlobDO blob1 = documentBlobMapper.selectById(Long.parseLong(v1.getFileKey()));
        assertArrayEquals(content1, blob1.getContent());
        assertEquals(DigestUtil.sha256Hex(content1), blob1.getSha256());
        // 审计
        List<AuditEventDO> uploads = filterList(auditEventMapper.selectList(),
                e -> ClmAuditActionEnum.DOCUMENT_UPLOAD.getCode().equals(e.getAction()));
        assertEquals(2, uploads.size());
        assertEquals(contract.getId(), uploads.get(0).getContractId());
    }

    @Test
    public void testUploadDocument_frozenVersionNotOverwritten() {
        ContractDO contract = insertContract();
        byte[] content1 = "frozen content".getBytes(StandardCharsets.UTF_8);
        Long v1Id = documentService.uploadDocument(contract.getId(), null, null, "a.pdf", "application/pdf", content1);
        // 冻结 v1（提交审批即冻结）
        documentService.freezeDocumentVersion(v1Id);
        DocumentVersionDO frozen = documentVersionMapper.selectById(v1Id);
        assertTrue(frozen.getFrozen());
        documentService.freezeDocumentVersion(v1Id); // 幂等

        // 再次上传同名 → 新版本，而不是覆盖
        byte[] content2 = "new content".getBytes(StandardCharsets.UTF_8);
        Long v2Id = documentService.uploadDocument(contract.getId(), null, null, "a.pdf", "application/pdf", content2);

        assertNotEquals(v1Id, v2Id);
        DocumentVersionDO v1 = documentVersionMapper.selectById(v1Id);
        assertTrue(v1.getFrozen());
        assertEquals(DigestUtil.sha256Hex(content1), v1.getChecksumSha256());
        assertEquals(frozen.getFileKey(), v1.getFileKey());
        assertArrayEquals(content1, documentBlobMapper.selectById(Long.parseLong(v1.getFileKey())).getContent());
        DocumentVersionDO v2 = documentVersionMapper.selectById(v2Id);
        assertFalse(v2.getFrozen());
        assertEquals(2, v2.getVersionNo());
        assertEquals(v2Id, contractMapper.selectById(contract.getId()).getCurrentDocumentVersionId());
    }

    @Test
    public void testUploadDocument_attachmentCreatesNewDocumentEachTime() {
        ContractDO contract = insertContract();
        Long a1 = documentService.uploadDocument(contract.getId(), ClmDocumentRoleEnum.ATTACHMENT.getCode(), null,
                "附件1.zip", "application/zip", "zip1".getBytes(StandardCharsets.UTF_8));
        Long a2 = documentService.uploadDocument(contract.getId(), ClmDocumentRoleEnum.ATTACHMENT.getCode(), null,
                "附件2.png", "image/png", "png".getBytes(StandardCharsets.UTF_8));

        DocumentVersionDO va1 = documentVersionMapper.selectById(a1);
        DocumentVersionDO va2 = documentVersionMapper.selectById(a2);
        assertNotEquals(va1.getDocumentId(), va2.getDocumentId());
        assertEquals(1, va1.getVersionNo());
        assertEquals(1, va2.getVersionNo());
        assertEquals("附件1.zip", documentMapper.selectById(va1.getDocumentId()).getName());
        // 附件不更新合同正文指针
        assertNull(contractMapper.selectById(contract.getId()).getCurrentDocumentVersionId());
        // 列表
        List<DocumentRespVO> list = documentService.getDocumentList(contract.getId());
        assertEquals(2, list.size());
        assertEquals(1, list.get(0).getVersions().size());
    }

    @Test
    public void testUploadDocument_validation() {
        ContractDO contract = insertContract();
        byte[] content = "x".getBytes(StandardCharsets.UTF_8);
        // 扩展名不在白名单
        assertServiceException(() -> documentService.uploadDocument(contract.getId(), null, null, "evil.exe", null, content),
                DOCUMENT_FILE_TYPE_NOT_ALLOWED, "exe");
        // 空文件
        assertServiceException(() -> documentService.uploadDocument(contract.getId(), null, null, "a.txt", null, new byte[0]),
                DOCUMENT_FILE_EMPTY);
        // 合同不存在
        assertServiceException(() -> documentService.uploadDocument(99999L, null, null, "a.txt", null, content),
                CONTRACT_NOT_EXISTS);
        // 审批中锁定
        contractMapper.updateById(new ContractDO().setId(contract.getId())
                .setApprovalStatus(ClmApprovalStatusEnum.RUNNING.getStatus()));
        assertServiceException(() -> documentService.uploadDocument(contract.getId(), null, null, "a.txt", null, content),
                CONTRACT_LOCKED_BY_APPROVAL);
        // 无权限
        ContractDO contract2 = insertContract();
        doThrow(accessDenied()).when(contractAccessService).assertCanEdit(any(), any());
        assertServiceException(() -> documentService.uploadDocument(contract2.getId(), null, null, "a.txt", null, content),
                CONTRACT_ACCESS_DENIED);
        assertTrue(documentVersionMapper.selectListByContractId(contract2.getId()).isEmpty());
    }

    @Test
    public void testDownloadDocumentVersion() {
        ContractDO contract = insertContract();
        byte[] content = "download me".getBytes(StandardCharsets.UTF_8);
        Long v1Id = documentService.uploadDocument(contract.getId(), null, null, "d.txt", "text/plain", content);

        DocumentDownloadResult result = documentService.downloadDocumentVersion(v1Id);

        assertArrayEquals(content, result.getContent());
        assertEquals("d.txt", result.getVersion().getFileName());
        verify(contractAccessService).assertCanDownload(any(ContractDO.class), any());
        List<AuditEventDO> downloads = filterList(auditEventMapper.selectList(),
                e -> ClmAuditActionEnum.DOCUMENT_DOWNLOAD.getCode().equals(e.getAction()));
        assertEquals(1, downloads.size());
        assertEquals(result.getVersion().getDocumentId(), downloads.get(0).getAggregateId());
        // 不存在
        assertServiceException(() -> documentService.downloadDocumentVersion(99999L), DOCUMENT_VERSION_NOT_EXISTS);
        // 无权限
        doThrow(accessDenied()).when(contractAccessService).assertCanDownload(any(), any());
        assertServiceException(() -> documentService.downloadDocumentVersion(v1Id), CONTRACT_ACCESS_DENIED);
    }

    // ========== 构造对象 ==========

    private ContractDO insertContract() {
        ContractDO contract = new ContractDO()
                .setTitle("测试合同")
                .setTypeId(1L)
                .setTypeVersionId(1L)
                .setOwnerUserId(1L)
                .setCurrency("CNY")
                .setLifecycleStatus(ClmLifecycleStatusEnum.DRAFT.getStatus())
                .setApprovalStatus(ClmApprovalStatusEnum.NOT_SUBMITTED.getStatus());
        contractMapper.insert(contract);
        return contract;
    }

    private static ServiceException accessDenied() {
        return exception(CONTRACT_ACCESS_DENIED);
    }

}
