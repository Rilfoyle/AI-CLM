package cn.iocoder.yudao.module.system.service.auth;

import cn.hutool.core.util.ReflectUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.system.api.sms.SmsCodeApi;
import cn.iocoder.yudao.module.system.api.social.dto.SocialUserBindReqDTO;
import cn.iocoder.yudao.module.system.api.social.dto.SocialUserRespDTO;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.*;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.RoleDO;
import cn.iocoder.yudao.module.system.dal.dataobject.tenant.TenantDO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.enums.logger.LoginLogTypeEnum;
import cn.iocoder.yudao.module.system.enums.logger.LoginResultEnum;
import cn.iocoder.yudao.module.system.enums.sms.SmsSceneEnum;
import cn.iocoder.yudao.module.system.enums.social.SocialTypeEnum;
import cn.iocoder.yudao.module.system.service.logger.LoginLogService;
import cn.iocoder.yudao.module.system.service.member.MemberService;
import cn.iocoder.yudao.module.system.service.oauth2.OAuth2TokenService;
import cn.iocoder.yudao.module.system.service.permission.PermissionService;
import cn.iocoder.yudao.module.system.service.permission.RoleService;
import cn.iocoder.yudao.module.system.service.social.SocialUserService;
import cn.iocoder.yudao.module.system.service.tenant.TenantService;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import com.anji.captcha.model.common.ResponseModel;
import com.anji.captcha.service.CaptchaService;
import jakarta.annotation.Resource;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import static cn.hutool.core.util.RandomUtil.randomEle;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertPojoEquals;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.randomPojo;
import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.randomString;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@Import(AdminAuthServiceImpl.class)
public class AdminAuthServiceImplTest extends BaseDbUnitTest {

    @Resource
    private AdminAuthServiceImpl authService;

    @MockitoBean
    private AdminUserService userService;
    @MockitoBean
    private CaptchaService captchaService;
    @MockitoBean
    private LoginLogService loginLogService;
    @MockitoBean
    private SocialUserService socialUserService;
    @MockitoBean
    private SmsCodeApi smsCodeApi;
    @MockitoBean
    private OAuth2TokenService oauth2TokenService;
    @MockitoBean
    private MemberService memberService;
    @MockitoBean
    private Validator validator;
    @MockitoBean
    private PermissionService permissionService;
    @MockitoBean
    private RoleService roleService;
    @MockitoBean
    private TenantService tenantService;

    @BeforeEach
    public void setUp() {
        authService.setCaptchaEnable(true);
        // 注入一个 Validator 对象
        ReflectUtil.setFieldValue(authService, "validator",
                Validation.buildDefaultValidatorFactory().getValidator());
    }

