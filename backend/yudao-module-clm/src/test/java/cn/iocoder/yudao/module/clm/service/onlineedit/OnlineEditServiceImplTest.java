package cn.iocoder.yudao.module.clm.service.onlineedit;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.clm.access.ContractAccessService;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.document.DocumentDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.document.DocumentVersionDO;
import cn.iocoder.yudao.module.clm.dal.mysql.document.DocumentMapper;
import cn.iocoder.yudao.module.clm.enums.contract.ClmApprovalStatusEnum;
import cn.iocoder.yudao.module.clm.enums.document.ClmDocumentSourceTypeEnum;
import cn.iocoder.yudao.module.clm.document.ClmDocumentStorage;
import cn.iocoder.yudao.module.clm.framework.onlyoffice.config.ClmOnlyOfficeProperties;
import cn.iocoder.yudao.module.clm.onlyoffice.OnlyOfficeTokenPayload;
import cn.iocoder.yudao.module.clm.onlyoffice.OnlyOfficeTokenService;
import cn.iocoder.yudao.module.clm.service.contract.ContractService;
import cn.iocoder.yudao.module.clm.service.document.DocumentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.clm.enums.ErrorCodeConstants.CONTRACT_ACCESS_DENIED;
import static cn.iocoder.yudao.module.clm.enums.ErrorCodeConstants.ONLINE_EDIT_CALLBACK_FETCH_FAILED;
import static cn.iocoder.yudao.module.clm.enums.ErrorCodeConstants.ONLINE_EDIT_TOKEN_INVALID;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@link OnlineEditServiceImpl} 的安全边界单元测试（无 DB / Spring 容器）。
 */
public class OnlineEditServiceImplTest extends BaseMockitoUnitTest {

    private OnlineEditServiceImpl onlineEditService;
    private ClmOnlyOfficeProperties properties;

    @Mock
    private HttpClient httpClient;
    @Mock
    private HttpResponse<byte[]> httpResponse;
    @Mock
    private OnlyOfficeTokenService onlyOfficeTokenService;
    @Mock
    private DocumentService documentService;
    @Mock
    private ContractService contractService;
    @Mock
    private ContractAccessService contractAccessService;
    @Mock
    private ClmDocumentStorage documentStorage;
    @Mock
    private DocumentMapper documentMapper;

    @BeforeEach
    public void setUp() {
        properties = new ClmOnlyOfficeProperties();
        properties.setEnabled(true);
        properties.setDocumentServerUrl("http://127.0.0.1:8090");
        properties.setJwtSecret("");
        onlineEditService = spy(new OnlineEditServiceImpl(httpClient));
        ReflectionTestUtils.setField(onlineEditService, "properties", properties);
        ReflectionTestUtils.setField(onlineEditService, "onlyOfficeTokenService", onlyOfficeTokenService);
        ReflectionTestUtils.setField(onlineEditService, "documentService", documentService);
        ReflectionTestUtils.setField(onlineEditService, "contractService", contractService);
        ReflectionTestUtils.setField(onlineEditService, "contractAccessService", contractAccessService);
        ReflectionTestUtils.setField(onlineEditService, "documentStorage", documentStorage);
        ReflectionTestUtils.setField(onlineEditService, "documentMapper", documentMapper);
    }

    @AfterEach
    public void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    public void testHandleCallback_keyMismatch_rejectedBeforeDownload() {
        OnlyOfficeTokenPayload payload = newPayload(OnlyOfficeTokenPayload.PURPOSE_CALLBACK);
        DocumentVersionDO version = newVersion();
        when(onlyOfficeTokenService.verify("callback-token", OnlyOfficeTokenPayload.PURPOSE_CALLBACK))
                .thenReturn(payload);
        when(onlyOfficeTokenService.isJwtEnabled()).thenReturn(false);
        when(documentService.getRequiredDocumentVersion(11L)).thenReturn(version);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 2);
        body.put("key", "clm-v999-forged");
        body.put("url", "http://127.0.0.1:8090/cache/edited.docx");

        assertServiceException(() -> onlineEditService.handleCallback("callback-token", body, null),
                ONLINE_EDIT_TOKEN_INVALID);

