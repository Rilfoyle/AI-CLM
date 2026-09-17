package cn.iocoder.yudao.module.clm.partyimport;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.excel.core.util.ExcelUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.clm.controller.admin.party.vo.PartySaveReqVO;
import cn.iocoder.yudao.module.clm.dal.dataobject.party.PartyDO;
import cn.iocoder.yudao.module.clm.dal.mysql.party.PartyMapper;
import cn.iocoder.yudao.module.clm.enums.audit.ClmAuditActionEnum;
import cn.iocoder.yudao.module.clm.enums.audit.ClmAuditAggregateTypeEnum;
import cn.iocoder.yudao.module.clm.service.audit.ClmAuditService;
import cn.iocoder.yudao.module.clm.service.party.PartyService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.clm.partyimport.PartyImportErrors.*;

@Service
public class PartyImportService {
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final int MAX_ROWS = 5000;
    private static final Set<String> IMPORTABLE_JOB_STATUSES = Set.of("PREVIEW_READY", "PARTIAL", "FAILED");
    private static final Set<String> RETRYABLE_JOB_STATUSES = Set.of("PARTIAL", "FAILED", "SUCCEEDED");
    private static final Set<String> RETRYABLE_ITEM_STATUSES = Set.of("FAILED", "INVALID");
    private static final Set<String> FAILED_ITEM_STATUSES = Set.of("FAILED", "INVALID");

    @Resource private PartyImportJobMapper partyImportJobMapper;
    @Resource private PartyImportItemMapper partyImportItemMapper;
    @Resource private PartyMapper partyMapper;
    @Resource private PartyService partyService;
    @Resource private ClmAuditService auditService;
    @Resource private TransactionTemplate transactionTemplate;

    public List<PartyImportExcelRow> templateRows() {
        return List.of(PartyImportExcelRow.builder().name("示例供应商有限公司")
                .unifiedCreditCode("91310000MA1FL1234X").contactName("张三").contactPhone("13800000000").build());
    }

    @Transactional(rollbackFor = Exception.class)
    public Long upload(MultipartFile file, String jobKey) {
        String normalizedJobKey = StrUtil.trim(jobKey);
        if (StrUtil.isBlank(normalizedJobKey)) throw exception(JOB_KEY_REQUIRED);
        PartyImportJobDO existingJob = partyImportJobMapper.selectByJobKey(normalizedJobKey);
        if (existingJob != null) return existingJob.getId();
        validateFile(file);
        List<PartyImportExcelRow> rows;
        try {
            rows = ExcelUtils.read(file, PartyImportExcelRow.class);
        } catch (Exception ex) {
            throw exception(FILE_TYPE_INVALID);
        }
        if (rows == null || rows.isEmpty()) throw exception(FILE_EMPTY);
        if (rows.size() > MAX_ROWS) throw exception(ROW_LIMIT_EXCEEDED);

        PartyImportJobDO job = new PartyImportJobDO().setJobKey(normalizedJobKey)
                .setFileName(StrUtil.blankToDefault(file.getOriginalFilename(), "counterparties.xlsx"))
                .setStatus("VALIDATING").setMappingJson(JsonUtils.toJsonString(Map.of(
                        "headers", List.of("相对方名称*", "统一社会信用代码*", "联系人", "联系电话"),
                        "sha256", fileSha256(file))))
                .setTotalCount(rows.size()).setSuccessCount(0).setFailedCount(0);
        partyImportJobMapper.insert(job);

        Map<String, Integer> batchCodes = new HashMap<>();
        Map<String, Integer> batchNames = new HashMap<>();
        int invalidCount = 0;
        for (int index = 0; index < rows.size(); index++) {
            int rowNo = index + 2;
            PartyImportExcelRow row = normalize(rows.get(index));
            Validation validation = validateNewRow(row, rowNo, batchCodes, batchNames);
            PartyImportItemDO item = new PartyImportItemDO().setJobId(job.getId()).setRowNo(rowNo)
                    .setSourceJson(JsonUtils.toJsonString(row)).setDuplicatePartyId(validation.duplicatePartyId())
                    .setStatus(validation.status()).setErrorMessage(validation.message());
            partyImportItemMapper.insert(item);
            if ("INVALID".equals(validation.status())) invalidCount++;
            if ("VALID".equals(validation.status())) {
                batchCodes.put(normalizedCode(row.getUnifiedCreditCode()), rowNo);
                batchNames.put(normalizedName(row.getName()), rowNo);
            }
        }
        partyImportJobMapper.updateById(new PartyImportJobDO().setId(job.getId()).setStatus("PREVIEW_READY")
                .setFailedCount(invalidCount));
        appendAudit(job.getId(), ClmAuditActionEnum.PARTY_IMPORT_UPLOAD, Map.of("jobKey", normalizedJobKey,
                "fileName", job.getFileName(), "totalCount", rows.size(), "invalidCount", invalidCount));
        return job.getId();
    }