    @AfterEach
    public void tearDown() {
        TenantContextHolder.clear();
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    public void testSwitchDemoRole_successFromSystemAdmin() {
        mockDemoTenant("TuriX");
        setLoginUser(200004L, null);
        mockDemoAccount(200004L, "systemadmin", "clm_system_admin");
        AdminUserDO targetUser = mockDemoAccount(200001L, "business", "clm_business");
        AuthDemoRoleSwitchReqVO reqVO = new AuthDemoRoleSwitchReqVO();
        reqVO.setRoleCode("clm_business");
        OAuth2AccessTokenDO accessTokenDO = randomPojo(OAuth2AccessTokenDO.class,
                o -> o.setUserId(targetUser.getId()).setUserType(UserTypeEnum.ADMIN.getValue()));
        when(oauth2TokenService.createAccessToken(eq(200001L), eq(UserTypeEnum.ADMIN.getValue()), eq("default"),
                eq(List.of(AdminAuthServiceImpl.DEMO_ROLE_SWITCH_SCOPE)))).thenReturn(accessTokenDO);

        AuthLoginRespVO result = authService.switchDemoRole(reqVO, "current-token");

        assertPojoEquals(accessTokenDO, result);
        verify(oauth2TokenService).removeAccessToken("current-token");
        verify(loginLogService).createLoginLog(argThat(log -> Objects.equals(log.getUserId(), 200001L)
                && Objects.equals(log.getResult(), LoginResultEnum.SUCCESS.getResult())));
    }

    @Test
    public void testSwitchDemoRole_successFromScopedDemoAccount() {
        mockDemoTenant("TuriX");
        setLoginUser(200001L, List.of(AdminAuthServiceImpl.DEMO_ROLE_SWITCH_SCOPE));
        mockDemoAccount(200001L, "business", "clm_business");
        mockDemoAccount(200002L, "legal", "clm_legal");
        AuthDemoRoleSwitchReqVO reqVO = new AuthDemoRoleSwitchReqVO();
        reqVO.setRoleCode("clm_legal");
        OAuth2AccessTokenDO accessTokenDO = randomPojo(OAuth2AccessTokenDO.class,
                o -> o.setUserId(200002L).setUserType(UserTypeEnum.ADMIN.getValue()));
        when(oauth2TokenService.createAccessToken(eq(200002L), eq(UserTypeEnum.ADMIN.getValue()), eq("default"),
                eq(List.of(AdminAuthServiceImpl.DEMO_ROLE_SWITCH_SCOPE)))).thenReturn(accessTokenDO);

        AuthLoginRespVO result = authService.switchDemoRole(reqVO, "scoped-token");

        assertPojoEquals(accessTokenDO, result);
        verify(oauth2TokenService).removeAccessToken("scoped-token");
    }

    @Test
    public void testSwitchDemoRole_rejectsUnscopedBusiness() {
        mockDemoTenant("TuriX");
        setLoginUser(200001L, null);
        AuthDemoRoleSwitchReqVO reqVO = new AuthDemoRoleSwitchReqVO();
        reqVO.setRoleCode("clm_legal");

        assertThrows(AccessDeniedException.class,
                () -> authService.switchDemoRole(reqVO, "current-token"));
        verify(oauth2TokenService, never()).createAccessToken(anyLong(), anyInt(), anyString(), any());
    }

    @Test
    public void testSwitchDemoRole_rejectsPlatformAdmin() {
        mockDemoTenant("TuriX");
        setLoginUser(1L, null);
        AuthDemoRoleSwitchReqVO reqVO = new AuthDemoRoleSwitchReqVO();
        reqVO.setRoleCode("clm_business");

        assertThrows(AccessDeniedException.class,
                () -> authService.switchDemoRole(reqVO, "current-token"));
        verify(oauth2TokenService, never()).createAccessToken(anyLong(), anyInt(), anyString(), any());
    }

    @Test
    public void testSwitchDemoRole_rejectsAdminTarget() {
        mockDemoTenant("TuriX");
        setLoginUser(200004L, null);
        mockDemoAccount(200004L, "systemadmin", "clm_system_admin");
        AuthDemoRoleSwitchReqVO reqVO = new AuthDemoRoleSwitchReqVO();
        reqVO.setRoleCode("super_admin");

        assertThrows(AccessDeniedException.class,
                () -> authService.switchDemoRole(reqVO, "current-token"));
        verify(oauth2TokenService, never()).createAccessToken(anyLong(), anyInt(), anyString(), any());
    }

    @Test
    public void testSwitchDemoRole_rejectsOtherTenant() {
        mockDemoTenant("Other");
        setLoginUser(200004L, null);
        AuthDemoRoleSwitchReqVO reqVO = new AuthDemoRoleSwitchReqVO();
        reqVO.setRoleCode("clm_business");

        assertThrows(AccessDeniedException.class,
                () -> authService.switchDemoRole(reqVO, "current-token"));
        verify(oauth2TokenService, never()).createAccessToken(anyLong(), anyInt(), anyString(), any());
    }

    @Test
    public void testSwitchDemoRole_rejectsWrongTargetRole() {
        mockDemoTenant("TuriX");
        setLoginUser(200004L, null);
        mockDemoAccount(200004L, "systemadmin", "clm_system_admin");
        AdminUserDO targetUser = new AdminUserDO().setId(200001L).setUsername("business")
                .setStatus(CommonStatusEnum.ENABLE.getStatus());
        targetUser.setTenantId(1L);
        when(userService.getUserByUsername("business")).thenReturn(targetUser);
        when(permissionService.getUserRoleIdListByUserId(200001L)).thenReturn(Set.of(9001L));
        RoleDO wrongRole = new RoleDO().setId(9001L).setCode("clm_legal")
                .setStatus(CommonStatusEnum.ENABLE.getStatus());
        wrongRole.setTenantId(1L);
        when(roleService.getRoleList(Set.of(9001L))).thenReturn(List.of(wrongRole));
        AuthDemoRoleSwitchReqVO reqVO = new AuthDemoRoleSwitchReqVO();
        reqVO.setRoleCode("clm_business");

        assertThrows(AccessDeniedException.class,
                () -> authService.switchDemoRole(reqVO, "current-token"));
        verify(oauth2TokenService, never()).createAccessToken(anyLong(), anyInt(), anyString(), any());
    }

    @Test
    public void testSwitchDemoRole_rejectsDisabledTarget() {
        mockDemoTenant("TuriX");
        setLoginUser(200004L, null);
        mockDemoAccount(200004L, "systemadmin", "clm_system_admin");
        AdminUserDO targetUser = new AdminUserDO().setId(200001L).setUsername("business")
                .setStatus(CommonStatusEnum.DISABLE.getStatus());
        targetUser.setTenantId(1L);
        when(userService.getUserByUsername("business")).thenReturn(targetUser);
        AuthDemoRoleSwitchReqVO reqVO = new AuthDemoRoleSwitchReqVO();
        reqVO.setRoleCode("clm_business");

        assertThrows(AccessDeniedException.class,
                () -> authService.switchDemoRole(reqVO, "current-token"));
        verify(oauth2TokenService, never()).createAccessToken(anyLong(), anyInt(), anyString(), any());
    }

    private void mockDemoTenant(String name) {
        TenantContextHolder.setTenantId(1L);
        when(tenantService.getTenant(1L)).thenReturn(new TenantDO().setId(1L).setName(name)
                .setStatus(CommonStatusEnum.ENABLE.getStatus()));
    }

    private void setLoginUser(Long userId, List<String> scopes) {
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(userId).setUserType(UserTypeEnum.ADMIN.getValue())
                .setTenantId(1L).setScopes(scopes), new MockHttpServletRequest());
    }

