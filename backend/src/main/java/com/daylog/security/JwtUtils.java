package com.daylog.security;

import com.daylog.common.exception.BusinessException;
import com.daylog.common.result.ResultCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类（基于 jjwt 0.12.x 新 API）
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>subject 存 userId（字符串形式，避免 Long 精度问题）</li>
 *   <li>HMAC-SHA256 对称加密，密钥从配置注入（生产走环境变量）</li>
 *   <li>解析失败统一抛业务异常，由全局异常处理器兜底</li>
 * </ul>
 */
@Component
public class JwtUtils {

    private final SecretKey key;

    private final long expireMinutes;

    public JwtUtils(@Value("${daylog.jwt.secret}") String secret,
                    @Value("${daylog.jwt.expire-minutes}") long expireMinutes) {
        // HMAC-SHA256 要求密钥至少 256 位（32 字节），配置过短会在这里快速失败
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMinutes = expireMinutes;
    }

    /**
     * 生成 token
     *
     * @param userId   用户ID
     * @param username 用户名（冗余存入 claim，方便日志排查）
     */
    public String generateToken(Long userId, String username) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expireMinutes * 60 * 1000);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    /**
     * 解析 token，返回 Claims
     *
     * @throws BusinessException token 无效或已过期
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            // 已过期会抛 ExpiredJwtException（JwtException 子类），统一按 401 处理
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
    }

    /**
     * 从 Claims 中取用户ID
     */
    public Long getUserId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    /**
     * 计算 token 剩余有效毫秒数（登出时用它作为黑名单 key 的 TTL）
     */
    public long getRemainingMillis(Claims claims) {
        return Math.max(0, claims.getExpiration().getTime() - System.currentTimeMillis());
    }
}
