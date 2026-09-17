package cn.iocoder.yudao.module.clm.controller.admin.approval;

import cn.iocoder.yudao.module.clm.approval.*;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskCopyReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskSignCreateReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskTransferReqVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/clm/approval")
public class ApprovalController {
    @Resource private ApprovalService approvalService;

    @GetMapping("/task/get")
    @PreAuthorize("@ss.hasPermission('clm:approval:query')")
    public CommonResult<ApprovalTaskDetailRespVO> getTask(@RequestParam("taskId") String taskId) {
        return success(approvalService.getTask(taskId));
    }
    @PutMapping("/task/approve")
    @PreAuthorize("@ss.hasPermission('clm:approval:approve')")
    public CommonResult<Boolean> approve(@Valid @RequestBody ApprovalDecisionReqVO reqVO) {
        approvalService.approve(reqVO); return success(true);
    }
    @PutMapping("/task/reject")
    @PreAuthorize("@ss.hasPermission('clm:approval:reject')")
    public CommonResult<Boolean> reject(@Valid @RequestBody ApprovalDecisionReqVO reqVO) {
        approvalService.reject(reqVO); return success(true);
    }
    @PutMapping("/task/return")
    @PreAuthorize("@ss.hasPermission('clm:approval:return')")
    public CommonResult<Boolean> returnTask(@Valid @RequestBody ApprovalDecisionReqVO reqVO) {
        approvalService.returnTask(reqVO); return success(true);
    }
    @PutMapping("/task/transfer")
    @PreAuthorize("@ss.hasPermission('clm:approval:collaborate')")
    public CommonResult<Boolean> transfer(@Valid @RequestBody BpmTaskTransferReqVO reqVO) {
        approvalService.transfer(reqVO); return success(true);
    }
    @PutMapping("/task/copy")
    @PreAuthorize("@ss.hasPermission('clm:approval:collaborate')")
    public CommonResult<Boolean> copy(@Valid @RequestBody BpmTaskCopyReqVO reqVO) {
        approvalService.copy(reqVO); return success(true);
    }
    @PutMapping("/task/add-sign")
    @PreAuthorize("@ss.hasPermission('clm:approval:collaborate')")
    public CommonResult<Boolean> addSign(@Valid @RequestBody BpmTaskSignCreateReqVO reqVO) {
        approvalService.addSign(reqVO); return success(true);
    }
    @PutMapping("/task/withdraw")
    @PreAuthorize("@ss.hasPermission('clm:approval:collaborate')")
    public CommonResult<Boolean> withdrawTask(@RequestParam("taskId") String taskId) {
        approvalService.withdrawTask(taskId); return success(true);
    }
    @PutMapping("/case/withdraw")
    @PreAuthorize("@ss.hasPermission('clm:approval:withdraw-case')")
    public CommonResult<Boolean> withdrawCase(@Valid @RequestBody ApprovalCaseWithdrawReqVO reqVO) {
        approvalService.withdrawCase(reqVO); return success(true);
    }
    @PutMapping("/task/edit")
    @PreAuthorize("@ss.hasPermission('clm:approval:edit')")
    public CommonResult<ApprovalEditRespVO> edit(@Valid @RequestBody ApprovalEditReqVO reqVO) {
        return success(approvalService.edit(reqVO));
    }
    @PutMapping(value = "/task/edit-document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@ss.hasPermission('clm:approval:edit')")
    public CommonResult<ApprovalEditRespVO> editDocument(
            @Valid @ModelAttribute ApprovalDocumentEditReqVO reqVO,
            @RequestPart("file") MultipartFile file) throws IOException {
        return success(approvalService.editDocument(reqVO, file.getOriginalFilename(),
                file.getContentType(), file.getBytes()));
    }
    @GetMapping("/history")
    @PreAuthorize("@ss.hasPermission('clm:approval:history')")
    public CommonResult<ApprovalHistoryRespVO> history(@RequestParam("contractId") Long contractId) {
        return success(approvalService.getHistory(contractId));
    }
}
