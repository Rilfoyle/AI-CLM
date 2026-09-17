package cn.iocoder.yudao.module.clm.controller.admin.partyimport;

import cn.iocoder.yudao.module.clm.partyimport.*;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.excel.core.util.ExcelUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/clm/party-import")
@Validated
public class PartyImportController {
    @Resource private PartyImportService service;

    @GetMapping("/template")
    @PreAuthorize("@ss.hasPermission('clm:party-import:query')")
    public void template(HttpServletResponse response) throws IOException {
        ExcelUtils.write(response, "相对方导入模板.xlsx", "相对方", PartyImportExcelRow.class, service.templateRows());
    }

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    @PreAuthorize("@ss.hasPermission('clm:party-import:create')")
    public CommonResult<Long> upload(@RequestParam("file") MultipartFile file,
                                     @NotBlank @RequestParam("jobKey") String jobKey) {
        return success(service.upload(file, jobKey));
    }

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('clm:party-import:query')")
    public CommonResult<PageResult<PartyImportJobRespVO>> page(@Valid PartyImportPageReqVO reqVO) {
        return success(service.page(reqVO));
    }

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('clm:party-import:query')")
    public CommonResult<PartyImportJobRespVO> get(@RequestParam("id") Long id) {
        return success(service.get(id));
    }

    @GetMapping("/item/page")
    @PreAuthorize("@ss.hasPermission('clm:party-import:query')")
    public CommonResult<PageResult<PartyImportItemRespVO>> itemPage(@Valid PartyImportItemPageReqVO reqVO) {
        return success(service.itemPage(reqVO));
    }

    @PutMapping("/item/update")
    @PreAuthorize("@ss.hasPermission('clm:party-import:create')")
    public CommonResult<PartyImportItemRespVO> updateItem(@Valid @RequestBody PartyImportItemUpdateReqVO reqVO) {
        return success(service.updateItem(reqVO));
    }

    @PutMapping("/confirm")
    @PreAuthorize("@ss.hasPermission('clm:party-import:confirm')")
    public CommonResult<PartyImportJobRespVO> confirm(@RequestParam("id") Long id,
                                                      @NotBlank @RequestParam("requestId") String requestId) {
        return success(service.confirm(id, requestId));
    }

    @PutMapping("/retry-failed")
    @PreAuthorize("@ss.hasPermission('clm:party-import:confirm')")
    public CommonResult<PartyImportJobRespVO> retryFailed(@RequestParam("id") Long id,
                                                          @NotBlank @RequestParam("requestId") String requestId) {
        return success(service.retryFailed(id, requestId));
    }

    @GetMapping("/failed-file")
    @PreAuthorize("@ss.hasPermission('clm:party-import:query')")
    public void failedFile(@RequestParam("id") Long id, HttpServletResponse response) throws IOException {
        ExcelUtils.write(response, "相对方导入失败行.xlsx", "失败行", PartyImportFailedExcelRow.class,
                service.failedRows(id));
    }
}
