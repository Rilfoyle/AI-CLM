package cn.iocoder.yudao.module.clm.service.document;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.clm.access.ContractAccessService;
import cn.iocoder.yudao.module.clm.dal.dataobject.audit.AuditEventDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractDO;
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
import cn.iocoder.yudao.module.clm.enums.document.ClmDocumentSourceTypeEnum;
import cn.iocoder.yudao.module.clm.service.audit.ClmAuditServiceImpl;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.filterList;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.clm.enums.ErrorCodeConstants.DOCUMENT_FILE_EMPTY;
import static cn.iocoder.yudao.module.clm.enums.ErrorCodeConstants.DOCUMENT_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link DocumentServiceImpl#createVersionFromBytes} 的单元测试（ONLYOFFICE 回调保存路径）
 */
@Import({DocumentServiceImpl.class, DbDocumentStorage.class, ClmAuditServiceImpl.class})
public class DocumentServiceCreateVersionFromBytesTest extends BaseDbUnitTest {

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
    public void testCreateVersionFromBytes_onlineEditSave() {
        ContractDO contract = insertContract();
        byte[] content1 = "online v1".getBytes(StandardCharsets.UTF_8);
        Long v1Id = documentService.uploadDocument(contract.getId(), null, "v1", "合同.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", content1);
        DocumentVersionDO v1 = documentVersionMapper.selectById(v1Id);
        AdminUserRespDTO user = new AdminUserRespDTO();
        user.setId(7L);
        user.setNickname("编辑者");
        when(adminUserApi.getUser(7L)).thenReturn(user);
        // 审批中也允许（回调是对已开启会话的收尾）
        contractMapper.updateById(new ContractDO().setId(contract.getId())
                .setApprovalStatus(ClmApprovalStatusEnum.RUNNING.getStatus()));

        byte[] content2 = "online v2 saved by document server".getBytes(StandardCharsets.UTF_8);
        Long v2Id = documentService.createVersionFromBytes(contract.getId(), v1.getDocumentId(), v1Id,
                v1.getFileName(), v1.getMimeType(), content2, ClmDocumentSourceTypeEnum.ONLINE_EDIT,
                "ONLYOFFICE 保存 (status=2)", 7L, Collections.singletonMap("lateSave", true));

        // 不做编辑 ACL 校验（只有 upload 调过一次）
        verify(contractAccessService, times(1)).assertCanEdit(any(ContractDO.class), any());
        DocumentVersionDO v2 = documentVersionMapper.selectById(v2Id);
        assertEquals(2, v2.getVersionNo());
        assertEquals(v1Id, v2.getParentVersionId());
        assertEquals(v1.getDocumentId(), v2.getDocumentId());
        assertEquals(ClmDocumentSourceTypeEnum.ONLINE_EDIT.getCode(), v2.getSourceType());
        assertEquals(DigestUtil.sha256Hex(content2), v2.getChecksumSha256());
        assertEquals(content2.length, v2.getFileSize());
        assertEquals("合同.docx", v2.getFileName());
        assertEquals(v1.getMimeType(), v2.getMimeType());
        assertEquals("ONLYOFFICE 保存 (status=2)", v2.getRemark());
        assertFalse(v2.getFrozen());
        assertEquals("7", v2.getCreator());
        assertArrayEquals(content2, documentBlobMapper.selectById(Long.parseLong(v2.getFileKey())).getContent());
        // 指针
        DocumentDO document = documentMapper.selectById(v1.getDocumentId());
        assertEquals(v2Id, document.getCurrentVersionId());
        assertEquals(v2Id, contractMapper.selectById(contract.getId()).getCurrentDocumentVersionId());
        // 审计：ONLINE_EDIT_SAVE，actor = 令牌用户
        List<AuditEventDO> saves = filterList(auditEventMapper.selectList(),
                e -> ClmAuditActionEnum.ONLINE_EDIT_SAVE.getCode().equals(e.getAction()));
        assertEquals(1, saves.size());
        AuditEventDO save = saves.get(0);
        assertEquals(7L, save.getActorUserId());
        assertEquals("编辑者", save.getActorName());
        assertEquals(contract.getId(), save.getContractId());
        assertEquals(v1.getDocumentId(), save.getAggregateId());
        assertTrue(save.getDetailJson().contains("\"lateSave\":true"));
        assertTrue(save.getDetailJson().contains("\"parentVersionId\":" + v1Id));
    }

    @Test
    public void testCreateVersionFromBytes_validation() {
        ContractDO contract = insertContract();
        Long v1Id = documentService.uploadDocument(contract.getId(), null, null, "a.docx", null,
                "x".getBytes(StandardCharsets.UTF_8));
        DocumentVersionDO v1 = documentVersionMapper.selectById(v1Id);
        // 文档不存在 / 不属于该合同
        assertServiceException(() -> documentService.createVersionFromBytes(contract.getId(), 99999L, v1Id,
                "a.docx", null, "y".getBytes(StandardCharsets.UTF_8), ClmDocumentSourceTypeEnum.ONLINE_EDIT,
                null, 1L, null), DOCUMENT_NOT_EXISTS);
        ContractDO other = insertContract();
        assertServiceException(() -> documentService.createVersionFromBytes(other.getId(), v1.getDocumentId(), v1Id,
                "a.docx", null, "y".getBytes(StandardCharsets.UTF_8), ClmDocumentSourceTypeEnum.ONLINE_EDIT,
                null, 1L, null), DOCUMENT_NOT_EXISTS);
        // 空内容
        assertServiceException(() -> documentService.createVersionFromBytes(contract.getId(), v1.getDocumentId(), v1Id,
                "a.docx", null, new byte[0], ClmDocumentSourceTypeEnum.ONLINE_EDIT, null, 1L, null),
                DOCUMENT_FILE_EMPTY);
        assertEquals(1, documentVersionMapper.selectListByContractId(contract.getId()).size());
    }

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

}
