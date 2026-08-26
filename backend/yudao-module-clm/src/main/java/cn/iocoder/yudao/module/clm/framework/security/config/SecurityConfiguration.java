package cn.iocoder.yudao.module.clm.framework.security.config;

import cn.iocoder.yudao.framework.security.config.AuthorizeRequestsCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

/**
 * clm 模块的 Security 配置
 *
 * 放行供 ONLYOFFICE Document Server 访问的开放接口（同时接口方法上保留 @PermitAll）
 */
@Configuration(proxyBeanMethods = false, value = "clmSecurityConfiguration")
public class SecurityConfiguration {

    @Bean("clmAuthorizeRequestsCustomizer")
    public AuthorizeRequestsCustomizer authorizeRequestsCustomizer() {
        return new AuthorizeRequestsCustomizer() {

            @Override
            public void customize(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry registry) {
                // ONLYOFFICE Document Server 拉取文件 / 保存回调（token 鉴权，见 OnlyOfficeTokenService）
                registry.requestMatchers(buildAdminApi("/clm/online-edit/file")).permitAll()
                        .requestMatchers(buildAdminApi("/clm/online-edit/callback")).permitAll();
            }

        };
    }

}
