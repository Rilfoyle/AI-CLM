package cn.iocoder.yudao.module.clm.onlyoffice;

import cn.hutool.core.util.StrUtil;
import cn.hutool.jwt.JWTUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.clm.framework.onlyoffice.config.ClmOnlyOfficeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.clm.enums.ErrorCodeConstants.ONLINE_EDIT_TOKEN_INVALID;

/**
 * ONLYOFFICE 访问令牌 Service 实现类
 */
@Service
@Slf4j
public class OnlyOfficeTokenServiceImpl implements OnlyOfficeTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder B64_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64_DECODER = Base64.getUrlDecoder();

    private final ClmOnlyOfficeProperties properties;

    public OnlyOfficeTokenServiceImpl(ClmOnlyOfficeProperties properties) {
        this.properties = properties;
    }

    // ========== 紧凑令牌 ==========

    @Override
    public String issue(OnlyOfficeTokenPayload payload) {
        Objects.requireNonNull(payload, "payload");
        if (payload.getExp() == null) {
            int ttl = properties.getTokenTtlSeconds() == null ? 3600 : properties.getTokenTtlSeconds();
            payload.setExp(Instant.now().getEpochSecond() + ttl);
        }
        String body = B64_ENCODER.encodeToString(JsonUtils.toJsonString(payload).getBytes(StandardCharsets.UTF_8));
        return body + "." + B64_ENCODER.encodeToString(hmac(body));
    }

    @Override
    public OnlyOfficeTokenPayload verify(String token, String expectedPurpose) {
        if (StrUtil.isBlank(token)) {
            throw exception(ONLINE_EDIT_TOKEN_INVALID);
        }
        int dot = token.indexOf('.');
        if (dot <= 0 || dot == token.length() - 1 || token.indexOf('.', dot + 1) >= 0) {
            throw exception(ONLINE_EDIT_TOKEN_INVALID);
        }
        String body = token.substring(0, dot);
        String signature = token.substring(dot + 1);
        // 1. 签名（常量时间比较）
        byte[] actual;
        try {
            actual = B64_DECODER.decode(signature);
        } catch (IllegalArgumentException ex) {
            throw exception(ONLINE_EDIT_TOKEN_INVALID);
        }
        if (!MessageDigest.isEqual(hmac(body), actual)) {
            throw exception(ONLINE_EDIT_TOKEN_INVALID);
        }
        // 2. 解析 payload
        OnlyOfficeTokenPayload payload;
        try {
            String json = new String(B64_DECODER.decode(body), StandardCharsets.UTF_8);
            payload = JsonUtils.parseObject(json, OnlyOfficeTokenPayload.class);
        } catch (Exception ex) {
            throw exception(ONLINE_EDIT_TOKEN_INVALID);
        }
        if (payload == null || payload.getV() == null || payload.getT() == null) {
            throw exception(ONLINE_EDIT_TOKEN_INVALID);
        }
        // 3. 过期
        if (payload.getExp() == null || payload.getExp() < Instant.now().getEpochSecond()) {
            throw exception(ONLINE_EDIT_TOKEN_INVALID);
        }
        // 4. 用途
        if (expectedPurpose != null && !expectedPurpose.equals(payload.getP())) {
            throw exception(ONLINE_EDIT_TOKEN_INVALID);
        }
        return payload;
    }

    private byte[] hmac(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secretBytes(), HMAC_ALGORITHM));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC-SHA256 失败", ex);
        }
    }

    private byte[] secretBytes() {
        // 紧凑令牌的密钥与 Document Server 的 JWT 密钥共用；为空时仍需一个非空密钥，避免 Mac.init 报错
        String secret = StrUtil.blankToDefault(properties.getJwtSecret(), "clm-onlyoffice-empty-secret");
        return secret.getBytes(StandardCharsets.UTF_8);
    }

    // ========== HS256 JWT ==========

    @Override
    public boolean isJwtEnabled() {
        return StrUtil.isNotBlank(properties.getJwtSecret());
    }

    @Override
    public String createJwt(Map<String, Object> payload) {
        return JWTUtil.createToken(payload, secretBytes());
    }

    @Override
    public boolean verifyJwt(String jwt) {
        if (StrUtil.isBlank(jwt)) {
            return false;
        }
        try {
            return JWTUtil.verify(jwt, secretBytes());
        } catch (Exception ex) {
            log.debug("[verifyJwt][JWT 解析失败: {}]", ex.getMessage());
            return false;
        }
    }

}
