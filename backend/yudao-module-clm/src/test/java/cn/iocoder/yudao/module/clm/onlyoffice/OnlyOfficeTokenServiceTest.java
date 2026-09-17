package cn.iocoder.yudao.module.clm.onlyoffice;

import cn.hutool.jwt.JWT;
import cn.iocoder.yudao.module.clm.framework.onlyoffice.config.ClmOnlyOfficeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.clm.enums.ErrorCodeConstants.ONLINE_EDIT_TOKEN_INVALID;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link OnlyOfficeTokenServiceImpl} 的单元测试（纯内存，不依赖 DB / Spring）
 */
public class OnlyOfficeTokenServiceTest {

    private static final String SECRET = "unit-test-secret-please-change";

    private OnlyOfficeTokenServiceImpl tokenService;

    @BeforeEach
    public void setUp() {
        ClmOnlyOfficeProperties properties = new ClmOnlyOfficeProperties();
        properties.setJwtSecret(SECRET);
        properties.setTokenTtlSeconds(3600);
        tokenService = new OnlyOfficeTokenServiceImpl(properties);
    }

    private OnlyOfficeTokenPayload newPayload(String purpose) {
        return new OnlyOfficeTokenPayload()
                .setV(11L).setD(22L).setC(33L).setU(1L).setT(1L)
                .setM(OnlyOfficeTokenPayload.MODE_EDIT).setP(purpose);
    }

    @Test
    public void testIssueAndVerify_success() {
        String token = tokenService.issue(newPayload(OnlyOfficeTokenPayload.PURPOSE_FILE));
        assertNotNull(token);
        // 格式：base64url(payload).base64url(hmac)，无其它 '.'
        assertEquals(2, token.split("\\.").length);
        assertTrue(token.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"));

        OnlyOfficeTokenPayload payload = tokenService.verify(token, OnlyOfficeTokenPayload.PURPOSE_FILE);
        assertEquals(11L, payload.getV());
        assertEquals(22L, payload.getD());
        assertEquals(33L, payload.getC());
        assertEquals(1L, payload.getU());
        assertEquals(1L, payload.getT());
        assertEquals(OnlyOfficeTokenPayload.MODE_EDIT, payload.getM());
        assertEquals(OnlyOfficeTokenPayload.PURPOSE_FILE, payload.getP());
        // exp = now + ttl
        long now = Instant.now().getEpochSecond();
        assertTrue(payload.getExp() >= now + 3500 && payload.getExp() <= now + 3700);
        // 不指定 purpose 也能校验
        assertNotNull(tokenService.verify(token, null));
    }

    @Test
    public void testVerify_expired() {
        OnlyOfficeTokenPayload payload = newPayload(OnlyOfficeTokenPayload.PURPOSE_FILE)
                .setExp(Instant.now().getEpochSecond() - 1);
        String token = tokenService.issue(payload);
        assertServiceException(() -> tokenService.verify(token, OnlyOfficeTokenPayload.PURPOSE_FILE),
                ONLINE_EDIT_TOKEN_INVALID);
    }

    @Test
    public void testVerify_tamperedPayload() {
        String token = tokenService.issue(newPayload(OnlyOfficeTokenPayload.PURPOSE_FILE));
        String[] parts = token.split("\\.");
        // 篡改 payload：换一个 versionId 的 base64url 串，签名不变
        String forgedBody = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"v\":999,\"d\":22,\"c\":33,\"u\":1,\"t\":1,\"m\":\"edit\",\"p\":\"file\",\"exp\":9999999999}".getBytes());
        String tampered = forgedBody + "." + parts[1];
        assertServiceException(() -> tokenService.verify(tampered, OnlyOfficeTokenPayload.PURPOSE_FILE),
                ONLINE_EDIT_TOKEN_INVALID);
    }

    @Test
    public void testVerify_tamperedSignature() {
        String token = tokenService.issue(newPayload(OnlyOfficeTokenPayload.PURPOSE_FILE));
        String[] parts = token.split("\\.");
        // 改首字符，确保改变签名字节；末字符可能只改变 Base64URL 未使用的 padding 位，导致偶发未篡改。
        char first = parts[1].charAt(0);
        String forgedSignature = (first == 'A' ? 'B' : 'A') + parts[1].substring(1);
        String tampered = parts[0] + "." + forgedSignature;
        assertServiceException(() -> tokenService.verify(tampered, OnlyOfficeTokenPayload.PURPOSE_FILE),
                ONLINE_EDIT_TOKEN_INVALID);
    }