    public PageResult<PartyImportJobRespVO> page(PartyImportPageReqVO reqVO) {
        PageResult<PartyImportJobDO> page = partyImportJobMapper.selectPage(reqVO);
        List<PartyImportJobRespVO> rows = new ArrayList<>(page.getList().size());
        for (PartyImportJobDO job : page.getList()) rows.add(toJobResp(job, false));
        return new PageResult<>(rows, page.getTotal());
    }

    public PartyImportJobRespVO get(Long id) {
        return toJobResp(getRequiredJob(id), true);
    }

    public PageResult<PartyImportItemRespVO> itemPage(PartyImportItemPageReqVO reqVO) {
        getRequiredJob(reqVO.getJobId());
        PageResult<PartyImportItemDO> page = partyImportItemMapper.selectPage(reqVO);
        return new PageResult<>(page.getList().stream().map(this::toItemResp).toList(), page.getTotal());
    }

    @Transactional(rollbackFor = Exception.class)
    public PartyImportItemRespVO updateItem(PartyImportItemUpdateReqVO reqVO) {
        PartyImportItemDO item = partyImportItemMapper.selectByIdForUpdate(reqVO.getId());
        if (item == null) throw exception(ITEM_NOT_EXISTS);
        getRequiredJob(item.getJobId());
        if ("IMPORTED".equals(item.getStatus())) throw exception(ITEM_ALREADY_IMPORTED);
        PartyImportExcelRow row = normalize(PartyImportExcelRow.builder().name(reqVO.getName())
                .unifiedCreditCode(reqVO.getUnifiedCreditCode()).contactName(reqVO.getContactName())
                .contactPhone(reqVO.getContactPhone()).build());
        Validation validation = revalidate(row, item.getJobId(), item.getId(), item.getRowNo());
        String nextStatus = "VALID".equals(validation.status()) ? "FAILED" : validation.status();
        String message = "VALID".equals(validation.status()) ? "已修正，等待重试" : validation.message();
        PartyImportItemDO update = new PartyImportItemDO().setId(item.getId())
                .setSourceJson(JsonUtils.toJsonString(row)).setDuplicatePartyId(validation.duplicatePartyId())
                .setStatus(nextStatus).setErrorMessage(message).setPartyId(null);
        partyImportItemMapper.updateById(update);
        return toItemResp(partyImportItemMapper.selectById(item.getId()));
    }

    public PartyImportJobRespVO confirm(Long id, String requestId) {
        PartyImportJobDO job = getRequiredJob(id);
        if ("IMPORTING".equals(job.getStatus())) return toJobResp(job, true);
        List<PartyImportItemDO> validItems = partyImportItemMapper.selectListByJobIdAndStatuses(id, Set.of("VALID"));
        if (validItems.isEmpty()) {
            if (Set.of("SUCCEEDED", "PARTIAL", "FAILED").contains(job.getStatus())) return toJobResp(job, true);
            if (!IMPORTABLE_JOB_STATUSES.contains(job.getStatus())) throw exception(JOB_STATUS_INVALID, job.getStatus());
            finalizeJob(id);
            appendAudit(id, ClmAuditActionEnum.PARTY_IMPORT_CONFIRM,
                    Map.of("requestId", requestId, "processedCount", 0));
            return get(id);
        }
        if (!IMPORTABLE_JOB_STATUSES.contains(job.getStatus())) throw exception(JOB_STATUS_INVALID, job.getStatus());
        if (partyImportJobMapper.compareAndSetImporting(id, job.getStatus(), currentActor()) == 0) return get(id);
        for (PartyImportItemDO item : validItems) importOne(item.getId());
        finalizeJob(id);
        appendAudit(id, ClmAuditActionEnum.PARTY_IMPORT_CONFIRM, Map.of("requestId", requestId,
                "processedCount", validItems.size()));
        return get(id);
    }

