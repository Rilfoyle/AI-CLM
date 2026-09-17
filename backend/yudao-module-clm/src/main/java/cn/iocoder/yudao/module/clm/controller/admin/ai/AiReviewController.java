package cn.iocoder.yudao.module.clm.controller.admin.ai;

import cn.iocoder.yudao.module.clm.ai.*;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@RestController
@RequestMapping("/clm/ai-review")
@Validated
public class AiReviewController {
    @Resource private AiReviewService reviewService;

    @PostMapping("/run")
    @PreAuthorize("@ss.hasPermission('clm:ai-review:run')")
    public CommonResult<Long> run(@Valid @RequestBody AiReviewRunReqVO reqVO) {
        return success(reviewService.run(reqVO, getLoginUserId()));
    }

    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermission('clm:contract:query')")
    public CommonResult<List<AiReviewRunRespVO>> getList(@RequestParam("contractId") Long contractId) {
        return success(reviewService.getList(contractId, getLoginUserId()));
    }

    @PutMapping("/finding/resolve")
    @PreAuthorize("@ss.hasPermission('clm:ai-review:update')")
    public CommonResult<Boolean> resolveFinding(@RequestParam("id") Long id,
            @RequestParam("resolution") @Pattern(regexp = "ACCEPTED|IGNORED") String resolution) {
        reviewService.resolveFinding(id, resolution, getLoginUserId());
        return success(true);
    }
}