    private AdminUserDO mockDemoAccount(Long userId, String username, String roleCode) {
        AdminUserDO user = new AdminUserDO().setId(userId).setUsername(username)
                .setStatus(CommonStatusEnum.ENABLE.getStatus());
        user.setTenantId(1L);
        when(userService.getUser(userId)).thenReturn(user);
        when(userService.getUserByUsername(username)).thenReturn(user);
        Long roleId = userId + 1_000_000L;
        when(permissionService.getUserRoleIdListByUserId(userId)).thenReturn(Set.of(roleId));
        RoleDO role = new RoleDO().setId(roleId).setCode(roleCode)
                .setStatus(CommonStatusEnum.ENABLE.getStatus());
        role.setTenantId(1L);
        when(roleService.getRoleList(Set.of(roleId))).thenReturn(List.of(role));
        return user;
    }

    @Test
    public void testAuthenticate_success() {
        // 准备参数
        String username = randomString();
        String password = randomString();
        // mock user 数据
        AdminUserDO user = randomPojo(AdminUserDO.class, o -> o.setUsername(username)
                .setPassword(password).setStatus(CommonStatusEnum.ENABLE.getStatus()));
        when(userService.getUserByUsername(eq(username))).thenReturn(user);
        // mock password 匹配
        when(userService.isPasswordMatch(eq(password), eq(user.getPassword()))).thenReturn(true);

        // 调用
        AdminUserDO loginUser = authService.authenticate(username, password);
        // 校验
        assertPojoEquals(user, loginUser);
    }

