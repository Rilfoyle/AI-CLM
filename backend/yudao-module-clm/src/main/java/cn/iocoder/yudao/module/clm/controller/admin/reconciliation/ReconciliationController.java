package cn.iocoder.yudao.module.clm.controller.admin.reconciliation;

import cn.iocoder.yudao.module.clm.reconciliation.*;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@RestController
@RequestMapping("/clm/reconciliation")
@Validated
public class ReconciliationController {
    @Resource private ReconciliationService reconciliationService;

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('clm:reconciliation:query')")
    public CommonResult<PageResult<ReconciliationRunDO>> getPage(@Valid PageParam pageParam) {
        return success(reconciliationService.getPage(pageParam));
    }

    @PostMapping("/run")
    @PreAuthorize("@ss.hasPermission('clm:reconciliation:replay')")
    public CommonResult<Long> run(@Valid @RequestBody ReconciliationRunReqVO reqVO) {
        return success(reconciliationService.run(reqVO));
    }

    @PutMapping("/confirm")
    @PreAuthorize("@ss.hasPermission('clm:reconciliation:confirm')")
    public CommonResult<Boolean> confirm(@Valid @RequestBody ReconciliationConfirmReqVO reqVO) {
        return success(reconciliationService.confirm(reqVO, getLoginUserId()));
    }
}
