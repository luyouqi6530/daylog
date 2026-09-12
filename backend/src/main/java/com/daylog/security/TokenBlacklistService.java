package com.daylog.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * JWT 黑名单服务（Redis 实现）
 *
 * <p>JWT 本身无状态，签发后无法主动作废。登出时将 token 写入
 * Redis 黑名单，TTL 设为 token 剩余有效期——到期自动清理，
 * 不会让 Redis 堆积已过期 token。这是"无状态令牌 + 主动失效"
 * 的经典折中方案。</p>
 */
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String KEY_PREFIX = "jwt:blacklist:";

    private final StringRedisTemplate redisTemplate;

    /**
     * 将 token 加入黑名单
     *
     * @param token      JWT 原文
     * @param ttlMillis  剩余有效期（毫秒）
     */
    public void blacklist(String token, long ttlMillis) {
        if (ttlMillis <= 0) {
            // 已过期的 token 无需入黑名单
            return;
        }
        redisTemplate.opsForValue().set(KEY_PREFIX + token, "1", ttlMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * 判断 token 是否已被拉黑（登出过的 token 仍会被携带请求，必须拦截）
     */
    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + token));
    }
}