    public PartyImportJobRespVO retryFailed(Long id, String requestId) {
        PartyImportJobDO job = getRequiredJob(id);
        if ("IMPORTING".equals(job.getStatus())) return toJobResp(job, true);
        if (!RETRYABLE_JOB_STATUSES.contains(job.getStatus())) throw exception(JOB_STATUS_INVALID, job.getStatus());
        List<PartyImportItemDO> retryable = partyImportItemMapper.selectListByJobIdAndStatuses(id, RETRYABLE_ITEM_STATUSES);
        List<Long> readyIds = new ArrayList<>();
        for (PartyImportItemDO item : retryable) {
            Boolean ready = transactionTemplate.execute(status -> revalidateForRetry(item.getId()));
            if (Boolean.TRUE.equals(ready)) readyIds.add(item.getId());
        }
        if (readyIds.isEmpty()) {
            finalizeJob(id);
            appendAudit(id, ClmAuditActionEnum.PARTY_IMPORT_RETRY, Map.of("requestId", requestId,
                    "retryableCount", retryable.size(), "processedCount", 0));
            return get(id);
        }
        PartyImportJobDO refreshed = getRequiredJob(id);
        if (partyImportJobMapper.compareAndSetImporting(id, refreshed.getStatus(), currentActor()) == 0) return get(id);
        for (Long itemId : readyIds) importOne(itemId);
        finalizeJob(id);
        appendAudit(id, ClmAuditActionEnum.PARTY_IMPORT_RETRY, Map.of("requestId", requestId,
                "retryableCount", retryable.size(), "processedCount", readyIds.size()));
        return get(id);
    }

    public List<PartyImportFailedExcelRow> failedRows(Long id) {
        getRequiredJob(id);
        List<PartyImportFailedExcelRow> result = new ArrayList<>();
        for (PartyImportItemDO item : partyImportItemMapper.selectListByJobIdAndStatuses(id, FAILED_ITEM_STATUSES)) {
            PartyImportExcelRow row = parseSource(item);
            result.add(PartyImportFailedExcelRow.builder().rowNo(item.getRowNo())
                    .name(safeForExcel(row.getName())).unifiedCreditCode(safeForExcel(row.getUnifiedCreditCode()))
                    .contactName(safeForExcel(row.getContactName())).contactPhone(safeForExcel(row.getContactPhone()))
                    .status(item.getStatus()).errorMessage(safeForExcel(item.getErrorMessage())).build());
        }
        return result;
    }

