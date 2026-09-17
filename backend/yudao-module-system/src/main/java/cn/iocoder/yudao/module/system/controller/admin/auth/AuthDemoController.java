package cn.iocoder.yudao.module.system.controller.admin.auth;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.config.SecurityProperties;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthDemoRoleSwitchReqVO;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthLoginRespVO;
import cn.iocoder.yudao.module.system.service.auth.AdminAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.context.annotation.Profile;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * TuriX 本地演示身份切换。
 *
 * 该 Controller 默认不注册，仅可通过 local 配置显式开启。它签发目标真实账号的
 * OAuth token，不修改任何用户角色绑定。
 */
@Tag(name = "管理后台 - 本地演示身份切换")
@RestController
@RequestMapping("/system/auth/demo")
@Validated
@Profile("local")
@ConditionalOnProperty(prefix = "turix.demo.role-switch", name = "enabled", havingValue = "true")
public class AuthDemoController {

    @Resource
    private AdminAuthService authService;
    @Resource
    private SecurityProperties securityProperties;

    @PostMapping("/switch-role")
    @Operation(summary = "切换本地演示身份")
    @PreAuthorize("@ss.hasRole('clm_system_admin') or @ss.hasScope('turix:demo-role-switch')")
    public CommonResult<AuthLoginRespVO> switchRole(@RequestBody @Valid AuthDemoRoleSwitchReqVO reqVO,
                                                     HttpServletRequest request) {
        validateLoopback(request);
        String currentToken = SecurityFrameworkUtils.obtainAuthorization(request,
                securityProperties.getTokenHeader(), securityProperties.getTokenParameter());
        if (StrUtil.isBlank(currentToken)) {
            throw new AccessDeniedException("本地演示身份切换需要有效登录令牌");
        }
        return success(authService.switchDemoRole(reqVO, currentToken));
    }

    private static void validateLoopback(HttpServletRequest request) {
        if (!isLoopback(request.getRemoteAddr()) || !isLoopback(request.getLocalAddr())) {
            throw new AccessDeniedException("本地演示身份切换仅允许从当前设备访问");
        }
    }

    private static boolean isLoopback(String address) {
        if (StrUtil.isBlank(address)) {
            return false;
        }
        try {
            return InetAddress.getByName(address).isLoopbackAddress();
        } catch (Exception ignored) {
            return false;
        }
    }

}
