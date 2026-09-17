package cn.iocoder.yudao.module.clm.controller.admin.integration;

import cn.iocoder.yudao.module.clm.integration.*;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/clm/integration")
@Validated
public class IntegrationController {
    @Resource private IntegrationService integrationService;

    @GetMapping("/dingtalk/status")
    @PreAuthorize("@ss.hasPermission('clm:integration:query')")
    public CommonResult<IntegrationStatusRespVO> getStatus() {
        return success(integrationService.getStatus());
    }

    @PostMapping("/dingtalk/run-sandbox")
    @PreAuthorize("@ss.hasPermission('clm:integration:run')")
    public CommonResult<Long> runSandbox(@Valid @RequestBody IntegrationRunReqVO reqVO) {
        return success(integrationService.runSandbox(reqVO));
    }

    @GetMapping("/run/page")
    @PreAuthorize("@ss.hasPermission('clm:integration:query')")
    public CommonResult<PageResult<IntegrationRunDO>> getRunPage(@Valid PageParam pageParam,
            @RequestParam(value = "type", required = false) String type) {
        return success(integrationService.getRunPage(pageParam, type));
    }

    @GetMapping("/delivery/page")
    @PreAuthorize("@ss.hasPermission('clm:integration:query')")
    public CommonResult<PageResult<IntegrationDeliveryDO>> getDeliveryPage(@Valid PageParam pageParam,
            @RequestParam(value = "status", required = false) String status) {
        return success(integrationService.getDeliveryPage(pageParam, status));
    }

    @PutMapping("/delivery/retry")
    @PreAuthorize("@ss.hasPermission('clm:integration:run')")
    public CommonResult<Boolean> retry(@RequestParam("id") Long id) {
        integrationService.retryDelivery(id);
        return success(true);
    }
}
