package cn.iocoder.yudao.module.clm.service.onlineedit;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.framework.web.config.WebProperties;
import cn.iocoder.yudao.module.clm.access.ContractAccessService;
import cn.iocoder.yudao.module.clm.controller.admin.onlineedit.vo.OnlineEditConfigRespVO;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.document.DocumentDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.document.DocumentVersionDO;
import cn.iocoder.yudao.module.clm.dal.mysql.document.DocumentMapper;
import cn.iocoder.yudao.module.clm.document.ClmDocumentStorage;
import cn.iocoder.yudao.module.clm.enums.audit.ClmAuditActionEnum;
import cn.iocoder.yudao.module.clm.enums.audit.ClmAuditAggregateTypeEnum;
import cn.iocoder.yudao.module.clm.enums.contract.ClmApprovalStatusEnum;
import cn.iocoder.yudao.module.clm.enums.document.ClmDocumentSourceTypeEnum;
import cn.iocoder.yudao.module.clm.framework.onlyoffice.config.ClmOnlyOfficeProperties;
import cn.iocoder.yudao.module.clm.onlyoffice.OnlyOfficeTokenPayload;
import cn.iocoder.yudao.module.clm.onlyoffice.OnlyOfficeTokenService;
import cn.iocoder.yudao.module.clm.service.audit.ClmAuditService;
import cn.iocoder.yudao.module.clm.service.contract.ContractService;
import cn.iocoder.yudao.module.clm.service.document.DocumentDownloadResult;
import cn.iocoder.yudao.module.clm.service.document.DocumentService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.invalidParamException;
import static cn.iocoder.yudao.module.clm.enums.ErrorCodeConstants.*;

/**
 * CLM 在线编辑（ONLYOFFICE）Service 实现类
 */
@Service
@Validated
@Slf4j
public class OnlineEditServiceImpl implements OnlineEditService {

    public static final String PERMISSION_EDIT = "clm:contract:update";
    public static final String PERMISSION_VIEW = "clm:contract:query";

    /**
     * 扩展名 → ONLYOFFICE documentType
     */
    private static final Map<String, String> DOCUMENT_TYPES;

    static {
        Map<String, String> map = new HashMap<>();
        map.put("docx", "word");
        map.put("doc", "word");
        map.put("xlsx", "cell");
        map.put("xls", "cell");
        map.put("pptx", "slide");
        map.put("ppt", "slide");
        DOCUMENT_TYPES = Collections.unmodifiableMap(map);
    }

    /**
     * 回调 status：2 已准备保存；6 强制保存
     */
    private static final Set<Integer> SAVE_STATUSES = new HashSet<>(Arrays.asList(2, 6));
    /**
     * 拉取文件超时（毫秒）
     */
    private static final int DOWNLOAD_TIMEOUT_MS = 60_000;
    /**
     * 回调下载与普通文档上传使用同一 50 MiB 上限，且在读取响应体时执行流式截断，避免先把超大响应读入内存。
     */
    static final long MAX_DOWNLOAD_BYTES = 50L * 1024L * 1024L;
    /**
     * OOXML 逻辑内容指纹的解压上限。回调本身最多 50 MiB；指纹仅用于去重，超过上限时退回二进制 SHA 判断。
     */
    static final long MAX_CANONICAL_UNCOMPRESSED_BYTES = 200L * 1024L * 1024L;
    static final int MAX_CANONICAL_ARCHIVE_ENTRIES = 10_000;
    private static final Set<String> OOXML_EXTENSIONS = Set.of("docx", "xlsx", "pptx");

    private final HttpClient callbackHttpClient;

    public OnlineEditServiceImpl() {
        this(buildCallbackHttpClient());
    }

    OnlineEditServiceImpl(HttpClient callbackHttpClient) {
        this.callbackHttpClient = Objects.requireNonNull(callbackHttpClient);
    }

    @Resource
    private ClmOnlyOfficeProperties properties;
    @Resource
    private WebProperties webProperties;

