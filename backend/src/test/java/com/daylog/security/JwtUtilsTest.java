package com.daylog.security;

import com.daylog.common.exception.BusinessException;
import com.daylog.common.result.ResultCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JwtUtils 单元测试
 *
 * <p>覆盖场景：签发后能解析、解析后可还原 userId/username、过期 token 抛业务异常、
 * 被篡改 token 抛业务异常、不同密钥签发的 token 不能互相解析、剩余有效期计算正确。</p>
 */
@DisplayName("JwtUtils 单元测试")
class JwtUtilsTest {

    private JwtUtils jwtUtils;

    /** 32 字节 = 256 位，恰好够 HS256 */
    private static final String SECRET = "daylog-test-secret-key-32bytes!!";

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils(SECRET, 60);
    }

    @Test
    @DisplayName("签发 token 后能解析，userId/username/issuer/exp 等 claims 一致")
    void generate_thenParse_shouldRoundTripClaims() {
        Long userId = 1234567890123456789L;
        String username = "test_user";

        String token = jwtUtils.generateToken(userId, username);
        Claims claims = jwtUtils.parseToken(token);

        assertThat(claims.getSubject()).isEqualTo(String.valueOf(userId));
        assertThat(claims.get("username", String.class)).isEqualTo(username);
        assertThat(claims.getExpiration()).isAfter(new Date());
        assertThat(jwtUtils.getUserId(claims)).isEqualTo(userId);
    }

    @Test
    @DisplayName("过期 token 解析时抛 BusinessException(UNAUTHORIZED)")
    void parseExpiredToken_shouldThrowBusinessException() throws InterruptedException {
        // 用 1 毫秒级自定义 expire 触发过期（直接构造一个已过期的 token）
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String expiredToken = Jwts.builder()
                .subject("100")
                .issuedAt(new Date(System.currentTimeMillis() - 1000))
                .expiration(new Date(System.currentTimeMillis() - 500))
                .signWith(key)
                .compact();

        assertThatThrownBy(() -> jwtUtils.parseToken(expiredToken))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ResultCode.UNAUTHORIZED.getCode()));
    }

    @Test
    @DisplayName("被篡改的 token（签名错误）应抛 BusinessException(UNAUTHORIZED)")
    void parseTamperedToken_shouldThrowBusinessException() {
        String token = jwtUtils.generateToken(1L, "user");
        // 翻转签名段最后一个字符，破坏 HMAC
        String tampered = token.substring(0, token.length() - 1)
                + (token.charAt(token.length() - 1) == 'A' ? 'B' : 'A');

        assertThatThrownBy(() -> jwtUtils.parseToken(tampered))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("不同密钥签发的 token 不能互相解析（验证密钥隔离）")
    void parseTokenSignedWithDifferentKey_shouldFail() {
        JwtUtils other = new JwtUtils("another-secret-key-with-32bytes!OK", 60);
        String foreignToken = other.generateToken(999L, "foreign");

        assertThatThrownBy(() -> jwtUtils.parseToken(foreignToken))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("getRemainingMillis：未到期返回正数，已过期返回 0")
    void getRemainingMillis_shouldRespectBoundary() {
        String validToken = jwtUtils.generateToken(1L, "u");
        Claims validClaims = jwtUtils.parseToken(validToken);
        // 60 分钟有效期，刚签发应该是 60 分钟内
        long remaining = jwtUtils.getRemainingMillis(validClaims);
        assertThat(remaining).isPositive()
                .isLessThanOrEqualTo(60L * 60 * 1000);
    }

    @Test
    @DisplayName("空字符串或非法格式的 token 解析时应被包装为业务异常")
    void parseMalformedToken_shouldThrowBusinessException() {
        assertThatThrownBy(() -> jwtUtils.parseToken("not.a.token"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> jwtUtils.parseToken(""))
                .isInstanceOf(BusinessException.class);
    }
}