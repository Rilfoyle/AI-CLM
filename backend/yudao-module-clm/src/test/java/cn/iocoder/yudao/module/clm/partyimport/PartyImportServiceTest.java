package cn.iocoder.yudao.module.clm.partyimport;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.excel.core.util.ExcelUtils;
import cn.iocoder.yudao.module.clm.controller.admin.party.vo.PartySaveReqVO;
import cn.iocoder.yudao.module.clm.dal.dataobject.party.PartyDO;
import cn.iocoder.yudao.module.clm.dal.mysql.party.PartyMapper;
import cn.iocoder.yudao.module.clm.enums.audit.ClmAuditActionEnum;
import cn.iocoder.yudao.module.clm.enums.audit.ClmAuditAggregateTypeEnum;
import cn.iocoder.yudao.module.clm.service.audit.ClmAuditService;
import cn.iocoder.yudao.module.clm.service.party.PartyService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PartyImportServiceTest {

    @InjectMocks private PartyImportService service;
    @Mock private PartyImportJobMapper jobMapper;
    @Mock private PartyImportItemMapper itemMapper;
    @Mock private PartyMapper partyMapper;
    @Mock private PartyService partyService;
    @Mock private ClmAuditService auditService;
    @Mock private TransactionTemplate transactionTemplate;

    @Test
    void template_isAReadableExcelWorkbook() throws Exception {
        byte[] bytes = workbook(PartyImportExcelRow.class, service.templateRows());

        assertTrue(bytes.length > 4);
        assertEquals('P', bytes[0]);
        assertEquals('K', bytes[1]);
        List<PartyImportExcelRow> rows = ExcelUtils.read(uploadFile("counterparty-template.xlsx", bytes),
                PartyImportExcelRow.class);
        assertEquals(1, rows.size());
        assertEquals("示例供应商有限公司", rows.get(0).getName());
        assertEquals("91310000MA1FL1234X", rows.get(0).getUnifiedCreditCode());
        assertEquals("张三", rows.get(0).getContactName());
        assertEquals("13800000000", rows.get(0).getContactPhone());
    }

    @Test
    void upload_persistsValidInvalidAndDuplicateRows() throws Exception {
        List<PartyImportExcelRow> rows = List.of(
                row("甲公司", "91310000MA1FL1234X"),
                row("乙公司", "BAD"),
                row("甲公司", "91310000MA1FL5678Y"),
                row("已存在公司", "91310000MA1FL9999Q"));
        MockMultipartFile file = uploadFile("counterparties.xlsx", workbook(PartyImportExcelRow.class, rows));
        when(jobMapper.selectByJobKey("job-1")).thenReturn(null);
        when(jobMapper.insert(any(PartyImportJobDO.class))).thenAnswer(invocation -> {
            PartyImportJobDO job = invocation.getArgument(0);
            job.setId(10L);
            return 1;
        });
        when(partyMapper.selectList(org.mockito.ArgumentMatchers.<Wrapper<PartyDO>>any()))
                .thenReturn(List.of(), List.of(), List.of(new PartyDO().setId(88L)));

        assertEquals(10L, service.upload(file, " job-1 "));

        ArgumentCaptor<PartyImportItemDO> itemCaptor = ArgumentCaptor.forClass(PartyImportItemDO.class);
        verify(itemMapper, times(4)).insert(itemCaptor.capture());
        assertEquals(List.of("VALID", "INVALID", "SKIPPED", "SKIPPED"), itemCaptor.getAllValues().stream()
                .map(PartyImportItemDO::getStatus).toList());
        assertEquals(88L, itemCaptor.getAllValues().get(3).getDuplicatePartyId());
        assertTrue(itemCaptor.getAllValues().get(1).getErrorMessage().contains("18 位"));
        verify(jobMapper).updateById(argThat((PartyImportJobDO job) -> Long.valueOf(10L).equals(job.getId())
                && "PREVIEW_READY".equals(job.getStatus()) && Integer.valueOf(1).equals(job.getFailedCount())));
        verify(auditService).record(eq(ClmAuditAggregateTypeEnum.PARTY_IMPORT), eq(10L), isNull(),
                eq(ClmAuditActionEnum.PARTY_IMPORT_UPLOAD), anyMap());
    }

    @Test
    void updateItem_correctedInvalidRowBecomesRetryable() {
        PartyImportItemDO original = item(1L, "INVALID", row("甲公司", "BAD"));
        AtomicReference<PartyImportItemDO> itemRef = new AtomicReference<>(original);
        when(itemMapper.selectByIdForUpdate(1L)).thenAnswer(invocation -> itemRef.get());
        when(itemMapper.selectById(1L)).thenAnswer(invocation -> itemRef.get());
        when(itemMapper.selectListByJobId(10L)).thenAnswer(invocation -> List.of(itemRef.get()));
        when(jobMapper.selectById(10L)).thenReturn(job(10L, "PREVIEW_READY"));
        when(partyMapper.selectList(org.mockito.ArgumentMatchers.<Wrapper<PartyDO>>any())).thenReturn(List.of());
        mergeItemUpdates(itemRef);
        PartyImportItemUpdateReqVO reqVO = new PartyImportItemUpdateReqVO();
        reqVO.setId(1L);
        reqVO.setName(" 甲公司 ");
        reqVO.setUnifiedCreditCode(" 91310000ma1fl1234x ");
        reqVO.setContactName("李四");
        reqVO.setContactPhone("13900000000");

        PartyImportItemRespVO result = service.updateItem(reqVO);

        assertEquals("FAILED", result.getStatus());
        assertEquals("已修正，等待重试", result.getErrorMessage());
        assertEquals("91310000MA1FL1234X", result.getUnifiedCreditCode());
    }

    @Test
    void confirm_importsValidRowsExactlyOnce() {
        AtomicReference<PartyImportJobDO> jobRef = new AtomicReference<>(job(10L, "PREVIEW_READY"));
        AtomicReference<PartyImportItemDO> itemRef = new AtomicReference<>(
                item(1L, "VALID", row("甲公司", "91310000MA1FL1234X")));
        stubStatefulImport(jobRef, itemRef);

        PartyImportJobRespVO first = service.confirm(10L, "confirm-1");
        PartyImportJobRespVO second = service.confirm(10L, "confirm-1");

        assertEquals("SUCCEEDED", first.getStatus());
        assertEquals(1, first.getSuccessCount());
        assertEquals("IMPORTED", first.getItems().get(0).getStatus());
        assertEquals("SUCCEEDED", second.getStatus());
        verify(partyService, times(1)).createParty(argThat((PartySaveReqVO party) -> "甲公司".equals(party.getName())
                && "91310000MA1FL1234X".equals(party.getUnifiedCreditCode())
                && Boolean.FALSE.equals(party.getInternalFlag())));
        verify(auditService, times(1)).record(eq(ClmAuditAggregateTypeEnum.PARTY_IMPORT), eq(10L), isNull(),
                eq(ClmAuditActionEnum.PARTY_IMPORT_CONFIRM), anyMap());
    }

    @Test
    void retryFailed_revalidatesAndImportsOnlyFailedRows() {
        AtomicReference<PartyImportJobDO> jobRef = new AtomicReference<>(job(10L, "FAILED"));
        AtomicReference<PartyImportItemDO> itemRef = new AtomicReference<>(
                item(1L, "FAILED", row("甲公司", "91310000MA1FL1234X")));
        stubStatefulImport(jobRef, itemRef);

        PartyImportJobRespVO result = service.retryFailed(10L, "retry-1");

        assertEquals("SUCCEEDED", result.getStatus());
        assertEquals("IMPORTED", result.getItems().get(0).getStatus());
        verify(partyService, times(1)).createParty(any(PartySaveReqVO.class));
        verify(auditService).record(eq(ClmAuditAggregateTypeEnum.PARTY_IMPORT), eq(10L), isNull(),
                eq(ClmAuditActionEnum.PARTY_IMPORT_RETRY), anyMap());
    }

    @Test
    void failedRows_exportWorkbookEscapesFormulaCells() throws Exception {
        PartyImportExcelRow unsafe = PartyImportExcelRow.builder().name("=HYPERLINK(\"https://example.test\")")
                .unifiedCreditCode("@formula").contactName("+SUM(1,1)").contactPhone("-2").build();
        PartyImportItemDO item = item(1L, "FAILED", unsafe).setErrorMessage("+external command");
        when(jobMapper.selectById(10L)).thenReturn(job(10L, "FAILED"));
        when(itemMapper.selectListByJobIdAndStatuses(eq(10L),
                org.mockito.ArgumentMatchers.<Collection<String>>any()))
                .thenReturn(List.of(item));

        List<PartyImportFailedExcelRow> failed = service.failedRows(10L);
        byte[] bytes = workbook(PartyImportFailedExcelRow.class, failed);
        List<PartyImportFailedExcelRow> exported = ExcelUtils.read(uploadFile("failed.xlsx", bytes),
                PartyImportFailedExcelRow.class);

        assertEquals(1, exported.size());
        assertTrue(exported.get(0).getName().startsWith("'="));
        assertTrue(exported.get(0).getUnifiedCreditCode().startsWith("'@"));
        assertTrue(exported.get(0).getContactName().startsWith("'+"));
        assertTrue(exported.get(0).getContactPhone().startsWith("'-"));
        assertTrue(exported.get(0).getErrorMessage().startsWith("'+"));
    }

    private void stubStatefulImport(AtomicReference<PartyImportJobDO> jobRef,
                                    AtomicReference<PartyImportItemDO> itemRef) {
        when(jobMapper.selectById(10L)).thenAnswer(invocation -> jobRef.get());
        when(itemMapper.selectByIdForUpdate(1L)).thenAnswer(invocation -> itemRef.get());
        when(itemMapper.selectListByJobId(10L)).thenAnswer(invocation -> List.of(itemRef.get()));
        when(itemMapper.selectListByJobIdAndStatuses(eq(10L), org.mockito.ArgumentMatchers.<Collection<String>>any()))
                .thenAnswer(invocation -> {
                    Collection<String> statuses = invocation.getArgument(1);
                    return statuses.contains(itemRef.get().getStatus()) ? List.of(itemRef.get()) : List.of();
                });
        when(partyMapper.selectList(org.mockito.ArgumentMatchers.<Wrapper<PartyDO>>any())).thenReturn(List.of());
        when(partyService.createParty(any(PartySaveReqVO.class))).thenReturn(99L);
        when(jobMapper.compareAndSetImporting(eq(10L), anyString(), anyString())).thenAnswer(invocation -> {
            jobRef.get().setStatus("IMPORTING");
            return 1;
        });
        when(jobMapper.updateById(any(PartyImportJobDO.class))).thenAnswer(invocation -> {
            PartyImportJobDO update = invocation.getArgument(0);
            if (update.getStatus() != null) jobRef.get().setStatus(update.getStatus());
            if (update.getSuccessCount() != null) jobRef.get().setSuccessCount(update.getSuccessCount());
            if (update.getFailedCount() != null) jobRef.get().setFailedCount(update.getFailedCount());
            if (update.getFinishedTime() != null) jobRef.get().setFinishedTime(update.getFinishedTime());
            return 1;
        });
        mergeItemUpdates(itemRef);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        lenient().doAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        }).when(transactionTemplate).execute(any(TransactionCallback.class));
    }

    private void mergeItemUpdates(AtomicReference<PartyImportItemDO> itemRef) {
        when(itemMapper.updateById(any(PartyImportItemDO.class))).thenAnswer(invocation -> {
            PartyImportItemDO update = invocation.getArgument(0);
            PartyImportItemDO current = itemRef.get();
            if (update.getSourceJson() != null) current.setSourceJson(update.getSourceJson());
            if (update.getStatus() != null) current.setStatus(update.getStatus());
            if (update.getDuplicatePartyId() != null || current.getDuplicatePartyId() != null) {
                current.setDuplicatePartyId(update.getDuplicatePartyId());
            }
            if (update.getPartyId() != null || current.getPartyId() != null) current.setPartyId(update.getPartyId());
            if (update.getErrorMessage() != null) current.setErrorMessage(update.getErrorMessage());
            return 1;
        });
    }

    private static PartyImportJobDO job(Long id, String status) {
        return new PartyImportJobDO().setId(id).setJobKey("job-1").setFileName("counterparties.xlsx")
                .setStatus(status).setTotalCount(1).setSuccessCount(0).setFailedCount(0);
    }

    private static PartyImportItemDO item(Long id, String status, PartyImportExcelRow source) {
        return new PartyImportItemDO().setId(id).setJobId(10L).setRowNo(2)
                .setSourceJson(JsonUtils.toJsonString(source)).setStatus(status).setErrorMessage("");
    }

    private static PartyImportExcelRow row(String name, String creditCode) {
        return PartyImportExcelRow.builder().name(name).unifiedCreditCode(creditCode)
                .contactName("张三").contactPhone("13800000000").build();
    }

    private static MockMultipartFile uploadFile(String fileName, byte[] bytes) {
        return new MockMultipartFile("file", fileName,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
    }

    private static <T> byte[] workbook(Class<T> type, List<T> rows) throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        ExcelUtils.write(response, "test.xlsx", "data", type, rows);
        return response.getContentAsByteArray();
    }
}
