package cn.iocoder.yudao.module.clm.controller.admin.contract;

import cn.hutool.core.io.IoUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.clm.controller.admin.document.vo.DocumentRespVO;
import cn.iocoder.yudao.module.clm.controller.admin.document.vo.DocumentUploadReqVO;
import cn.iocoder.yudao.module.clm.service.document.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - CLM 合同文档")
@RestController
@RequestMapping("/clm/contract/document")
@Validated
public class ContractDocumentController {

    @Resource
    private DocumentService documentService;

    @PostMapping("/upload")
    @Operation(summary = "上传合同文档（生成新版本）")
    @Parameter(name = "file", description = "文件", required = true,
            schema = @Schema(type = "string", format = "binary"))
    @PreAuthorize("@ss.hasPermission('clm:contract:update')")
    public CommonResult<Long> uploadDocument(@Valid DocumentUploadReqVO reqVO) throws IOException {
        MultipartFile file = reqVO.getFile();
        byte[] content = IoUtil.readBytes(file.getInputStream());
        Long versionId = documentService.uploadDocument(reqVO.getContractId(), reqVO.getRoleCode(), reqVO.getRemark(),
                file.getOriginalFilename(), file.getContentType(), content);
        return success(versionId);
    }

    @GetMapping("/list")
    @Operation(summary = "获得合同文档列表（含版本）")
    @Parameter(name = "contractId", description = "合同编号", required = true)
    @PreAuthorize("@ss.hasPermission('clm:contract:query')")
    public CommonResult<List<DocumentRespVO>> getDocumentList(@RequestParam("contractId") Long contractId) {
        return success(documentService.getDocumentList(contractId));
    }

}