    @Test
    public void testAuthenticate_userNotFound() {
        // 准备参数
        String username = randomString();
        String password = randomString();

        // 调用, 并断言异常
        assertServiceException(() -> authService.authenticate(username, password),
                AUTH_LOGIN_BAD_CREDENTIALS);
        verify(loginLogService).createLoginLog(
                argThat(o -> o.getLogType().equals(LoginLogTypeEnum.LOGIN_USERNAME.getType())
                        && o.getResult().equals(LoginResultEnum.BAD_CREDENTIALS.getResult())
                        && o.getUserId() == null)
        );
    }

    @Test
    public void testAuthenticate_badCredentials() {
        // 准备参数
        String username = randomString();
        String password = randomString();
        // mock user 数据
        AdminUserDO user = randomPojo(AdminUserDO.class, o -> o.setUsername(username)
                .setPassword(password).setStatus(CommonStatusEnum.ENABLE.getStatus()));
        when(userService.getUserByUsername(eq(username))).thenReturn(user);

        // 调用, 并断言异常
        assertServiceException(() -> authService.authenticate(username, password),
                AUTH_LOGIN_BAD_CREDENTIALS);
        verify(loginLogService).createLoginLog(
                argThat(o -> o.getLogType().equals(LoginLogTypeEnum.LOGIN_USERNAME.getType())
                        && o.getResult().equals(LoginResultEnum.BAD_CREDENTIALS.getResult())
                        && o.getUserId().equals(user.getId()))
        );
    }

    @Test
    public void testAuthenticate_userDisabled() {
        // 准备参数
        String username = randomString();
        String password = randomString();
        // mock user 数据
        AdminUserDO user = randomPojo(AdminUserDO.class, o -> o.setUsername(username)
                .setPassword(password).setStatus(CommonStatusEnum.DISABLE.getStatus()));
        when(userService.getUserByUsername(eq(username))).thenReturn(user);
        // mock password 匹配
        when(userService.isPasswordMatch(eq(password), eq(user.getPassword()))).thenReturn(true);

        // 调用, 并断言异常
        assertServiceException(() -> authService.authenticate(username, password),
                AUTH_LOGIN_USER_DISABLED);
        verify(loginLogService).createLoginLog(
                argThat(o -> o.getLogType().equals(LoginLogTypeEnum.LOGIN_USERNAME.getType())
                        && o.getResult().equals(LoginResultEnum.USER_DISABLED.getResult())
                        && o.getUserId().equals(user.getId()))
        );
    }

    @Test
    public void testLogin_success() {
        // 准备参数
        AuthLoginReqVO reqVO = randomPojo(AuthLoginReqVO.class, o ->
                o.setUsername("test_username").setPassword("test_password")
                        .setSocialType(randomEle(SocialTypeEnum.values()).getType()));

        // mock 验证码正确
        authService.setCaptchaEnable(false);
        // mock user 数据
        AdminUserDO user = randomPojo(AdminUserDO.class, o -> o.setId(1L).setUsername("test_username")
                .setPassword("test_password").setStatus(CommonStatusEnum.ENABLE.getStatus()));
        when(userService.getUserByUsername(eq("test_username"))).thenReturn(user);
        // mock password 匹配
        when(userService.isPasswordMatch(eq("test_password"), eq(user.getPassword()))).thenReturn(true);
        // mock 缓存登录用户到 Redis
        OAuth2AccessTokenDO accessTokenDO = randomPojo(OAuth2AccessTokenDO.class, o -> o.setUserId(1L)
                .setUserType(UserTypeEnum.ADMIN.getValue()));
        when(oauth2TokenService.createAccessToken(eq(1L), eq(UserTypeEnum.ADMIN.getValue()), eq("default"), isNull()))
                .thenReturn(accessTokenDO);

        // 调用，并校验
        AuthLoginRespVO loginRespVO = authService.login(reqVO);
        assertPojoEquals(accessTokenDO, loginRespVO);
        // 校验调用参数
        verify(loginLogService).createLoginLog(
                argThat(o -> o.getLogType().equals(LoginLogTypeEnum.LOGIN_USERNAME.getType())
                        && o.getResult().equals(LoginResultEnum.SUCCESS.getResult())
                        && o.getUserId().equals(user.getId()))
        );
        verify(socialUserService).bindSocialUser(eq(new SocialUserBindReqDTO(
                user.getId(), UserTypeEnum.ADMIN.getValue(),
                reqVO.getSocialType(), reqVO.getSocialCode(), reqVO.getSocialState())));
    }

