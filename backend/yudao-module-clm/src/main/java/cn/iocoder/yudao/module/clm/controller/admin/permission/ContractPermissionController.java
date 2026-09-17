package cn.iocoder.yudao.module.clm.controller.admin.permission;

import cn.iocoder.yudao.module.clm.permission.*;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@RestController
@RequestMapping("/clm/permission")
@Validated
public class ContractPermissionController {
    @Resource private ContractPermissionService permissionService;

    @GetMapping("/policy/list")
    @PreAuthorize("@ss.hasPermission('clm:permission-policy:query')")
    public CommonResult<List<PermissionPolicyVersionDO>> getPolicyList() {
        return success(permissionService.getPolicyList());
    }

    @PostMapping("/policy/save")
    @PreAuthorize("@ss.hasPermission('clm:permission-policy:update')")
    public CommonResult<Long> savePolicy(@Valid @RequestBody PermissionPolicySaveReqVO reqVO) {
        return success(permissionService.savePolicy(reqVO));
    }

    @PutMapping("/policy/publish")
    @PreAuthorize("@ss.hasPermission('clm:permission-policy:publish')")
    public CommonResult<Boolean> publishPolicy(@RequestParam("id") Long id) {
        permissionService.publishPolicy(id, getLoginUserId());
        return success(true);
    }

    @GetMapping("/user-scope/list")
    @PreAuthorize("@ss.hasPermission('clm:user-scope:query')")
    public CommonResult<List<UserScopeDO>> getUserScopes(@RequestParam("userId") Long userId) {
        return success(permissionService.getUserScopes(userId));
    }

    @PostMapping("/user-scope/save")
    @PreAuthorize("@ss.hasPermission('clm:user-scope:update')")
    public CommonResult<Long> saveUserScope(@Valid @RequestBody UserScopeSaveReqVO reqVO) {
        return success(permissionService.saveUserScope(reqVO));
    }

    @DeleteMapping("/user-scope/delete")
    @PreAuthorize("@ss.hasPermission('clm:user-scope:update')")
    public CommonResult<Boolean> deleteUserScope(@RequestParam("id") Long id) {
        permissionService.deleteUserScope(id);
        return success(true);
    }
}
