package cn.iocoder.yudao.module.clm.controller.admin.onlineedit;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.http.HttpUtils;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.clm.dal.dataobject.document.DocumentVersionDO;
import cn.iocoder.yudao.module.clm.service.document.DocumentDownloadResult;
import cn.iocoder.yudao.module.clm.service.onlineedit.OnlineEditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 供 ONLYOFFICE Document Server 访问的开放接口
 *
 * 不依赖登录态与 tenant-id 请求头：由 {@link cn.iocoder.yudao.module.clm.onlyoffice.OnlyOfficeTokenService} 的紧凑令牌鉴权，
 * 租户从令牌中取出后通过 TenantUtils.execute 切换。
 * URL 同时在 clm 的 SecurityConfiguration 中放行。
 */
@Tag(name = "管理后台 - CLM 在线编辑（Document Server 开放接口）")
@RestController
@RequestMapping("/clm/online-edit")
@TenantIgnore
@Slf4j
public class OnlineEditOpenController {

    private static final String DEFAULT_MIME_TYPE = "application/octet-stream";

    @Resource
    private OnlineEditService onlineEditService;

    @GetMapping("/file")
    @PermitAll
    @Operation(summary = "Document Server 拉取文档版本内容（token 鉴权，不写下载审计）")
    @Parameter(name = "token", description = "file 用途的访问令牌", required = true)
    public void getFile(@RequestParam("token") String token, HttpServletResponse response) throws IOException {
        DocumentDownloadResult result = onlineEditService.loadFile(token);
        DocumentVersionDO version = result.getVersion();
        byte[] content = result.getContent();
        String filename = StrUtil.blankToDefault(version.getFileName(), "document");
        response.setContentType(StrUtil.blankToDefault(version.getMimeType(), DEFAULT_MIME_TYPE));
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, buildContentDisposition(filename));
        response.setContentLengthLong(content.length);
        IoUtil.write(response.getOutputStream(), false, content);
    }

    @PostMapping("/callback")
    @PermitAll
    @Operation(summary = "Document Server 保存回调（token 鉴权 + JWT 校验）；返回 {\"error\":0} 或 {\"error\":1}")
    @Parameter(name = "token", description = "callback 用途的访问令牌", required = true)
    public Map<String, Object> callback(@RequestParam("token") String token,
                                        @RequestBody(required = false) Map<String, Object> body,
                                        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            onlineEditService.handleCallback(token, body, obtainJwt(body, authorization));
            result.put("error", 0);
        } catch (ServiceException ex) {
            // 令牌 / JWT 失败、拉取失败等：返回 error 1，Document Server 会重试保存
            log.warn("[callback][回调处理失败 code({}) msg({})]", ex.getCode(), ex.getMessage());
            result.put("error", 1);
            result.put("message", ex.getMessage());
        } catch (Exception ex) {
            log.error("[callback][回调处理异常]", ex);
            result.put("error", 1);
        }
        return result;
    }

    /**
     * Document Server 的 JWT：优先 body.token，其次 Authorization: Bearer xxx
     */
    private static String obtainJwt(Map<String, Object> body, String authorization) {
        if (body != null && body.get("token") != null && StrUtil.isNotBlank(String.valueOf(body.get("token")))) {
            return String.valueOf(body.get("token"));
        }
        if (StrUtil.isBlank(authorization)) {
            return null;
        }
        String value = authorization.trim();
        if (StrUtil.startWithIgnoreCase(value, "Bearer ")) {
            value = value.substring("Bearer ".length()).trim();
        }
        return StrUtil.blankToDefault(value, null);
    }

    /**
     * 构建 RFC 5987 的 Content-Disposition：attachment; filename="fallback"; filename*=UTF-8''encoded
     */
    private static String buildContentDisposition(String filename) {
        String encoded = HttpUtils.encodeUrlPathSegment(filename);
        String fallback = filename.replaceAll("[^\\x20-\\x7E]", "_").replace("\"", "_");
        return StrUtil.format("attachment; filename=\"{}\"; filename*=UTF-8''{}", fallback, encoded);
    }

}