    @Resource
    private OnlyOfficeTokenService onlyOfficeTokenService;
    @Resource
    private DocumentService documentService;
    @Resource
    private ContractService contractService;
    @Resource
    private ContractAccessService contractAccessService;
    @Resource
    private ClmAuditService clmAuditService;
    @Resource
    private ClmDocumentStorage documentStorage;

    @Resource
    private DocumentMapper documentMapper;

    @Resource
    private PermissionApi permissionApi;
    @Resource
    private AdminUserApi adminUserApi;

    // ========== 3.1 config ==========

    @Override
    public OnlineEditConfigRespVO buildConfig(Long versionId, String mode, Long userId) {
        // 1. 参数
        mode = StrUtil.blankToDefault(mode, OnlyOfficeTokenPayload.MODE_VIEW).toLowerCase();
        boolean edit = OnlyOfficeTokenPayload.MODE_EDIT.equals(mode);
        if (!edit && !OnlyOfficeTokenPayload.MODE_VIEW.equals(mode)) {
            throw invalidParamException("mode 只能为 edit 或 view");
        }
        if (userId == null) {
            throw exception(GlobalErrorCodeConstants.UNAUTHORIZED);
        }
        // 2. 版本 / 文档 / 合同
        DocumentVersionDO version = documentService.getRequiredDocumentVersion(versionId);
        DocumentDO document = documentMapper.selectById(version.getDocumentId());
        if (document == null) {
            throw exception(DOCUMENT_NOT_EXISTS);
        }
        ContractDO contract = contractService.getRequiredContract(version.getContractId());
        // 3. 功能权限（Controller 只校验二者之一，这里按 mode 校验更严格的那个）+ 对象权限
        if (!permissionApi.hasAnyPermissions(userId, edit ? PERMISSION_EDIT : PERMISSION_VIEW)) {
            throw exception(GlobalErrorCodeConstants.FORBIDDEN);
        }
        if (edit) {
            contractAccessService.assertCanEdit(contract, userId); // 审批中抛 CONTRACT_LOCKED_BY_APPROVAL
            if (BooleanUtil.isTrue(version.getFrozen())) {
                throw exception(ONLINE_EDIT_VERSION_FROZEN);
            }
            if (!Objects.equals(document.getCurrentVersionId(), versionId)) {
                throw exception(ONLINE_EDIT_NOT_CURRENT_VERSION);
            }
        } else {
            contractAccessService.assertCanView(contract, userId);
        }
        // 4. 文件类型
        String extension = StrUtil.emptyToDefault(FileUtil.extName(version.getFileName()), "").toLowerCase();
        String documentType = DOCUMENT_TYPES.get(extension);
        if (documentType == null) {
            throw exception(ONLINE_EDIT_UNSUPPORTED_TYPE, extension);
        }
        // 5. 未启用：只返回 enabled=false（前端据此提示）
        OnlineEditConfigRespVO respVO = new OnlineEditConfigRespVO()
                .setEnabled(BooleanUtil.isTrue(properties.getEnabled()))
                .setDocumentServerUrl(properties.getDocumentServerUrl());
        if (!respVO.getEnabled()) {
            return respVO;
        }
        // 6. 令牌与 URL
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            tenantId = contract.getTenantId();
        }
        String fileToken = onlyOfficeTokenService.issue(newPayload(version, userId, tenantId, mode,
                OnlyOfficeTokenPayload.PURPOSE_FILE));
        String fileUrl = buildOpenUrl("/clm/online-edit/file", fileToken);
        boolean canDownload = contractAccessService.canDownload(contract, userId);
        // 7. config
        Map<String, Object> permissions = new LinkedHashMap<>();
        permissions.put("edit", edit);
        permissions.put("download", canDownload);
        permissions.put("print", canDownload);
        permissions.put("review", true);
        permissions.put("comment", true);

        Map<String, Object> documentConfig = new LinkedHashMap<>();
        documentConfig.put("fileType", extension);
        documentConfig.put("key", buildDocumentKey(version));
        documentConfig.put("title", version.getFileName());
        documentConfig.put("url", fileUrl);
        documentConfig.put("permissions", permissions);

        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", String.valueOf(userId));
        user.put("name", resolveNickname(userId));

