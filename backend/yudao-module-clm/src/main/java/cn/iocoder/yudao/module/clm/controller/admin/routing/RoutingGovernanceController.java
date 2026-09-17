package cn.iocoder.yudao.module.clm.controller.admin.routing;

import cn.iocoder.yudao.module.clm.routing.*;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/clm/governance/routing")
@Validated
public class RoutingGovernanceController {
    @Resource private RoutingGovernanceService service;

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('clm:governance:routing:query')")
    public CommonResult<PageResult<RoutingRuleRespVO>> page(@Valid RoutingRulePageReqVO reqVO) {
        return success(service.page(reqVO));
    }

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('clm:governance:routing:query')")
    public CommonResult<RoutingRuleRespVO> get(@RequestParam("id") Long id) {
        return success(service.get(id));
    }

    @PostMapping("/save-draft")
    @PreAuthorize("@ss.hasPermission('clm:governance:routing:update')")
    public CommonResult<Long> saveDraft(@Valid @RequestBody RoutingRuleSaveReqVO reqVO) {
        return success(service.saveDraft(reqVO));
    }

    @PostMapping("/precheck")
    @PreAuthorize("@ss.hasPermission('clm:governance:routing:query')")
    public CommonResult<RoutingPrecheckRespVO> precheck(@Valid @RequestBody RoutingPrecheckReqVO reqVO) {
        return success(service.precheck(reqVO));
    }

    @PutMapping("/publish")
    @PreAuthorize("@ss.hasPermission('clm:governance:routing:publish')")
    public CommonResult<Boolean> publish(@RequestParam("id") Long id) {
        service.publish(id);
        return success(true);
    }

    @PutMapping("/disable")
    @PreAuthorize("@ss.hasPermission('clm:governance:routing:publish')")
    public CommonResult<Boolean> disable(@RequestParam("id") Long id) {
        service.disable(id);
        return success(true);
    }
}
