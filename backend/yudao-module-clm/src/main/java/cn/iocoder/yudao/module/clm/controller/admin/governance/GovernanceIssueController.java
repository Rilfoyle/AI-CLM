package cn.iocoder.yudao.module.clm.controller.admin.governance;

import cn.iocoder.yudao.module.clm.governance.*;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@RestController
@RequestMapping("/clm/governance-issue")
@Validated
public class GovernanceIssueController {
    @Resource private GovernanceIssueService issueService;

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('clm:governance-issue:query')")
    public CommonResult<PageResult<GovernanceIssueDO>> getPage(@Valid GovernanceIssuePageReqVO reqVO) {
        return success(issueService.getPage(reqVO));
    }

    @PutMapping("/resolve")
    @PreAuthorize("@ss.hasPermission('clm:governance-issue:update')")
    public CommonResult<Boolean> resolve(@RequestParam("id") Long id) {
        issueService.resolve(id, getLoginUserId());
        return success(true);
    }
}
