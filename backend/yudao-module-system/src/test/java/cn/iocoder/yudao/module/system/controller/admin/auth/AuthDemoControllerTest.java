package cn.iocoder.yudao.module.system.controller.admin.auth;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.config.SecurityProperties;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthDemoRoleSwitchReqVO;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthLoginRespVO;
import cn.iocoder.yudao.module.system.service.auth.AdminAuthService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertPojoEquals;
import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.randomPojo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AuthDemoControllerTest extends BaseMockitoUnitTest {

    @InjectMocks
    private AuthDemoController controller;

    @Mock
    private AdminAuthService authService;
    @Spy
    private SecurityProperties securityProperties = new SecurityProperties();

    @Test
    public void testSwitchRole_successFromLoopback() {
        AuthDemoRoleSwitchReqVO reqVO = new AuthDemoRoleSwitchReqVO();
        reqVO.setRoleCode("clm_business");
        MockHttpServletRequest request = createRequest("127.0.0.1", "127.0.0.1", "current-token");
        AuthLoginRespVO loginRespVO = randomPojo(AuthLoginRespVO.class);
        when(authService.switchDemoRole(reqVO, "current-token")).thenReturn(loginRespVO);

        CommonResult<AuthLoginRespVO> result = controller.switchRole(reqVO, request);

        assertEquals(0, result.getCode());
        assertPojoEquals(loginRespVO, result.getData());
        verify(authService).switchDemoRole(reqVO, "current-token");
    }

    @Test
    public void testSwitchRole_rejectsRemoteRequest() {
        AuthDemoRoleSwitchReqVO reqVO = new AuthDemoRoleSwitchReqVO();
        reqVO.setRoleCode("clm_business");
        MockHttpServletRequest request = createRequest("192.0.2.10", "127.0.0.1", "current-token");

        assertThrows(AccessDeniedException.class, () -> controller.switchRole(reqVO, request));
    }

    @Test
    public void testSwitchRole_rejectsNonLoopbackListener() {
        AuthDemoRoleSwitchReqVO reqVO = new AuthDemoRoleSwitchReqVO();
        reqVO.setRoleCode("clm_business");
        MockHttpServletRequest request = createRequest("127.0.0.1", "192.0.2.20", "current-token");

        assertThrows(AccessDeniedException.class, () -> controller.switchRole(reqVO, request));
    }

    @Test
    public void testSwitchRole_rejectsMissingToken() {
        AuthDemoRoleSwitchReqVO reqVO = new AuthDemoRoleSwitchReqVO();
        reqVO.setRoleCode("clm_business");
        MockHttpServletRequest request = createRequest("127.0.0.1", "127.0.0.1", null);

        assertThrows(AccessDeniedException.class, () -> controller.switchRole(reqVO, request));
    }

    private MockHttpServletRequest createRequest(String remoteAddr, String localAddr, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        request.setLocalAddr(localAddr);
        if (token != null) {
            request.addHeader("Authorization", "Bearer " + token);
        }
        return request;
    }

}
