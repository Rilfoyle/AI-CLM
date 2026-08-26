package cn.iocoder.yudao.module.clm.controller.admin.contracttype;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.http.HttpUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.clm.controller.admin.contracttype.vo.*;
import cn.iocoder.yudao.module.clm.dal.dataobject.contracttype.ContractTypeVersionDO;
import cn.iocoder.yudao.module.clm.service.contracttype.ContractTypeService;
import cn.iocoder.yudao.module.clm.service.contracttype.TypeTemplateDownloadResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.clm.enums.ErrorCodeConstants.CONTRACT_TYPE_VERSION_NOT_EXISTS;

@Tag(name = "管理后台 - CLM 合同类型")
@RestController
@RequestMapping("/clm/contract-type")
@Validated
public class ContractTypeController {

    @Resource
    private ContractTypeService contractTypeService;

    @PostMapping("/create")
    @Operation(summary = "创建合同类型（同时创建版本 1 草稿）")
    @PreAuthorize("@ss.hasPermission('clm:contract-type:create')")
    public CommonResult<Long> createContractType(@Valid @RequestBody ContractTypeSaveReqVO createReqVO) {
        return success(contractTypeService.createContractType(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新合同类型")
    @PreAuthorize("@ss.hasPermission('clm:contract-type:update')")
    public CommonResult<Boolean> updateContractType(@Valid @RequestBody ContractTypeSaveReqVO updateReqVO) {
        contractTypeService.updateContractType(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除合同类型")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('clm:contract-type:delete')")
    public CommonResult<Boolean> deleteContractType(@RequestParam("id") Long id) {
        contractTypeService.deleteContractType(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得合同类型")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('clm:contract-type:query')")
    public CommonResult<ContractTypeRespVO> getContractType(@RequestParam("id") Long id) {
        return success(contractTypeService.getContractTypeDetail(id));
    }

    @GetMapping("/page")
    @Operation(summary = "获得合同类型分页")
    @PreAuthorize("@ss.hasPermission('clm:contract-type:query')")
    public CommonResult<PageResult<ContractTypeRespVO>> getContractTypePage(@Valid ContractTypePageReqVO pageReqVO) {
        return success(contractTypeService.getContractTypePage(pageReqVO));
    }

    @GetMapping("/simple-list")
    @Operation(summary = "获得合同类型精简列表（仅开启且已发布）")
    public CommonResult<List<ContractTypeSimpleRespVO>> getContractTypeSimpleList() {
        return success(contractTypeService.getContractTypeSimpleList());
    }

    // ========== 范本 ==========

    @PostMapping("/template/upload")
    @Operation(summary = "上传/替换合同类型范本文件（.docx/.doc/.pdf）")
    @PreAuthorize("@ss.hasPermission('clm:contract-type:update')")
    public CommonResult<Boolean> uploadContractTypeTemplate(@RequestParam("typeId") Long typeId,
                                                            @RequestParam("file") MultipartFile file) throws IOException {
        contractTypeService.uploadContractTypeTemplate(typeId, file.getOriginalFilename(), file.getBytes());
        return success(true);
    }

    @GetMapping("/template/download")
    @Operation(summary = "下载合同类型范本文件（登录即可）")
    @Parameter(name = "typeId", description = "合同类型编号", required = true)
    public void downloadContractTypeTemplate(@RequestParam("typeId") Long typeId,
                                             HttpServletResponse response) throws IOException {
        TypeTemplateDownloadResult result = contractTypeService.downloadContractTypeTemplate(typeId);
        String fileName = result.getFileName();
        response.setContentType(resolveMimeType(fileName));
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, buildContentDisposition(fileName));
        response.setContentLengthLong(result.getContent().length);
        IoUtil.write(response.getOutputStream(), false, result.getContent());
    }

    private static String resolveMimeType(String fileName) {
        String extension = StrUtil.emptyToDefault(FileUtil.extName(fileName), "").toLowerCase();
        switch (extension) {
            case "pdf":
                return "application/pdf";
            case "doc":
                return "application/msword";
            case "docx":
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            default:
                return "application/octet-stream";
        }
    }

    /**
     * 构建 RFC 5987 的 Content-Disposition：attachment; filename="fallback"; filename*=UTF-8''encoded
     */
    private static String buildContentDisposition(String filename) {
        String encoded = HttpUtils.encodeUrlPathSegment(filename);
        String fallback = filename.replaceAll("[^\\x20-\\x7E]", "_").replace("\"", "_");
        return StrUtil.format("attachment; filename=\"{}\"; filename*=UTF-8''{}", fallback, encoded);
    }

    // ========== 版本 ==========

    @GetMapping("/version/list")
    @Operation(summary = "获得合同类型版本列表（按版本号降序）")
    @Parameter(name = "typeId", description = "合同类型编号", required = true)
    @PreAuthorize("@ss.hasPermission('clm:contract-type:query')")
    public CommonResult<List<ContractTypeVersionRespVO>> getContractTypeVersionList(@RequestParam("typeId") Long typeId) {
        List<ContractTypeVersionDO> list = contractTypeService.getContractTypeVersionList(typeId);
        return success(BeanUtils.toBean(list, ContractTypeVersionRespVO.class));
    }

    @GetMapping("/version/get")
    @Operation(summary = "获得合同类型版本")
    @Parameter(name = "id", description = "版本编号", required = true)
    public CommonResult<ContractTypeVersionRespVO> getContractTypeVersion(@RequestParam("id") Long id) {
        ContractTypeVersionDO version = contractTypeService.getContractTypeVersion(id);
        if (version == null) {
            throw exception(CONTRACT_TYPE_VERSION_NOT_EXISTS);
        }
        return success(BeanUtils.toBean(version, ContractTypeVersionRespVO.class));
    }

    @PutMapping("/version/update")
    @Operation(summary = "更新合同类型草稿版本")
    @PreAuthorize("@ss.hasPermission('clm:contract-type:update')")
    public CommonResult<Boolean> updateContractTypeVersion(@Valid @RequestBody ContractTypeVersionSaveReqVO updateReqVO) {
        contractTypeService.updateContractTypeVersion(updateReqVO);
        return success(true);
    }

    @PostMapping("/version/publish")
    @Operation(summary = "发布合同类型版本")
    @Parameter(name = "id", description = "版本编号", required = true)
    @PreAuthorize("@ss.hasPermission('clm:contract-type:publish')")
    public CommonResult<Boolean> publishContractTypeVersion(@RequestParam("id") Long id) {
        contractTypeService.publishContractTypeVersion(id);
        return success(true);
    }

    @PostMapping("/version/create-draft")
    @Operation(summary = "新建合同类型草稿版本")
    @Parameter(name = "typeId", description = "合同类型编号", required = true)
    @PreAuthorize("@ss.hasPermission('clm:contract-type:update')")
    public CommonResult<Long> createDraftVersion(@RequestParam("typeId") Long typeId) {
        return success(contractTypeService.createDraftVersion(typeId));
    }

}