    private void importOne(Long itemId) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                PartyImportItemDO item = partyImportItemMapper.selectByIdForUpdate(itemId);
                if (item == null || !"VALID".equals(item.getStatus())) return;
                PartyImportExcelRow row = parseSource(item);
                Validation validation = revalidate(row, item.getJobId(), item.getId(), item.getRowNo());
                if (!"VALID".equals(validation.status())) {
                    partyImportItemMapper.updateById(new PartyImportItemDO().setId(item.getId())
                            .setStatus(validation.status()).setDuplicatePartyId(validation.duplicatePartyId())
                            .setErrorMessage(validation.message()));
                    return;
                }
                PartySaveReqVO party = new PartySaveReqVO();
                party.setPartyType(1); party.setName(row.getName()); party.setUnifiedCreditCode(row.getUnifiedCreditCode());
                party.setInternalFlag(false); party.setContactName(row.getContactName());
                party.setContactPhone(row.getContactPhone()); party.setStatus(0); party.setRemark("相对方批量导入");
                Long partyId = partyService.createParty(party);
                partyImportItemMapper.updateById(new PartyImportItemDO().setId(item.getId()).setStatus("IMPORTED")
                        .setPartyId(partyId).setDuplicatePartyId(null).setErrorMessage(""));
            });
        } catch (RuntimeException ex) {
            transactionTemplate.executeWithoutResult(status -> {
                PartyImportItemDO item = partyImportItemMapper.selectByIdForUpdate(itemId);
                if (item != null && !"IMPORTED".equals(item.getStatus())) {
                    partyImportItemMapper.updateById(new PartyImportItemDO().setId(itemId).setStatus("FAILED")
                            .setErrorMessage(errorMessage(ex)));
                }
            });
        }
    }

    private boolean revalidateForRetry(Long itemId) {
        PartyImportItemDO item = partyImportItemMapper.selectByIdForUpdate(itemId);
        if (item == null || !RETRYABLE_ITEM_STATUSES.contains(item.getStatus())) return false;
        Validation validation = revalidate(parseSource(item), item.getJobId(), item.getId(), item.getRowNo());
        partyImportItemMapper.updateById(new PartyImportItemDO().setId(item.getId()).setStatus(validation.status())
                .setDuplicatePartyId(validation.duplicatePartyId()).setErrorMessage(validation.message()));
        return "VALID".equals(validation.status());
    }

    private void finalizeJob(Long jobId) {
        List<PartyImportItemDO> items = partyImportItemMapper.selectListByJobId(jobId);
        int success = count(items, "IMPORTED");
        int failed = count(items, "FAILED") + count(items, "INVALID");
        int skipped = count(items, "SKIPPED");
        String status = failed > 0 ? (success > 0 || skipped > 0 ? "PARTIAL" : "FAILED") : "SUCCEEDED";
        partyImportJobMapper.updateById(new PartyImportJobDO().setId(jobId).setStatus(status).setSuccessCount(success)
                .setFailedCount(failed).setFinishedTime(LocalDateTime.now()));
    }

    private Validation validateNewRow(PartyImportExcelRow row, int rowNo,
                                      Map<String, Integer> batchCodes, Map<String, Integer> batchNames) {
        Validation basic = validateBasics(row);
        if (basic != null) return basic;
        PartyDO duplicate = findExistingDuplicate(row);
        if (duplicate != null) return new Validation("SKIPPED", "系统中已存在相同信用代码或名称", duplicate.getId());
        Integer codeRow = batchCodes.get(normalizedCode(row.getUnifiedCreditCode()));
        Integer nameRow = batchNames.get(normalizedName(row.getName()));
        Integer duplicateRow = codeRow != null ? codeRow : nameRow;
        if (duplicateRow != null) return new Validation("SKIPPED", "与本文件第 " + duplicateRow + " 行重复", null);
        return Validation.valid();
    }

    private Validation revalidate(PartyImportExcelRow row, Long jobId, Long currentItemId, Integer rowNo) {
        Validation basic = validateBasics(row);
        if (basic != null) return basic;
        PartyDO duplicate = findExistingDuplicate(row);
        if (duplicate != null) return new Validation("SKIPPED", "系统中已存在相同信用代码或名称", duplicate.getId());
        for (PartyImportItemDO other : partyImportItemMapper.selectListByJobId(jobId)) {
            if (Objects.equals(other.getId(), currentItemId) || other.getRowNo() >= rowNo
                    || "INVALID".equals(other.getStatus()) || "FAILED".equals(other.getStatus())) continue;
            PartyImportExcelRow otherRow = parseSource(other);
            if (normalizedCode(row.getUnifiedCreditCode()).equals(normalizedCode(otherRow.getUnifiedCreditCode()))
                    || normalizedName(row.getName()).equals(normalizedName(otherRow.getName()))) {
                Long duplicatePartyId = "IMPORTED".equals(other.getStatus()) ? other.getPartyId() : null;
                return new Validation("SKIPPED", "与本文件第 " + other.getRowNo() + " 行重复", duplicatePartyId);
            }
        }
        return Validation.valid();
    }

    private Validation validateBasics(PartyImportExcelRow row) {
        if (StrUtil.isBlank(row.getName())) return Validation.invalid("相对方名称不能为空");
        if (row.getName().length() > 255) return Validation.invalid("相对方名称长度不能超过 255");
        if (StrUtil.isBlank(row.getUnifiedCreditCode())) return Validation.invalid("统一社会信用代码不能为空");
        if (!row.getUnifiedCreditCode().matches("[0-9A-HJ-NPQRTUWXY]{18}")) {
            return Validation.invalid("统一社会信用代码必须为 18 位有效字符");
        }
        if (StrUtil.length(row.getContactName()) > 64) return Validation.invalid("联系人长度不能超过 64");
        if (StrUtil.length(row.getContactPhone()) > 32) return Validation.invalid("联系电话长度不能超过 32");
        return null;
    }

    private PartyDO findExistingDuplicate(PartyImportExcelRow row) {
        List<PartyDO> matches = partyMapper.selectList(new LambdaQueryWrapperX<PartyDO>()
                .and(query -> query.eq(PartyDO::getUnifiedCreditCode, row.getUnifiedCreditCode())
                        .or().eq(PartyDO::getName, row.getName()))
                .orderByAsc(PartyDO::getId).last("LIMIT 1"));
        return matches.isEmpty() ? null : matches.get(0);
    }

    private PartyImportExcelRow normalize(PartyImportExcelRow source) {
        PartyImportExcelRow row = source == null ? new PartyImportExcelRow() : source;
        row.setName(StrUtil.nullToEmpty(row.getName()).trim());
        row.setUnifiedCreditCode(normalizedCode(row.getUnifiedCreditCode()));
        row.setContactName(StrUtil.nullToEmpty(row.getContactName()).trim());
        row.setContactPhone(StrUtil.nullToEmpty(row.getContactPhone()).trim());
        return row;
    }

    private String normalizedCode(String value) {
        return StrUtil.nullToEmpty(value).replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    private String normalizedName(String value) {
        return StrUtil.nullToEmpty(value).trim().toLowerCase(Locale.ROOT);
    }

    private PartyImportJobRespVO toJobResp(PartyImportJobDO job, boolean includeItems) {
        PartyImportJobRespVO resp = BeanUtils.toBean(job, PartyImportJobRespVO.class);
        List<PartyImportItemDO> items = partyImportItemMapper.selectListByJobId(job.getId());
        resp.setValidCount(count(items, "VALID"));
        resp.setInvalidCount(count(items, "INVALID"));
        resp.setSkippedCount(count(items, "SKIPPED"));
        resp.setSuccessCount(count(items, "IMPORTED"));
        resp.setFailedCount(count(items, "FAILED") + count(items, "INVALID"));
        if (includeItems) resp.setItems(items.stream().map(this::toItemResp).toList());
        return resp;
    }

    private PartyImportItemRespVO toItemResp(PartyImportItemDO item) {
        PartyImportItemRespVO resp = BeanUtils.toBean(item, PartyImportItemRespVO.class);
        PartyImportExcelRow source = parseSource(item);
        resp.setName(source.getName()); resp.setUnifiedCreditCode(source.getUnifiedCreditCode());
        resp.setContactName(source.getContactName()); resp.setContactPhone(source.getContactPhone());
        return resp;
    }

    private PartyImportExcelRow parseSource(PartyImportItemDO item) {
        PartyImportExcelRow source = JsonUtils.parseObjectQuietly(item.getSourceJson(), PartyImportExcelRow.class);
        return normalize(source);
    }

    private PartyImportJobDO getRequiredJob(Long id) {
        PartyImportJobDO job = partyImportJobMapper.selectById(id);
        if (job == null) throw exception(JOB_NOT_EXISTS);
        return job;
    }

    private int count(List<PartyImportItemDO> items, String status) {
        return (int) items.stream().filter(item -> status.equals(item.getStatus())).count();
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw exception(FILE_EMPTY);
        if (file.getSize() > MAX_FILE_SIZE) throw exception(FILE_TOO_LARGE);
        String extension = StrUtil.emptyToDefault(FileUtil.extName(file.getOriginalFilename()), "").toLowerCase(Locale.ROOT);
        if (!Set.of("xls", "xlsx").contains(extension)) throw exception(FILE_TYPE_INVALID);
    }

    private String fileSha256(MultipartFile file) {
        try {
            return DigestUtil.sha256Hex(file.getBytes());
        } catch (Exception ex) {
            throw exception(FILE_TYPE_INVALID);
        }
    }

    private String safeForExcel(String value) {
        String text = StrUtil.nullToEmpty(value);
        return !text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0 ? "'" + text : text;
    }

    private String errorMessage(RuntimeException ex) {
        String message = StrUtil.blankToDefault(ex.getMessage(), "导入失败").replaceAll("[\\r\\n]+", " ");
        return StrUtil.sub(message, 0, 1000);
    }

    private String currentActor() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        return userId == null ? "" : String.valueOf(userId);
    }

    private void appendAudit(Long jobId, ClmAuditActionEnum action, Map<String, Object> detail) {
        auditService.record(ClmAuditAggregateTypeEnum.PARTY_IMPORT, jobId, null, action, detail);
    }

    private record Validation(String status, String message, Long duplicatePartyId) {
        static Validation valid() { return new Validation("VALID", "", null); }
        static Validation invalid(String message) { return new Validation("INVALID", message, null); }
    }
}
