package cn.iocoder.yudao.module.clm.controller.admin.workbench;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.clm.controller.admin.workbench.vo.ClmWorkbenchItemRespVO;
import cn.iocoder.yudao.module.clm.controller.admin.workbench.vo.ClmWorkbenchItemsReqVO;
import cn.iocoder.yudao.module.clm.controller.admin.workbench.vo.ClmWorkbenchSummaryRespVO;
import cn.iocoder.yudao.module.clm.service.workbench.ClmWorkbenchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - CLM 角色工作台")
@RestController
@RequestMapping("/clm/workbench")
@Validated
public class ClmWorkbenchController {

    @Resource
    private ClmWorkbenchService clmWorkbenchService;

    @GetMapping("/summary")
    @Operation(summary = "获得当前用户的合同工作项汇总")
    @PreAuthorize("@ss.hasPermission('clm:workbench:query')")
    public CommonResult<ClmWorkbenchSummaryRespVO> getSummary() {
        return success(clmWorkbenchService.getSummary(getLoginUserId()));
    }

    @GetMapping("/items")
    @Operation(summary = "获得当前用户的统一合同工作项")
    @PreAuthorize("@ss.hasPermission('clm:workbench:query')")
    public CommonResult<PageResult<ClmWorkbenchItemRespVO>> getItems(@Valid ClmWorkbenchItemsReqVO reqVO) {
        return success(clmWorkbenchService.getItems(reqVO, getLoginUserId()));
    }

}
