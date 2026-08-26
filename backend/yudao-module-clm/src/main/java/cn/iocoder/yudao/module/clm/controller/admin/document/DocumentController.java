package cn.iocoder.yudao.module.clm.controller.admin.document;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.http.HttpUtils;
import cn.iocoder.yudao.module.clm.controller.admin.document.vo.DocumentVersionRespVO;
import cn.iocoder.yudao.module.clm.dal.dataobject.document.DocumentVersionDO;
import cn.iocoder.yudao.module.clm.service.document.DocumentDownloadResult;
import cn.iocoder.yudao.module.clm.service.document.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - CLM 文档版本")
@RestController
@RequestMapping("/clm/document")
@Validated
public class DocumentController {

    private static final String DEFAULT_MIME_TYPE = "application/octet-stream";

    @Resource
    private DocumentService documentService;

    @GetMapping("/version/get")
    @Operation(summary = "获得文档版本")
    @Parameter(name = "id", description = "版本编号", required = true)
    @PreAuthorize("@ss.hasPermission('clm:contract:query')")
    public CommonResult<DocumentVersionRespVO> getDocumentVersion(@RequestParam("id") Long id) {
        return success(documentService.getDocumentVersionDetail(id));
    }

    @GetMapping("/version/download")
    @Operation(summary = "下载文档版本（私有存储，需对象权限）")
    @Parameter(name = "id", description = "版本编号", required = true)
    @PreAuthorize("@ss.hasPermission('clm:contract:download')")
    public void downloadDocumentVersion(@RequestParam("id") Long id, HttpServletResponse response) throws IOException {
        DocumentDownloadResult result = documentService.downloadDocumentVersion(id);
        DocumentVersionDO version = result.getVersion();
        byte[] content = result.getContent();
        String filename = StrUtil.blankToDefault(version.getFileName(), "document");
        response.setContentType(StrUtil.blankToDefault(version.getMimeType(), DEFAULT_MIME_TYPE));
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, buildContentDisposition(filename));
        response.setContentLengthLong(content.length);
        IoUtil.write(response.getOutputStream(), false, content);
    }

    /**
     * 构建 RFC 5987 的 Content-Disposition：attachment; filename="fallback"; filename*=UTF-8''encoded
     */
    static String buildContentDisposition(String filename) {
        String encoded = HttpUtils.encodeUrlPathSegment(filename);
        String fallback = filename.replaceAll("[^\\x20-\\x7E]", "_").replace("\"", "_");
        return StrUtil.format("attachment; filename=\"{}\"; filename*=UTF-8''{}", fallback, encoded);
    }

}
