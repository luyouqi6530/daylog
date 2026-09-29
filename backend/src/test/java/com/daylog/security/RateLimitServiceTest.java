package com.daylog.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RateLimitService 单元测试
 *
 * <p>契约：INCR 计数、首次命中补一个 TTL、窗口编号写进 key、
 * Redis 抛异常时返回 -1（由调用方按放行处理）。
 * 这里 mock 掉 Redis，只验证与 Redis 的交互形状。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RateLimitService 单元测试")
class RateLimitServiceTest {

    private static final String LOGIN_KEY_PREFIX = "ratelimit:login:1.2.3.4:";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private RateLimitService service;

    @BeforeEach
    void setUp() {
        service = new RateLimitService(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    @DisplayName("key 格式为 ratelimit:<action>:<ip>:<窗口编号>")
    void countInWindow_shouldBuildBucketedKey() {
        when(valueOps.increment(anyString())).thenReturn(1L);

        service.countInWindow("login", "1.2.3.4", 60);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).increment(keyCaptor.capture());
        assertThat(keyCaptor.getValue()).startsWith(LOGIN_KEY_PREFIX);
        assertThat(Long.parseLong(keyCaptor.getValue().substring(LOGIN_KEY_PREFIX.length())))
                .isEqualTo(System.currentTimeMillis() / 1000 / 60);
    }

    @Test
    @DisplayName("窗口内第一次访问（count==1）补 TTL=2 倍窗口，后续命中不再补")
    void countInWindow_shouldSetExpireOnlyOnFirstHit() {
        when(valueOps.increment(anyString())).thenReturn(1L, 2L, 3L);

        assertThat(service.countInWindow("login", "1.2.3.4", 60)).isEqualTo(1L);
        assertThat(service.countInWindow("login", "1.2.3.4", 60)).isEqualTo(2L);
        assertThat(service.countInWindow("login", "1.2.3.4", 60)).isEqualTo(3L);

        verify(redisTemplate, times(1)).expire(anyString(), eq(120L), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("Redis 异常返回 -1，不抛出（限流不能自己成为故障源）")
    void countInWindow_whenRedisFails_returnsMinusOne() {
        when(valueOps.increment(anyString()))
                .thenThrow(new DataAccessResourceFailureException("connection refused"));

        assertThat(service.countInWindow("login", "1.2.3.4", 60)).isEqualTo(-1L);
    }

    @Test
    @DisplayName("INCR 返回 null 时按 -1 处理（不 NPE）")
    void countInWindow_whenIncrementReturnsNull_returnsMinusOne() {
        when(valueOps.increment(anyString())).thenReturn(null);

        assertThat(service.countInWindow("login", "1.2.3.4", 60)).isEqualTo(-1L);
    }
}
