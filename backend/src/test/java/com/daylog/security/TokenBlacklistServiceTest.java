package com.daylog.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TokenBlacklistService 单元测试
 *
 * <p>Redis 黑名单语义：登出时把 token 写入 Redis（key 带 jwt:blacklist: 前缀），TTL = 剩余有效期；
 * 每次请求过滤器查 hasKey；TTL ≤ 0 的 token（已自然过期）直接跳过 Redis。
 * 验证这些契约 + key 拼接格式。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // setUp 中的 stubbing 跨测试复用，部分测试用不到，故放开严格检查
@DisplayName("TokenBlacklistService 单元测试")
class TokenBlacklistServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private TokenBlacklistService service;

    @BeforeEach
    void setUp() {
        service = new TokenBlacklistService(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    @DisplayName("blacklist：写入 Redis 时 key 带前缀，TTL=剩余毫秒")
    void blacklist_shouldWriteWithPrefixAndTtl() {
        String token = "eyJhbGciOiJIUzI1NiJ9.payload.signature";
        long ttlMillis = 60_000L; // 1 分钟

        service.blacklist(token, ttlMillis);

        verify(valueOps).set(
                eq("jwt:blacklist:" + token),
                eq("1"),
                eq(ttlMillis),
                eq(TimeUnit.MILLISECONDS)
        );
    }

    @Test
    @DisplayName("blacklist：TTL ≤ 0 时跳过写入（已自然过期，无需入黑名单）")
    void blacklist_withZeroOrNegativeTtl_shouldSkip() {
        String token = "expired.token";

        service.blacklist(token, 0L);
        service.blacklist(token, -100L);

        verify(valueOps, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("isBlacklisted：Redis 命中返回 true")
    void isBlacklisted_whenKeyExists_returnsTrue() {
        String token = "blacklisted.token";
        when(redisTemplate.hasKey("jwt:blacklist:" + token)).thenReturn(true);

        assertThat(service.isBlacklisted(token)).isTrue();
    }

    @Test
    @DisplayName("isBlacklisted：Redis 未命中返回 false")
    void isBlacklisted_whenKeyMissing_returnsFalse() {
        String token = "fresh.token";
        when(redisTemplate.hasKey("jwt:blacklist:" + token)).thenReturn(false);

        assertThat(service.isBlacklisted(token)).isFalse();
    }

    @Test
    @DisplayName("isBlacklisted：Redis 返回 null 时按未命中处理（不 NPE）")
    void isBlacklisted_whenRedisReturnsNull_returnsFalse() {
        String token = "any.token";
        when(redisTemplate.hasKey(anyString())).thenReturn(null);

        assertThat(service.isBlacklisted(token)).isFalse();
    }
}