        Map<String, Object> customization = new LinkedHashMap<>();
        customization.put("forcesave", true);
        customization.put("autosave", true);

        Map<String, Object> editorConfig = new LinkedHashMap<>();
        editorConfig.put("mode", mode);
        editorConfig.put("lang", "zh-CN");
        if (edit) {
            String callbackToken = onlyOfficeTokenService.issue(newPayload(version, userId, tenantId, mode,
                    OnlyOfficeTokenPayload.PURPOSE_CALLBACK));
            editorConfig.put("callbackUrl", buildOpenUrl("/clm/online-edit/callback", callbackToken));
        }
        editorConfig.put("user", user);
        editorConfig.put("customization", customization);

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("documentType", documentType);
        config.put("document", documentConfig);
        config.put("editorConfig", editorConfig);
        respVO.setConfig(config);
        if (onlyOfficeTokenService.isJwtEnabled()) {
            respVO.setToken(onlyOfficeTokenService.createJwt(config));
        }
        // 8. 审计
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("versionId", version.getId());
        detail.put("versionNo", version.getVersionNo());
        detail.put("fileName", version.getFileName());
        detail.put("mode", mode);
        detail.put("key", documentConfig.get("key"));
        clmAuditService.record(ClmAuditAggregateTypeEnum.DOCUMENT, document.getId(), contract.getId(),
                ClmAuditActionEnum.ONLINE_EDIT_OPEN, detail);
        return respVO;
    }

    // ========== 3.2 file ==========

    @Override
    public DocumentDownloadResult loadFile(String token) {
        assertEnabled();
        OnlyOfficeTokenPayload payload = onlyOfficeTokenService.verify(token, OnlyOfficeTokenPayload.PURPOSE_FILE);
        if (payload.getU() == null) {
            throw exception(ONLINE_EDIT_TOKEN_INVALID);
        }
        return executeInTenant(payload.getT(), () -> {
            DocumentVersionDO version = documentService.getRequiredDocumentVersion(payload.getV());
            assertPayloadMatches(payload, version);
            // file token 不是 ACL 快照：参与人被撤权后，未过期 token 也必须立即失效。
            ContractDO contract = contractService.getRequiredContract(version.getContractId());
            contractAccessService.assertCanView(contract, payload.getU());
            byte[] content = documentStorage.load(version.getFileKey());
            log.debug("[loadFile][Document Server 拉取版本({}) 文件({}) 大小({}) 用户({}) 模式({})]",
                    version.getId(), version.getFileName(), content.length, payload.getU(), payload.getM());
            return new DocumentDownloadResult(version, content);
        });
    }

    // ========== 3.2 callback ==========

    @Override
    public Long handleCallback(String token, Map<String, Object> body, String jwt) {
        assertEnabled();
        // 1. 紧凑令牌 + Document Server JWT
        OnlyOfficeTokenPayload payload = onlyOfficeTokenService.verify(token, OnlyOfficeTokenPayload.PURPOSE_CALLBACK);
        if (onlyOfficeTokenService.isJwtEnabled() && !onlyOfficeTokenService.verifyJwt(jwt)) {
            log.warn("[handleCallback][版本({}) 回调 JWT 校验失败]", payload.getV());
            throw exception(ONLINE_EDIT_TOKEN_INVALID);
        }
        if (body == null) {
            body = Collections.emptyMap();
        }
        Integer status = toInteger(body.get("status"));
        String url = toStringValue(body.get("url"));
        String key = toStringValue(body.get("key"));
        Object users = body.get("users");
        Integer forceSaveType = toInteger(body.get("forcesavetype"));
        log.info("[handleCallback][版本({}) 回调 status({}) key({}) users({}) forcesavetype({})]",
                payload.getV(), status, key, users, forceSaveType);
        // 2. key 必须与 token 绑定版本一致；所有回调状态都校验，避免用一个编辑会话串改其它版本。
        DocumentVersionDO parent = executeInTenant(payload.getT(), () -> {
            DocumentVersionDO version = documentService.getRequiredDocumentVersion(payload.getV());
            assertPayloadMatches(payload, version);
            if (!StrUtil.equals(key, buildDocumentKey(version))) {
                log.warn("[handleCallback][版本({}) 回调 key({}) 不匹配]", payload.getV(), key);
                throw exception(ONLINE_EDIT_TOKEN_INVALID);
            }
            return version;
        });
        // 3. 非保存状态只记日志
        if (status == null || !SAVE_STATUSES.contains(status)) {
            return null;
        }
        // 4. 拉取新文件：仅允许 configured Document Server 同源 URL，且不跟随重定向。
        byte[] content = downloadContent(url, payload.getV());
        String sha256 = DigestUtil.sha256Hex(content);
        Integer finalStatus = status;
        return executeInTenant(payload.getT(), () -> {
            DocumentDO document = documentMapper.selectById(parent.getDocumentId());
            if (document == null) {
                throw exception(DOCUMENT_NOT_EXISTS);
            }
            // 5. 幂等：文档当前版本内容相同则不新建
            DocumentVersionDO current = document.getCurrentVersionId() == null ? null
                    : documentService.getDocumentVersion(document.getCurrentVersionId());
            if (current != null && StrUtil.equalsIgnoreCase(current.getChecksumSha256(), sha256)) {
                log.info("[handleCallback][版本({}) 回调内容与当前版本({}) 相同，幂等跳过]", parent.getId(), current.getId());
                return null;
            }
            // ONLYOFFICE 的 forcesave(status=6) 与随后 close(status=2) 可能返回解压后逐项完全相同、
            // 但 ZIP 容器元数据/压缩结果不同的 OOXML。仅当当前版本也是同一父版本的在线编辑产物时，
            // 再比较规范化的 archive-entry 内容；若用户在 forcesave 后继续编辑，document.xml 等条目会变化，仍会保留新版本。
            if (isSameLogicalCallbackContent(parent, current, content)) {
                log.info("[handleCallback][版本({}) 回调与同会话当前版本({}) 的 OOXML 逻辑内容相同，幂等跳过]",
                        parent.getId(), current.getId());
                return null;
            }
            // 6. 新建版本（审批中 / 父版本已冻结 / 父版本已非当前版本 仍然保存，但审计标记 lateSave）
            ContractDO contract = contractService.getRequiredContract(parent.getContractId());
            boolean running = ClmApprovalStatusEnum.RUNNING.getStatus().equals(contract.getApprovalStatus());
            boolean parentNotCurrent = !Objects.equals(document.getCurrentVersionId(), parent.getId());
            boolean lateSave = running || BooleanUtil.isTrue(parent.getFrozen()) || parentNotCurrent;
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("status", finalStatus);
            extra.put("key", key);
            extra.put("forcesavetype", forceSaveType);
            extra.put("users", users);
            extra.put("lateSave", lateSave);
            String remark = StrUtil.format("ONLYOFFICE 保存 (status={})", finalStatus);
            Long versionId = documentService.createVersionFromBytes(parent.getContractId(), document.getId(),
                    parent.getId(), parent.getFileName(), parent.getMimeType(), content,
                    ClmDocumentSourceTypeEnum.ONLINE_EDIT, remark, payload.getU(), extra, !lateSave);
            log.info("[handleCallback][版本({}) 回调保存为新版本({}) sha256({}) lateSave({})]",
                    parent.getId(), versionId, sha256, lateSave);
            return versionId;
        });
    }

    // ========== 私有方法 ==========

    private void assertEnabled() {
        if (!BooleanUtil.isTrue(properties.getEnabled())) {
            throw exception(ONLINE_EDIT_DISABLED);
        }
    }

    private OnlyOfficeTokenPayload newPayload(DocumentVersionDO version, Long userId, Long tenantId,
                                              String mode, String purpose) {
        return new OnlyOfficeTokenPayload()
                .setV(version.getId())
                .setD(version.getDocumentId())
                .setC(version.getContractId())
                .setU(userId)
                .setT(tenantId)
                .setM(mode)
                .setP(purpose);
    }

    private void assertPayloadMatches(OnlyOfficeTokenPayload payload, DocumentVersionDO version) {
        if ((payload.getD() != null && !Objects.equals(payload.getD(), version.getDocumentId()))
                || (payload.getC() != null && !Objects.equals(payload.getC(), version.getContractId()))) {
            throw exception(ONLINE_EDIT_TOKEN_INVALID);
        }
    }

    private String buildOpenUrl(String path, String token) {
        String base = StrUtil.removeSuffix(StrUtil.nullToEmpty(properties.getCallbackBaseUrl()), "/");
        String prefix = webProperties.getAdminApi() == null ? "/admin-api"
                : StrUtil.blankToDefault(webProperties.getAdminApi().getPrefix(), "/admin-api");
        return base + prefix + path + "?token=" + token;
    }

    /**
     * 同一版本稳定；保存生成新版本 → 新 key
     */
    static String buildDocumentKey(DocumentVersionDO version) {
        String sha = StrUtil.nullToEmpty(version.getChecksumSha256());
        return StrUtil.format("clm-v{}-{}", version.getId(), StrUtil.sub(sha, 0, Math.min(12, sha.length())));
    }

    private String resolveNickname(Long userId) {
        try {
            AdminUserRespDTO user = adminUserApi.getUser(userId);
            if (user != null && StrUtil.isNotBlank(user.getNickname())) {
                return user.getNickname();
            }
        } catch (Exception ex) {
            log.debug("[resolveNickname][用户({}) 获取失败: {}]", userId, ex.getMessage());
        }
        return String.valueOf(userId);
    }

    byte[] downloadContent(String url, Long versionId) {
        if (StrUtil.isBlank(url)) {
            log.warn("[downloadContent][版本({}) 回调未携带 url]", versionId);
            throw exception(ONLINE_EDIT_CALLBACK_FETCH_FAILED);
        }
        URI uri = validateDownloadUri(properties.getDocumentServerUrl(), url);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(Duration.ofMillis(DOWNLOAD_TIMEOUT_MS))
                .build();
        CompletableFuture<HttpResponse<byte[]>> responseFuture = callbackHttpClient.sendAsync(
                request, boundedBodyHandler(MAX_DOWNLOAD_BYTES));
        try {
            // HttpRequest.timeout 主要约束收到响应；Future timeout + cancel 继续约束流式 body，防止同源服务慢速拖住线程。
            HttpResponse<byte[]> response = responseFuture.get(DOWNLOAD_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            int status = response.statusCode();
            // 只接受最终 2xx；客户端配置为 NEVER，任何 3xx 都在这里拒绝，不能跳转到其它源。
            if (status < 200 || status >= 300) {
                log.warn("[downloadContent][版本({}) Document Server 拉取失败: status({})]", versionId, status);
                throw exception(ONLINE_EDIT_CALLBACK_FETCH_FAILED);
            }
            long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
            if (contentLength > MAX_DOWNLOAD_BYTES) {
                log.warn("[downloadContent][版本({}) 响应声明大小({}) 超过上限({})]",
                        versionId, contentLength, MAX_DOWNLOAD_BYTES);
                throw exception(ONLINE_EDIT_CALLBACK_FETCH_FAILED);
            }
            // 注意：本模块的 /file 接口在令牌失败时返回 HTTP 200 的 JSON 错误体，这里按 Content-Type 识别并拒绝
            String contentType = response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
            if (contentType.contains("application/json")) {
                log.warn("[downloadContent][版本({}) Document Server 返回 JSON: contentType({})]",
                        versionId, contentType);
                throw exception(ONLINE_EDIT_CALLBACK_FETCH_FAILED);
            }
            byte[] content = response.body();
            if (content == null || content.length == 0 || content.length > MAX_DOWNLOAD_BYTES) {
                log.warn("[downloadContent][版本({}) 拉取内容大小({})无效]",
                        versionId, content == null ? null : content.length);
                throw exception(ONLINE_EDIT_CALLBACK_FETCH_FAILED);
            }
            return content;
        } catch (ServiceException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            responseFuture.cancel(true);
            Thread.currentThread().interrupt();
            log.warn("[downloadContent][版本({}) 拉取被中断]", versionId);
            throw exception(ONLINE_EDIT_CALLBACK_FETCH_FAILED);
        } catch (TimeoutException ex) {
            responseFuture.cancel(true);
            log.warn("[downloadContent][版本({}) 拉取超过 {} ms]", versionId, DOWNLOAD_TIMEOUT_MS);
            throw exception(ONLINE_EDIT_CALLBACK_FETCH_FAILED);
        } catch (ExecutionException | RuntimeException ex) {
            log.warn("[downloadContent][版本({}) 拉取失败: {}]", versionId, ex.getMessage());
            throw exception(ONLINE_EDIT_CALLBACK_FETCH_FAILED);
        }
    }

    private boolean isSameLogicalCallbackContent(DocumentVersionDO parent, DocumentVersionDO current,
                                                  byte[] callbackContent) {
        if (current == null
                || !Objects.equals(parent.getId(), current.getParentVersionId())
                || !ClmDocumentSourceTypeEnum.ONLINE_EDIT.getCode().equals(current.getSourceType())) {
            return false;
        }
        byte[] currentContent = documentStorage.load(current.getFileKey());
        return hasSameLogicalOfficeContent(parent.getFileName(), currentContent, callbackContent);
    }

    /**
     * 比较 OOXML 解压后的条目名称与内容；忽略 ZIP 时间戳、条目顺序和压缩差异。
     * 任一包非法或超过防膨胀上限时返回 false，由调用方退回“视为新内容”的保守路径。
     */
    static boolean hasSameLogicalOfficeContent(String fileName, byte[] left, byte[] right) {
        String extension = StrUtil.emptyToDefault(FileUtil.extName(fileName), "").toLowerCase(Locale.ROOT);
        if (!OOXML_EXTENSIONS.contains(extension)) {
            return false;
        }
        String leftDigest = canonicalArchiveDigest(left);
        String rightDigest = canonicalArchiveDigest(right);
        return leftDigest != null && leftDigest.equals(rightDigest);
    }

    private static String canonicalArchiveDigest(byte[] archive) {
        if (archive == null || archive.length == 0) {
            return null;
        }
        List<String> entryFingerprints = new ArrayList<>();
        Set<String> entryNames = new HashSet<>();
        long totalUncompressed = 0;
        int entryCount = 0;
        byte[] buffer = new byte[8192];
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (++entryCount > MAX_CANONICAL_ARCHIVE_ENTRIES) {
                    return null;
                }
                String entryName = entry.getName();
                // OOXML part names use forward-slash relative paths. Reject malformed or ambiguous archives
                // instead of allowing duplicate/traversal names to collapse into a misleading fingerprint.
                if (!isSafeArchiveEntryName(entryName) || !entryNames.add(entryName)) {
                    return null;
                }
                if (entry.isDirectory()) {
                    continue;
                }
                MessageDigest entryDigest = newSha256Digest();
                long entrySize = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    entrySize += count;
                    totalUncompressed += count;
                    if (totalUncompressed > MAX_CANONICAL_UNCOMPRESSED_BYTES) {
                        return null;
                    }
                    entryDigest.update(buffer, 0, count);
                }
                entryFingerprints.add(entryName + "\u0000" + entrySize + "\u0000"
                        + HexFormat.of().formatHex(entryDigest.digest()));
            }
        } catch (IOException | RuntimeException ex) {
            return null;
        }
        if (entryFingerprints.isEmpty()) {
            return null;
        }
        Collections.sort(entryFingerprints);
        MessageDigest archiveDigest = newSha256Digest();
        for (String fingerprint : entryFingerprints) {
            byte[] encoded = fingerprint.getBytes(StandardCharsets.UTF_8);
            archiveDigest.update(encoded);
            archiveDigest.update((byte) '\n');
        }
        return HexFormat.of().formatHex(archiveDigest.digest());
    }

    private static boolean isSafeArchiveEntryName(String entryName) {
        if (StrUtil.isBlank(entryName) || entryName.startsWith("/") || entryName.startsWith("\\")
                || entryName.contains("\\")) {
            return false;
        }
        for (String segment : entryName.split("/")) {
            if ("..".equals(segment) || ".".equals(segment)) {
                return false;
            }
        }
        return true;
    }

    private static MessageDigest newSha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JDK does not provide SHA-256", ex);
        }
    }

    static HttpClient buildCallbackHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(DOWNLOAD_TIMEOUT_MS))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    static HttpResponse.BodyHandler<byte[]> boundedBodyHandler(long maxBytes) {
        return responseInfo -> new BoundedBodySubscriber(maxBytes,
                responseInfo.headers().firstValueAsLong("Content-Length").orElse(-1L));
    }

    /**
     * HttpClient 的限长流式订阅器：分块到达时即检查大小，不让未知 Content-Length 的响应先完整进入内存。
     */
    static final class BoundedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {

        private final long maxBytes;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream(8192);
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private Flow.Subscription subscription;
        private long total;

        BoundedBodySubscriber(long maxBytes, long declaredLength) {
            this.maxBytes = maxBytes;
            if (maxBytes < 0 || declaredLength > maxBytes) {
                body.completeExceptionally(new IOException("response body exceeds limit"));
            }
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(Flow.Subscription newSubscription) {
            if (subscription != null) {
                newSubscription.cancel();
                return;
            }
            subscription = newSubscription;
            if (body.isCompletedExceptionally()) {
                subscription.cancel();
                return;
            }
            subscription.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            if (body.isDone()) {
                subscription.cancel();
                return;
            }
            for (ByteBuffer buffer : buffers) {
                int remaining = buffer.remaining();
                total += remaining;
                if (total > maxBytes) {
                    subscription.cancel();
                    body.completeExceptionally(new IOException("response body exceeds limit"));
                    return;
                }
                byte[] chunk = new byte[remaining];
                buffer.get(chunk);
                output.writeBytes(chunk);
            }
            subscription.request(1);
        }

        @Override
        public void onError(Throwable throwable) {
            body.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            body.complete(output.toByteArray());
        }
    }

    static URI validateDownloadUri(String documentServerUrl, String callbackUrl) {
        URI configured = parseHttpUri(documentServerUrl);
        URI candidate = parseHttpUri(callbackUrl);
        if (candidate.getRawUserInfo() != null || candidate.getRawFragment() != null
                || configured.getRawUserInfo() != null) {
            throw exception(ONLINE_EDIT_CALLBACK_FETCH_FAILED);
        }
        if (!configured.getScheme().equalsIgnoreCase(candidate.getScheme())
                || !configured.getHost().equalsIgnoreCase(candidate.getHost())
                || effectivePort(configured) != effectivePort(candidate)) {
            throw exception(ONLINE_EDIT_CALLBACK_FETCH_FAILED);
        }
        return candidate;
    }

    private static URI parseHttpUri(String value) {
        if (StrUtil.isBlank(value)) {
            throw exception(ONLINE_EDIT_CALLBACK_FETCH_FAILED);
        }
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            if ((!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))
                    || StrUtil.isBlank(uri.getHost())) {
                throw exception(ONLINE_EDIT_CALLBACK_FETCH_FAILED);
            }
            return uri;
        } catch (URISyntaxException ex) {
            throw exception(ONLINE_EDIT_CALLBACK_FETCH_FAILED);
        }
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static <T> T executeInTenant(Long tenantId, Supplier<T> action) {
        AtomicReference<T> result = new AtomicReference<>();
        TenantUtils.execute(tenantId, () -> result.set(action.get()));
        return result.get();
    }

    private static Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String toStringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

}
