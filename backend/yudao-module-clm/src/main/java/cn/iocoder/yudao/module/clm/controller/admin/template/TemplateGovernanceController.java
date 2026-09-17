package cn.iocoder.yudao.module.clm.controller.admin.template;

import cn.iocoder.yudao.module.clm.template.*;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.http.HttpUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/clm/governance/template")
@Validated
public class TemplateGovernanceController {

    @Resource
    private TemplateService templateService;

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('clm:governance:template:query')")
    public CommonResult<PageResult<TemplateGovernanceRespVO>> page(@Valid TemplateGovernancePageReqVO reqVO) {
        return success(templateService.getGovernancePage(reqVO));
    }

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('clm:governance:template:query')")
    public CommonResult<TemplateGovernanceRespVO> get(@RequestParam("id") Long id) {
        return success(templateService.getGovernanceDetail(id));
    }

    @PostMapping(value = "/save-draft", consumes = "multipart/form-data")
    @PreAuthorize("@ss.hasPermission('clm:governance:template:update')")
    public CommonResult<Long> saveDraft(@Valid @ModelAttribute TemplateDraftSaveReqVO reqVO) throws IOException {
        return success(templateService.saveDraft(reqVO));
    }

    @PutMapping("/publish")
    @PreAuthorize("@ss.hasPermission('clm:governance:template:publish')")
    public CommonResult<Boolean> publish(@RequestParam("id") Long id) {
        templateService.publish(id);
        return success(true);
    }

    @PutMapping("/disable")
    @PreAuthorize("@ss.hasPermission('clm:governance:template:publish')")
    public CommonResult<Boolean> disable(@RequestParam("id") Long id) {
        templateService.disable(id);
        return success(true);
    }

    @GetMapping("/file")
    @PreAuthorize("@ss.hasPermission('clm:governance:template:query')")
    public void file(@RequestParam("versionId") Long versionId, HttpServletResponse response) throws IOException {
        TemplateFileResult result = templateService.getFile(versionId);
        response.setContentType(StrUtil.blankToDefault(result.getMimeType(), "application/octet-stream"));
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, buildContentDisposition(result.getFileName()));
        response.setContentLengthLong(result.getContent().length);
        IoUtil.write(response.getOutputStream(), false, result.getContent());
    }

    private static String buildContentDisposition(String filename) {
        String safeName = StrUtil.blankToDefault(filename, "template");
        String encoded = HttpUtils.encodeUrlPathSegment(safeName);
        String fallback = safeName.replaceAll("[^\\x20-\\x7E]", "_").replace("\"", "_");
        return StrUtil.format("attachment; filename=\"{}\"; filename*=UTF-8''{}", fallback, encoded);
    }
}
