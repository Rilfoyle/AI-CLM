package cn.iocoder.yudao.module.clm.controller.admin.template;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.clm.template.*;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@Validated
public class TemplateController {
    @Resource private TemplateService templateService;
    @Resource private ContractDraftService draftService;
    @Resource private TemplateUpgradeService upgradeService;

    @GetMapping("/clm/template/published-page")
    @PreAuthorize("@ss.hasPermission('clm:template:query')")
    public CommonResult<PageResult<PublishedTemplateRespVO>> publishedPage(@Valid TemplatePageReqVO reqVO) {
        return success(templateService.getPublishedPage(reqVO));
    }

    @PostMapping("/clm/contract/create-from-template")
    @PreAuthorize("@ss.hasPermission('clm:contract:create')")
    public CommonResult<Long> createFromTemplate(@Valid @RequestBody CreateFromTemplateReqVO reqVO) {
        return success(draftService.createFromTemplate(reqVO));
    }

    @PostMapping(value = "/clm/contract/create-from-upload", consumes = "multipart/form-data")
    @PreAuthorize("@ss.hasPermission('clm:contract:create')")
    public CommonResult<Long> createFromUpload(@RequestParam("file") MultipartFile file,
                                               @RequestParam("name") String name,
                                               @RequestParam("contractTypeId") Long contractTypeId,
                                               @RequestParam(value = "ownerUserId", required = false) Long ownerUserId)
            throws IOException {
        return success(draftService.createFromUpload(name, contractTypeId, ownerUserId,
                StrUtil.blankToDefault(file.getOriginalFilename(), "contract.docx"), file.getContentType(), file.getBytes()));
    }

    @GetMapping("/clm/template/upgrade-preview")
    @PreAuthorize("@ss.hasPermission('clm:template:query')")
    public CommonResult<TemplateUpgradePreviewRespVO> upgradePreview(@RequestParam("contractId") Long contractId) {
        return success(upgradeService.preview(contractId));
    }

    @PutMapping("/clm/template/upgrade")
    @PreAuthorize("@ss.hasPermission('clm:contract:update')")
    public CommonResult<TemplateUpgradeRespVO> upgrade(@Valid @RequestBody TemplateUpgradeReqVO reqVO) {
        return success(upgradeService.upgrade(reqVO));
    }
}
