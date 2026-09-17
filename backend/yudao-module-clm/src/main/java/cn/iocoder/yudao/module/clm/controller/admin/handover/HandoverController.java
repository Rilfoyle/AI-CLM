package cn.iocoder.yudao.module.clm.controller.admin.handover;

import cn.iocoder.yudao.module.clm.handover.*;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@RestController
@RequestMapping("/clm/handover")
@Validated
public class HandoverController {

    @Resource private HandoverService handoverService;

    /** 组织同步的受控入口；响应刻意不包含合同或审批任务内容。 */
    @PostMapping("/open/refresh")
    @PreAuthorize("@ss.hasPermission('clm:integration:run')")
    public CommonResult<HandoverRefreshRespVO> refresh(@Valid @RequestBody HandoverRefreshReqVO reqVO) {
        return success(handoverService.refresh(reqVO.getSourceUserId(), getLoginUserId()));
    }

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('clm:handover:query')")
    public CommonResult<PageResult<HandoverCaseSummaryRespVO>> getPage(@Valid HandoverPageReqVO reqVO) {
        return success(handoverService.getPage(reqVO));
    }

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('clm:handover:query')")
    public CommonResult<HandoverDetailRespVO> get(@RequestParam("id") Long id) {
        return success(handoverService.get(id));
    }

    @PutMapping("/reassign")
    @PreAuthorize("@ss.hasPermission('clm:handover:reassign-task')")
    public CommonResult<HandoverReassignRespVO> reassign(
            @Valid @RequestBody HandoverReassignReqVO reqVO) {
        return success(handoverService.reassign(reqVO, getLoginUserId()));
    }
}