        verifyNoInteractions(httpClient);
        verify(documentService).getRequiredDocumentVersion(11L);
    }

    @Test
    public void testDownloadContent_ssrfOriginsRejectedBeforeRequest() {
        List<String> invalidUrls = List.of(
                "file:///etc/passwd",
                "https://127.0.0.1:8090/cache/edited.docx",
                "http://evil.example:8090/cache/edited.docx",
                "http://user@127.0.0.1:8090/cache/edited.docx",
                "http://127.0.0.1:8091/cache/edited.docx",
                "http://127.0.0.1:8090/cache/edited.docx#fragment");

        for (String invalidUrl : invalidUrls) {
            assertServiceException(() -> onlineEditService.downloadContent(invalidUrl, 11L),
                    ONLINE_EDIT_CALLBACK_FETCH_FAILED);
        }

        verifyNoInteractions(httpClient);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void testDownloadContent_redirectRejectedAndClientNeverFollows() throws Exception {
        when(httpResponse.statusCode()).thenReturn(302);
        doReturn(CompletableFuture.completedFuture(httpResponse)).when(httpClient)
                .sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        assertServiceException(() -> onlineEditService.downloadContent(
                "http://127.0.0.1:8090/cache/redirect", 11L), ONLINE_EDIT_CALLBACK_FETCH_FAILED);

        assertEquals(HttpClient.Redirect.NEVER, OnlineEditServiceImpl.buildCallbackHttpClient().followRedirects());
        verify(httpClient).sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void testDownloadContent_declaredOversizeRejected() throws Exception {
        HttpHeaders headers = HttpHeaders.of(Map.of(
                "Content-Type", List.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
                "Content-Length", List.of(String.valueOf(OnlineEditServiceImpl.MAX_DOWNLOAD_BYTES + 1))),
                (name, value) -> true);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.headers()).thenReturn(headers);
        doReturn(CompletableFuture.completedFuture(httpResponse)).when(httpClient)
                .sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        assertServiceException(() -> onlineEditService.downloadContent(
                "http://127.0.0.1:8090/cache/oversize", 11L), ONLINE_EDIT_CALLBACK_FETCH_FAILED);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void testDownloadContent_sameOriginSuccess() throws Exception {
        byte[] edited = "edited-docx-bytes".getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = HttpHeaders.of(Map.of(
                "Content-Type", List.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
                "Content-Length", List.of(String.valueOf(edited.length))), (name, value) -> true);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.headers()).thenReturn(headers);
        when(httpResponse.body()).thenReturn(edited);
        doReturn(CompletableFuture.completedFuture(httpResponse)).when(httpClient)
                .sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        byte[] result = onlineEditService.downloadContent(
                "http://127.0.0.1:8090/cache/edited.docx?token=server", 11L);

        assertArrayEquals(edited, result);
    }

    @Test
    public void testLoadFile_permissionRevoked_rejectedImmediately() {
        OnlyOfficeTokenPayload payload = newPayload(OnlyOfficeTokenPayload.PURPOSE_FILE);
        DocumentVersionDO version = newVersion();
        ContractDO contract = new ContractDO().setId(33L);
        when(onlyOfficeTokenService.verify("file-token", OnlyOfficeTokenPayload.PURPOSE_FILE)).thenReturn(payload);
        when(documentService.getRequiredDocumentVersion(11L)).thenReturn(version);
        when(contractService.getRequiredContract(33L)).thenReturn(contract);
        doThrow(exception(CONTRACT_ACCESS_DENIED)).when(contractAccessService).assertCanView(eq(contract), eq(7L));

        assertServiceException(() -> onlineEditService.loadFile("file-token"), CONTRACT_ACCESS_DENIED);

        verify(contractAccessService).assertCanView(contract, 7L);
        verifyNoInteractions(documentStorage);
    }

    @Test
    public void testHandleCallback_lateSaveKeepsCurrentWorkingBaseline() {
        byte[] edited = "late-edited-content".getBytes(StandardCharsets.UTF_8);
        OnlyOfficeTokenPayload payload = newPayload(OnlyOfficeTokenPayload.PURPOSE_CALLBACK);
        DocumentVersionDO parent = newVersion().setFrozen(false).setMimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        DocumentDO document = new DocumentDO().setId(22L).setContractId(33L).setCurrentVersionId(11L);
        when(onlyOfficeTokenService.verify("callback-token", OnlyOfficeTokenPayload.PURPOSE_CALLBACK))
                .thenReturn(payload);
        when(onlyOfficeTokenService.isJwtEnabled()).thenReturn(false);
        when(documentService.getRequiredDocumentVersion(11L)).thenReturn(parent);
        when(documentMapper.selectById(22L)).thenReturn(document);
        when(documentService.getDocumentVersion(11L)).thenReturn(parent);
        when(contractService.getRequiredContract(33L)).thenReturn(new ContractDO().setId(33L)
                .setApprovalStatus(ClmApprovalStatusEnum.RUNNING.getStatus()));
        doReturn(edited).when(onlineEditService).downloadContent("http://127.0.0.1:8090/cache/edited.docx", 11L);
        when(documentService.createVersionFromBytes(eq(33L), eq(22L), eq(11L), eq("合同.docx"), any(),
                eq(edited), eq(ClmDocumentSourceTypeEnum.ONLINE_EDIT), any(), eq(7L), any(), eq(false)))
                .thenReturn(12L);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 2);
        body.put("key", OnlineEditServiceImpl.buildDocumentKey(parent));
        body.put("url", "http://127.0.0.1:8090/cache/edited.docx");

        Long versionId = onlineEditService.handleCallback("callback-token", body, null);

        assertEquals(12L, versionId);
        verify(documentService).createVersionFromBytes(eq(33L), eq(22L), eq(11L), eq("合同.docx"), any(),
                eq(edited), eq(ClmDocumentSourceTypeEnum.ONLINE_EDIT), any(), eq(7L), any(), eq(false));
    }

    @Test
    public void testLogicalOfficeContent_sameEntriesDifferentZipMetadata_equal() throws IOException {
        Map<String, String> firstEntries = new LinkedHashMap<>();
        firstEntries.put("[Content_Types].xml", "<Types/>");
        firstEntries.put("word/document.xml", "<w:document>same text</w:document>");
        Map<String, String> secondEntries = new LinkedHashMap<>();
        secondEntries.put("word/document.xml", "<w:document>same text</w:document>");
        secondEntries.put("[Content_Types].xml", "<Types/>");
        byte[] first = zip(firstEntries, 1_000L, Deflater.BEST_SPEED);
        byte[] second = zip(secondEntries, 10_000L, Deflater.BEST_COMPRESSION);

        assertFalse(java.util.Arrays.equals(first, second));
        assertTrue(OnlineEditServiceImpl.hasSameLogicalOfficeContent("contract.docx", first, second));
    }

    @Test
    public void testLogicalOfficeContent_changedEntry_notEqual() throws IOException {
        Map<String, String> beforeEntries = new LinkedHashMap<>();
        beforeEntries.put("[Content_Types].xml", "<Types/>");
        beforeEntries.put("word/document.xml", "<w:document>before</w:document>");
        Map<String, String> afterEntries = new LinkedHashMap<>();
        afterEntries.put("[Content_Types].xml", "<Types/>");
        afterEntries.put("word/document.xml", "<w:document>after</w:document>");

        assertFalse(OnlineEditServiceImpl.hasSameLogicalOfficeContent("contract.docx",
                zip(beforeEntries, 1_000L, Deflater.DEFAULT_COMPRESSION),
                zip(afterEntries, 1_000L, Deflater.DEFAULT_COMPRESSION)));
    }

    @Test
    public void testLogicalOfficeContent_pathTraversalEntry_rejected() throws IOException {
        byte[] malformed = zip(Map.of(
                "[Content_Types].xml", "<Types/>",
                "../word/document.xml", "<w:document>same text</w:document>"),
                1_000L, Deflater.DEFAULT_COMPRESSION);

        assertFalse(OnlineEditServiceImpl.hasSameLogicalOfficeContent("contract.docx", malformed, malformed));
    }

    @Test
    public void testLogicalOfficeContent_duplicateEntry_rejected() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("word/document1.xml", "<w:document>first</w:document>");
        entries.put("word/document2.xml", "<w:document>second</w:document>");
        byte[] malformed = replaceAscii(zip(entries, 1_000L, Deflater.DEFAULT_COMPRESSION),
                "word/document2.xml", "word/document1.xml");

        assertFalse(OnlineEditServiceImpl.hasSameLogicalOfficeContent("contract.docx", malformed, malformed));
    }

    @Test
    public void testBoundedBodySubscriber_chunkedOversize_cancelled() {
        OnlineEditServiceImpl.BoundedBodySubscriber subscriber =
                new OnlineEditServiceImpl.BoundedBodySubscriber(3, -1);
        Flow.Subscription subscription = mock(Flow.Subscription.class);
        subscriber.onSubscribe(subscription);
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[]{1, 2}), ByteBuffer.wrap(new byte[]{3, 4})));

        assertTrue(subscriber.getBody().toCompletableFuture().isCompletedExceptionally());
        verify(subscription).cancel();
    }

    private static OnlyOfficeTokenPayload newPayload(String purpose) {
        return new OnlyOfficeTokenPayload()
                .setV(11L).setD(22L).setC(33L).setU(7L).setT(1L)
                .setM(OnlyOfficeTokenPayload.MODE_EDIT).setP(purpose);
    }

    private static DocumentVersionDO newVersion() {
        return new DocumentVersionDO()
                .setId(11L).setDocumentId(22L).setContractId(33L)
                .setChecksumSha256("abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789")
                .setFileKey("blob-11").setFileName("合同.docx");
    }

    private static byte[] zip(Map<String, String> entries, long timestamp, int level) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.setLevel(level);
            for (Map.Entry<String, String> item : entries.entrySet()) {
                ZipEntry entry = new ZipEntry(item.getKey());
                entry.setTime(timestamp);
                zip.putNextEntry(entry);
                zip.write(item.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private static byte[] replaceAscii(byte[] source, String before, String after) {
        byte[] search = before.getBytes(StandardCharsets.US_ASCII);
        byte[] replacement = after.getBytes(StandardCharsets.US_ASCII);
        assertEquals(search.length, replacement.length);
        byte[] result = Arrays.copyOf(source, source.length);
        int replacements = 0;
        for (int i = 0; i <= result.length - search.length; i++) {
            boolean match = true;
            for (int j = 0; j < search.length; j++) {
                if (result[i + j] != search[j]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                System.arraycopy(replacement, 0, result, i, replacement.length);
                replacements++;
                i += search.length - 1;
            }
        }
        assertEquals(2, replacements, "ZIP local header and central directory should both be rewritten");
        return result;
    }

}