    @Test
    public void testVerify_wrongSecret() {
        String token = tokenService.issue(newPayload(OnlyOfficeTokenPayload.PURPOSE_FILE));
        ClmOnlyOfficeProperties other = new ClmOnlyOfficeProperties();
        other.setJwtSecret("another-secret");
        OnlyOfficeTokenServiceImpl otherService = new OnlyOfficeTokenServiceImpl(other);
        assertServiceException(() -> otherService.verify(token, OnlyOfficeTokenPayload.PURPOSE_FILE),
                ONLINE_EDIT_TOKEN_INVALID);
    }

    @Test
    public void testVerify_purposeMismatch() {
        String fileToken = tokenService.issue(newPayload(OnlyOfficeTokenPayload.PURPOSE_FILE));
        assertServiceException(() -> tokenService.verify(fileToken, OnlyOfficeTokenPayload.PURPOSE_CALLBACK),
                ONLINE_EDIT_TOKEN_INVALID);
        String callbackToken = tokenService.issue(newPayload(OnlyOfficeTokenPayload.PURPOSE_CALLBACK));
        assertServiceException(() -> tokenService.verify(callbackToken, OnlyOfficeTokenPayload.PURPOSE_FILE),
                ONLINE_EDIT_TOKEN_INVALID);
        assertNotNull(tokenService.verify(callbackToken, OnlyOfficeTokenPayload.PURPOSE_CALLBACK));
    }

    @Test
    public void testVerify_malformed() {
        assertServiceException(() -> tokenService.verify(null, OnlyOfficeTokenPayload.PURPOSE_FILE),
                ONLINE_EDIT_TOKEN_INVALID);
        assertServiceException(() -> tokenService.verify("", OnlyOfficeTokenPayload.PURPOSE_FILE),
                ONLINE_EDIT_TOKEN_INVALID);
        assertServiceException(() -> tokenService.verify("no-dot", OnlyOfficeTokenPayload.PURPOSE_FILE),
                ONLINE_EDIT_TOKEN_INVALID);
        assertServiceException(() -> tokenService.verify("a.b.c", OnlyOfficeTokenPayload.PURPOSE_FILE),
                ONLINE_EDIT_TOKEN_INVALID);
        assertServiceException(() -> tokenService.verify("abc.", OnlyOfficeTokenPayload.PURPOSE_FILE),
                ONLINE_EDIT_TOKEN_INVALID);
        assertServiceException(() -> tokenService.verify(".abc", OnlyOfficeTokenPayload.PURPOSE_FILE),
                ONLINE_EDIT_TOKEN_INVALID);
    }

    @Test
    public void testJwt_createAndVerify() {
        assertTrue(tokenService.isJwtEnabled());
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("key", "clm-v11-abcdef012345");
        document.put("url", "http://host.docker.internal:48080/admin-api/clm/online-edit/file?token=x");
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("documentType", "word");
        config.put("document", document);

        String jwt = tokenService.createJwt(config);
        assertEquals(3, jwt.split("\\.").length);
        assertTrue(tokenService.verifyJwt(jwt));
        // 标准 HS256：hutool JWT 可解析并还原 payload
        JWT parsed = JWT.of(jwt);
        assertEquals("HS256", parsed.getHeader("alg"));
        assertEquals("word", parsed.getPayload("documentType"));
        assertTrue(parsed.setKey(SECRET.getBytes()).verify());

        // 篡改 / 错误密钥 / 空
        String[] parts = jwt.split("\\.");
        assertFalse(tokenService.verifyJwt(parts[0] + "." + parts[1] + ".AAAA"));
        assertFalse(tokenService.verifyJwt("not-a-jwt"));
        assertFalse(tokenService.verifyJwt(null));
        ClmOnlyOfficeProperties other = new ClmOnlyOfficeProperties();
        other.setJwtSecret("another-secret");
        assertFalse(new OnlyOfficeTokenServiceImpl(other).verifyJwt(jwt));
    }

    @Test
    public void testJwt_disabledWhenSecretBlank() {
        ClmOnlyOfficeProperties properties = new ClmOnlyOfficeProperties();
        properties.setJwtSecret("");
        OnlyOfficeTokenServiceImpl service = new OnlyOfficeTokenServiceImpl(properties);
        assertFalse(service.isJwtEnabled());
        // 紧凑令牌在密钥为空时仍可签发 / 校验（使用内置兜底密钥）
        String token = service.issue(newPayload(OnlyOfficeTokenPayload.PURPOSE_FILE));
        assertNotNull(service.verify(token, OnlyOfficeTokenPayload.PURPOSE_FILE));
    }

}
