package com.daylog.modules.stats.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 统计缓存管理
 *
 * <p>缓存策略（Cache-Aside 变体）：</p>
 * <ul>
 *   <li>读：先查 Redis，未命中查库并回填，TTL 5 分钟兜底</li>
 *   <li>写：日记增删改时<b>主动删除</b>该用户全部统计缓存（而非更新），
 *       下次查询自然重建——删除比更新简单且不会出现旧值覆盖新值</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class StatsCacheService {

    public static final String KEY_PREFIX = "stats:";

    private final StringRedisTemplate redisTemplate;

    /**
     * 读缓存，命中返回 JSON 字符串，未命中返回 null
     */
    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * 写缓存，带 TTL（秒）
     */
    public void set(String key, String json, long ttlSeconds) {
        redisTemplate.opsForValue().set(key, json, java.time.Duration.ofSeconds(ttlSeconds));
    }

    /**
     * 日记发生增删改后调用：删除该用户所有统计缓存（主动失效）
     *
     * <p>个人项目数据量小，用 KEYS 模式匹配即可；
     * 生产大键空间应改用 SCAN 渐进遍历，避免阻塞 Redis。</p>
     */
    public void evictByUser(Long userId) {
        Set<String> keys = redisTemplate.keys(KEY_PREFIX + userId + ":*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    /**
     * 构造缓存 key，如 stats:123:overview、stats:123:mood-trend:2026-09-01:2026-09-07
     *
     * <p>userId 必须紧跟 {@code stats:} 前缀：evictByUser 靠 {@code stats:{userId}:*}
     * 整键匹配来失效，参数后缀排在 userId 之后才删得掉。换成 name 在前会让带参数的
     * 四类（mood-trend / mood-distribution / tag-cloud / heatmap）永远漏删。</p>
     */
    public String buildKey(String name, Long userId, Object... params) {
        StringBuilder key = new StringBuilder(KEY_PREFIX).append(userId).append(':').append(name);
        for (Object param : params) {
            key.append(':').append(param);
        }
        return key.toString();
    }
}
