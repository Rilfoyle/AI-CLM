package cn.iocoder.yudao.module.clm.service.onlineedit;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
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

import java.util.*;

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
        return TenantUtils.execute(payload.getT(), () -> {
            DocumentVersionDO version = documentService.getRequiredDocumentVersion(payload.getV());
            assertPayloadMatches(payload, version);
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
        log.info("[handleCallback][版本({}) 回调 status({}) key({}) users({}) forcesavetype({}) url({})]",
                payload.getV(), status, key, users, forceSaveType, url);
        // 2. 非保存状态只记日志
        if (status == null || !SAVE_STATUSES.contains(status)) {
            return null;
        }
        // 3. 拉取新文件
        byte[] content = downloadContent(url, payload.getV());
        String sha256 = DigestUtil.sha256Hex(content);
        Integer finalStatus = status;
        return TenantUtils.execute(payload.getT(), () -> {
            DocumentVersionDO parent = documentService.getRequiredDocumentVersion(payload.getV());
            assertPayloadMatches(payload, parent);
            DocumentDO document = documentMapper.selectById(parent.getDocumentId());
            if (document == null) {
                throw exception(DOCUMENT_NOT_EXISTS);
            }
            // 4. 幂等：文档当前版本内容相同则不新建
            DocumentVersionDO current = document.getCurrentVersionId() == null ? null
                    : documentService.getDocumentVersion(document.getCurrentVersionId());
            if (current != null && StrUtil.equalsIgnoreCase(current.getChecksumSha256(), sha256)) {
                log.info("[handleCallback][版本({}) 回调内容与当前版本({}) 相同，幂等跳过]", parent.getId(), current.getId());
                return null;
            }
            // 5. 新建版本（审批中 / 父版本已冻结 / 父版本已非当前版本 仍然保存，但审计标记 lateSave）
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
                    ClmDocumentSourceTypeEnum.ONLINE_EDIT, remark, payload.getU(), extra);
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

    private byte[] downloadContent(String url, Long versionId) {
        if (StrUtil.isBlank(url)) {
            log.warn("[downloadContent][版本({}) 回调未携带 url]", versionId);
            throw exception(ONLINE_EDIT_CALLBACK_FETCH_FAILED);
        }
        byte[] content;
        try (HttpResponse response = HttpUtil.createGet(url).timeout(DOWNLOAD_TIMEOUT_MS).execute()) {
            // 注意：本模块的 /file 接口在令牌失败时返回 HTTP 200 的 JSON 错误体，这里按 Content-Type 识别并拒绝
            String contentType = StrUtil.nullToEmpty(response.header("Content-Type")).toLowerCase();
            if (!response.isOk() || contentType.contains("application/json")) {
                log.warn("[downloadContent][版本({}) 拉取 url({}) 失败: status({}) contentType({})]",
                        versionId, url, response.getStatus(), contentType);
                throw exception(ONLINE_EDIT_CALLBACK_FETCH_FAILED);
            }
            content = response.bodyBytes();
        } catch (ServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("[downloadContent][版本({}) 拉取 url({}) 失败: {}]", versionId, url, ex.getMessage());
            throw exception(ONLINE_EDIT_CALLBACK_FETCH_FAILED);
        }
        if (ArrayUtil.isEmpty(content)) {
            log.warn("[downloadContent][版本({}) 拉取 url({}) 内容为空]", versionId, url);
            throw exception(ONLINE_EDIT_CALLBACK_FETCH_FAILED);
        }
        return content;
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
