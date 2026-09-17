package cn.iocoder.yudao.module.clm.controller.admin.numbering;

import cn.iocoder.yudao.module.clm.numbering.*;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/clm/governance/numbering")
@Validated
public class NumberingGovernanceController {
    @Resource private NumberingGovernanceService service;

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('clm:governance:numbering:query')")
    public CommonResult<PageResult<NumberingRuleRespVO>> page(@Valid NumberingRulePageReqVO reqVO) {
        return success(service.page(reqVO));
    }

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('clm:governance:numbering:query')")
    public CommonResult<NumberingRuleRespVO> get(@RequestParam("id") Long id) {
        return success(service.get(id));
    }

    @PostMapping("/save-draft")
    @PreAuthorize("@ss.hasPermission('clm:governance:numbering:update')")
    public CommonResult<Long> saveDraft(@Valid @RequestBody NumberingRuleSaveReqVO reqVO) {
        return success(service.saveDraft(reqVO));
    }

    @PostMapping("/preview")
    @PreAuthorize("@ss.hasPermission('clm:governance:numbering:query')")
    public CommonResult<NumberingPreviewRespVO> preview(@Valid @RequestBody NumberingRuleSaveReqVO reqVO) {
        return success(service.preview(reqVO));
    }

    @PutMapping("/publish")
    @PreAuthorize("@ss.hasPermission('clm:governance:numbering:publish')")
    public CommonResult<Boolean> publish(@RequestParam("id") Long id) {
        service.publish(id);
        return success(true);
    }

    @PutMapping("/disable")
    @PreAuthorize("@ss.hasPermission('clm:governance:numbering:publish')")
    public CommonResult<Boolean> disable(@RequestParam("id") Long id) {
        service.disable(id);
        return success(true);
    }
}
