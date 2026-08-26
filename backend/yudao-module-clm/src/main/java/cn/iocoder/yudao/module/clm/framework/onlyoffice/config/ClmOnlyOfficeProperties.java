package cn.iocoder.yudao.module.clm.framework.onlyoffice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * ONLYOFFICE Document Server 配置项（clm.onlyoffice.*）
 */
@ConfigurationProperties("clm.onlyoffice")
@Data
@Validated
public class ClmOnlyOfficeProperties {

    /**
     * 是否启用在线编辑
     */
    private Boolean enabled = true;
    /**
     * 浏览器访问 Document Server 的地址
     */
    private String documentServerUrl = "http://127.0.0.1:8090";
    /**
     * 与 Document Server 的 JWT_SECRET 一致；同时作为本地访问令牌的 HMAC 密钥
     */
    private String jwtSecret = "change-me-local-only";
    /**
     * Document Server 访问后端的地址（用于拼接 file / callback URL）
     */
    private String callbackBaseUrl = "http://host.docker.internal:48080";
    /**
     * 访问令牌有效期（秒）
     */
    private Integer tokenTtlSeconds = 3600;

}
