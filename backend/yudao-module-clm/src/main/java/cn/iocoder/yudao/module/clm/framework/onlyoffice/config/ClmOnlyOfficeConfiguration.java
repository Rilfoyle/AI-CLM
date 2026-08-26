package cn.iocoder.yudao.module.clm.framework.onlyoffice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * clm 模块的 ONLYOFFICE 配置类：注册 {@link ClmOnlyOfficeProperties}
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ClmOnlyOfficeProperties.class)
public class ClmOnlyOfficeConfiguration {
}
