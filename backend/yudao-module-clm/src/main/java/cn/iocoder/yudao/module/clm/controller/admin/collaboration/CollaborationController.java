package cn.iocoder.yudao.module.clm.controller.admin.collaboration;

import cn.iocoder.yudao.module.clm.collaboration.*;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - CLM 法务协同")
@RestController
@RequestMapping("/clm/collaboration")
@Validated
public class CollaborationController {

    @Resource private CollaborationService collaborationService;

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('clm:collaboration:query')")
    public CommonResult<PageResult<CollaborationCaseRespVO>> getPage(@Valid CollaborationPageReqVO reqVO) {
        return success(collaborationService.getPage(reqVO, getLoginUserId()));
    }

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('clm:collaboration:query')")
    public CommonResult<CollaborationCaseRespVO> get(@RequestParam("id") Long id) {
        return success(collaborationService.get(id, getLoginUserId()));
    }

    @PostMapping("/start")
    @PreAuthorize("@ss.hasPermission('clm:collaboration:start')")
    public CommonResult<Long> start(@Valid @RequestBody CollaborationStartReqVO reqVO) {
        return success(collaborationService.start(reqVO, getLoginUserId()));
    }

    @PostMapping("/comment")
    @PreAuthorize("@ss.hasPermission('clm:collaboration:update')")
    public CommonResult<Boolean> comment(@Valid @RequestBody CollaborationActionReqVO reqVO) {
        collaborationService.comment(reqVO, getLoginUserId());
        return success(true);
    }

    @PutMapping("/request-change")
    @PreAuthorize("@ss.hasPermission('clm:collaboration:update')")
    public CommonResult<Boolean> requestChange(@Valid @RequestBody CollaborationActionReqVO reqVO) {
        collaborationService.requestChange(reqVO, getLoginUserId());
        return success(true);
    }

    @PutMapping("/complete")
    @PreAuthorize("@ss.hasPermission('clm:collaboration:update')")
    public CommonResult<Boolean> complete(@Valid @RequestBody CollaborationCompleteReqVO reqVO) {
        collaborationService.complete(reqVO.toActionReqVO(), getLoginUserId());
        return success(true);
    }

    @PutMapping("/cancel")
    @PreAuthorize("@ss.hasPermission('clm:collaboration:start')")
    public CommonResult<Boolean> cancel(@Valid @RequestBody CollaborationCancelReqVO reqVO) {
        collaborationService.cancel(reqVO.toActionReqVO(), getLoginUserId());
        return success(true);
    }

}
