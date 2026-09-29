package com.daylog.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 固定窗口计数限流（Redis INCR）
 *
 * <p>窗口编号直接写进 key（{@code ratelimit:login:1.2.3.4:29475123}），
 * 所以 TTL 只负责回收内存、不参与正确性：即使 EXPIRE 因网络抖动失败，
 * 下一个窗口用的是新 key，绝不会把某个 IP 永久锁死。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private static final String KEY_PREFIX = "ratelimit:";

    private final StringRedisTemplate redisTemplate;

    /**
     * 记一次访问并返回当前窗口内的累计次数
     *
     * @param action        业务动作名，作为 key 的一部分（login / register）
     * @param identity      限流维度标识（客户端 IP）
     * @param windowSeconds 窗口长度（秒）
     * @return 窗口内第几次访问；返回 -1 表示 Redis 不可用（调用方按放行处理）
     */
    public long countInWindow(String action, String identity, int windowSeconds) {
        long windowIndex = System.currentTimeMillis() / 1000 / windowSeconds;
        String key = KEY_PREFIX + action + ":" + identity + ":" + windowIndex;
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                // 多留一个窗口，纯做内存清理
                redisTemplate.expire(key, windowSeconds * 2L, TimeUnit.SECONDS);
            }
            return count == null ? -1L : count;
        } catch (DataAccessException e) {
            log.warn("限流计数不可用（Redis 异常），本次放行: {}", e.getMessage());
            return -1L;
        }
    }
}