    @Test
    public void testSendSmsCode() {
        // 准备参数
        String mobile = randomString();
        Integer scene = SmsSceneEnum.ADMIN_MEMBER_LOGIN.getScene();
        AuthSmsSendReqVO reqVO = new AuthSmsSendReqVO(mobile, scene);
        // mock 方法（用户信息）
        AdminUserDO user = randomPojo(AdminUserDO.class);
        when(userService.getUserByMobile(eq(mobile))).thenReturn(user);

        // 调用
        authService.sendSmsCode(reqVO);
        // 断言
        verify(smsCodeApi).sendSmsCode(argThat(sendReqDTO -> {
            assertEquals(mobile, sendReqDTO.getMobile());
            assertEquals(scene, sendReqDTO.getScene());
            return true;
        }));
    }

    @Test
    public void testSmsLogin_success() {
        // 准备参数
        String mobile = randomString();
        String code = randomString();
        AuthSmsLoginReqVO reqVO = new AuthSmsLoginReqVO(mobile, code);
        // mock 方法（验证码）
        doNothing().when(smsCodeApi).useSmsCode((argThat(smsCodeUseReqDTO -> {
            assertEquals(mobile, smsCodeUseReqDTO.getMobile());
            assertEquals(code, smsCodeUseReqDTO.getCode());
            assertEquals(SmsSceneEnum.ADMIN_MEMBER_LOGIN.getScene(), smsCodeUseReqDTO.getScene());
            return true;
        })));
        // mock 方法（用户信息）
        AdminUserDO user = randomPojo(AdminUserDO.class, o -> o.setId(1L));
        when(userService.getUserByMobile(eq(mobile))).thenReturn(user);
        // mock 缓存登录用户到 Redis
        OAuth2AccessTokenDO accessTokenDO = randomPojo(OAuth2AccessTokenDO.class, o -> o.setUserId(1L)
                .setUserType(UserTypeEnum.ADMIN.getValue()));
        when(oauth2TokenService.createAccessToken(eq(1L), eq(UserTypeEnum.ADMIN.getValue()), eq("default"), isNull()))
                .thenReturn(accessTokenDO);

        // 调用，并断言
        AuthLoginRespVO loginRespVO = authService.smsLogin(reqVO);
        assertPojoEquals(accessTokenDO, loginRespVO);
        // 断言调用
        verify(loginLogService).createLoginLog(
                argThat(o -> o.getLogType().equals(LoginLogTypeEnum.LOGIN_MOBILE.getType())
                        && o.getResult().equals(LoginResultEnum.SUCCESS.getResult())
                        && o.getUserId().equals(user.getId()))
        );
    }

    @Test
    public void testSocialLogin_success() {
        // 准备参数
        AuthSocialLoginReqVO reqVO = randomPojo(AuthSocialLoginReqVO.class);
        // mock 方法（绑定的用户编号）
        Long userId = 1L;
        when(socialUserService.getSocialUserByCode(eq(UserTypeEnum.ADMIN.getValue()), eq(reqVO.getType()),
                eq(reqVO.getCode()), eq(reqVO.getState()))).thenReturn(new SocialUserRespDTO(randomString(), randomString(), randomString(), userId));
        // mock（用户）
        AdminUserDO user = randomPojo(AdminUserDO.class, o -> o.setId(userId));
        when(userService.getUser(eq(userId))).thenReturn(user);
        // mock 缓存登录用户到 Redis
        OAuth2AccessTokenDO accessTokenDO = randomPojo(OAuth2AccessTokenDO.class, o -> o.setUserId(1L)
                .setUserType(UserTypeEnum.ADMIN.getValue()));
        when(oauth2TokenService.createAccessToken(eq(1L), eq(UserTypeEnum.ADMIN.getValue()), eq("default"), isNull()))
                .thenReturn(accessTokenDO);

        // 调用，并断言
        AuthLoginRespVO loginRespVO = authService.socialLogin(reqVO);
        assertPojoEquals(accessTokenDO, loginRespVO);
        // 断言调用
        verify(loginLogService).createLoginLog(
                argThat(o -> o.getLogType().equals(LoginLogTypeEnum.LOGIN_SOCIAL.getType())
                        && o.getResult().equals(LoginResultEnum.SUCCESS.getResult())
                        && o.getUserId().equals(user.getId()))
        );
    }

