package cn.iocoder.yudao.module.clm.controller.admin.savedfilter;

import cn.iocoder.yudao.module.clm.savedfilter.*;

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
@RequestMapping("/clm/saved-filter")
@Validated
public class SavedFilterController {
    @Resource private SavedFilterService filterService;

    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermission('clm:contract:query')")
    public CommonResult<List<SavedFilterDO>> getList(
            @RequestParam("sceneCode")
            @Pattern(regexp = "CONTRACT_LEDGER|APPROVAL_INBOX|COLLABORATION_CENTER") String sceneCode) {
        return success(filterService.getList(getLoginUserId(), sceneCode));
    }

    @PostMapping("/save")
    @PreAuthorize("@ss.hasPermission('clm:contract:query')")
    public CommonResult<Long> save(@Valid @RequestBody SavedFilterSaveReqVO reqVO) {
        return success(filterService.save(reqVO, getLoginUserId()));
    }

    @DeleteMapping("/delete")
    @PreAuthorize("@ss.hasPermission('clm:contract:query')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        filterService.delete(id, getLoginUserId());
        return success(true);
    }
}
