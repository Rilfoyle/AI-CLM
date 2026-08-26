package cn.iocoder.yudao.module.clm.onlyoffice;

import java.util.Map;

/**
 * ONLYOFFICE 访问令牌 Service 接口
 *
 * 1. 紧凑令牌（自实现 HMAC-SHA256）：给 Document Server 拉取文件 / 回调使用，格式 base64url(payloadJson) + "." + base64url(hmac)
 * 2. 标准 HS256 JWT（hutool）：给编辑器 config 签名、校验 Document Server 回调的 token
 */
public interface OnlyOfficeTokenService {

    /**
     * 签发紧凑令牌；payload.exp 为空时取 now + token-ttl-seconds
     *
     * @param payload 令牌内容
     * @return 令牌
     */
    String issue(OnlyOfficeTokenPayload payload);

    /**
     * 校验紧凑令牌：格式、签名、过期、用途
     *
     * @param token           令牌
     * @param expectedPurpose 期望用途（file / callback）
     * @return 令牌内容；不合法抛 ONLINE_EDIT_TOKEN_INVALID
     */
    OnlyOfficeTokenPayload verify(String token, String expectedPurpose);

    /**
     * 是否配置了 JWT 密钥（为空时 Document Server 侧关闭 JWT）
     */
    boolean isJwtEnabled();

    /**
     * 用 jwt-secret 生成 HS256 JWT
     *
     * @param payload JWT payload
     * @return JWT
     */
    String createJwt(Map<String, Object> payload);

    /**
     * 用 jwt-secret 校验 HS256 JWT 的签名（格式错误、签名错误均返回 false）
     *
     * @param jwt JWT
     * @return 是否合法
     */
    boolean verifyJwt(String jwt);

}