    @Test
    public void testValidateCaptcha_successWithEnable() {
        // 准备参数
        AuthLoginReqVO reqVO = randomPojo(AuthLoginReqVO.class);

        // mock 验证通过
        when(captchaService.verification(argThat(captchaVO -> {
            assertEquals(reqVO.getCaptchaVerification(), captchaVO.getCaptchaVerification());
            return true;
        }))).thenReturn(ResponseModel.success());

        // 调用，无需断言
        authService.validateCaptcha(reqVO);
    }

    @Test
    public void testValidateCaptcha_successWithDisable() {
        // 准备参数
        AuthLoginReqVO reqVO = randomPojo(AuthLoginReqVO.class);

        // mock 验证码关闭
        authService.setCaptchaEnable(false);

        // 调用，无需断言
        authService.validateCaptcha(reqVO);
    }

    @Test
    public void testCaptcha_fail() {
        // 准备参数
        AuthLoginReqVO reqVO = randomPojo(AuthLoginReqVO.class);

        // mock 验证通过
        when(captchaService.verification(argThat(captchaVO -> {
            assertEquals(reqVO.getCaptchaVerification(), captchaVO.getCaptchaVerification());
            return true;
        }))).thenReturn(ResponseModel.errorMsg("就是不对"));

        // 调用, 并断言异常
        assertServiceException(() -> authService.validateCaptcha(reqVO), AUTH_LOGIN_CAPTCHA_CODE_ERROR, "就是不对");
        // 校验调用参数
        verify(loginLogService).createLoginLog(
                argThat(o -> o.getLogType().equals(LoginLogTypeEnum.LOGIN_USERNAME.getType())
                        && o.getResult().equals(LoginResultEnum.CAPTCHA_CODE_ERROR.getResult()))
        );
    }

    @Test
    public void testRefreshToken() {
        // 准备参数
        String refreshToken = randomString();
        // mock 方法
        OAuth2AccessTokenDO accessTokenDO = randomPojo(OAuth2AccessTokenDO.class);
        when(oauth2TokenService.refreshAccessToken(eq(refreshToken), eq("default")))
                .thenReturn(accessTokenDO);

        // 调用
        AuthLoginRespVO loginRespVO = authService.refreshToken(refreshToken);
        // 断言
        assertPojoEquals(accessTokenDO, loginRespVO);
    }

    @Test
    public void testLogout_success() {
        // 准备参数
        String token = randomString();
        // mock
        OAuth2AccessTokenDO accessTokenDO = randomPojo(OAuth2AccessTokenDO.class, o -> o.setUserId(1L)
                .setUserType(UserTypeEnum.ADMIN.getValue()));
        when(oauth2TokenService.removeAccessToken(eq(token))).thenReturn(accessTokenDO);

        // 调用
        authService.logout(token, LoginLogTypeEnum.LOGOUT_SELF.getType());
        // 校验调用参数
        verify(loginLogService).createLoginLog(
                argThat(o -> o.getLogType().equals(LoginLogTypeEnum.LOGOUT_SELF.getType())
                        && o.getResult().equals(LoginResultEnum.SUCCESS.getResult()))
        );
        // 调用，并校验

    }

    @Test
    public void testLogout_fail() {
        // 准备参数
        String token = randomString();

        // 调用
        authService.logout(token, LoginLogTypeEnum.LOGOUT_SELF.getType());
        // 校验调用参数
        verify(loginLogService, never()).createLoginLog(any());
    }

}
