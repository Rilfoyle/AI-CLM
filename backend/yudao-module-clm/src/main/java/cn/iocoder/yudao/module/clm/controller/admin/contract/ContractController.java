package cn.iocoder.yudao.module.clm.controller.admin.contract;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.clm.controller.admin.contract.vo.*;
import cn.iocoder.yudao.module.clm.service.contract.ContractService;
import cn.iocoder.yudao.module.clm.service.workflow.ContractWorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - CLM 合同")
@RestController
@RequestMapping("/clm/contract")
@Validated
public class ContractController {

    @Resource
    private ContractService contractService;
    @Resource
    private ContractWorkflowService contractWorkflowService;

    @PostMapping("/create")
    @Operation(summary = "创建合同（草稿）")
    @PreAuthorize("@ss.hasPermission('clm:contract:create')")
    public CommonResult<Long> createContract(@Valid @RequestBody ContractSaveReqVO createReqVO) {
        return success(contractService.createContract(createReqVO));
    }

    @PostMapping("/copy")
    @Operation(summary = "复制/续签合同")
    @PreAuthorize("@ss.hasPermission('clm:contract:create')")
    public CommonResult<Long> copyContract(@Valid @RequestBody ContractCopyReqVO reqVO) {
        return success(contractService.copyContract(reqVO));
    }

    @PostMapping("/archive")
    @Operation(summary = "归档定稿：上传盖章扫描件，生成 MAIN 冻结版本并推进为已签订")
    @PreAuthorize("@ss.hasPermission('clm:contract:update')")
    public CommonResult<Long> archiveContract(
            @RequestParam("contractId") Long contractId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "signDate", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate signDate,
            @RequestParam(value = "effectiveDate", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate effectiveDate,
            @RequestParam(value = "expiryDate", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate expiryDate) throws IOException {
        return success(contractService.archiveContract(contractId, signDate, effectiveDate, expiryDate,
                file.getOriginalFilename(), file.getContentType(), file.getBytes()));
    }

    @PutMapping("/update")
    @Operation(summary = "更新合同")
    @PreAuthorize("@ss.hasPermission('clm:contract:update')")
    public CommonResult<Boolean> updateContract(@Valid @RequestBody ContractSaveReqVO updateReqVO) {
        contractService.updateContract(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除合同")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('clm:contract:delete')")
    public CommonResult<Boolean> deleteContract(@RequestParam("id") Long id) {
        contractService.deleteContract(id);
        return success(true);
    }

    @PutMapping("/restore-draft")
    @Operation(summary = "从回收站恢复草稿")
    @Parameter(name = "id", description = "合同编号", required = true)
    @PreAuthorize("@ss.hasPermission('clm:contract:restore')")
    public CommonResult<Boolean> restoreDraft(@RequestParam("id") Long id) {
        contractService.restoreDraft(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得合同详情")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('clm:contract:query')")
    public CommonResult<ContractRespVO> getContract(@RequestParam("id") Long id) {
        return success(contractService.getContractDetail(id));
    }

    @GetMapping("/deleted-get")
    @Operation(summary = "获得回收站草稿详情（仅原负责人）")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('clm:contract:query')")
    public CommonResult<ContractRespVO> getDeletedContract(@RequestParam("id") Long id) {
        return success(contractService.getDeletedContractDetail(id));
    }

    @GetMapping("/page")
    @Operation(summary = "获得合同分页（仅当前用户可见的合同）")
    @PreAuthorize("@ss.hasPermission('clm:contract:query')")
    public CommonResult<PageResult<ContractRespVO>> getContractPage(@Valid ContractPageReqVO pageReqVO) {
        return success(contractService.getContractPage(pageReqVO));
    }

    @PostMapping("/submit")
    @Operation(summary = "提交审批")
    @PreAuthorize("@ss.hasPermission('clm:contract:submit')")
    public CommonResult<ContractSubmitRespVO> submitContract(@Valid @RequestBody ContractSubmitReqVO reqVO) {
        return success(contractWorkflowService.submit(reqVO));
    }

    @GetMapping("/approval-preview")
    @Operation(summary = "审批预览：流程定义信息")
    @Parameter(name = "id", description = "合同编号", required = true)
    @PreAuthorize("@ss.hasPermission('clm:contract:query')")
    public CommonResult<ContractApprovalPreviewRespVO> getApprovalPreview(@RequestParam("id") Long id) {
        return success(contractWorkflowService.getApprovalPreview(id));
    }

}
