package com.daylog.modules.stats.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

/**
 * StatsCacheService 单元测试
 *
 * <p>这里刻意<b>不 mock {@code buildKey}</b>：evict 的模式串和 buildKey 生成的键名是同一条
 * 契约的两半，把其中一半换成桩就测不到它们是否还匹配（旧版正是这么漏掉四类键的）。
 * RedisTemplate 背后接一个内存键空间，按 Redis glob 语义做整键匹配。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StatsCacheService 单元测试")
class StatsCacheServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    /** 模拟 Redis 键空间 */
    private final Map<String, String> store = new HashMap<>();

    private StatsCacheService cacheService;

    @BeforeEach
    void setUp() {
        cacheService = new StatsCacheService(redisTemplate);

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().doAnswer(inv -> {
            store.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), anyString(), any(Duration.class));
        lenient().when(valueOperations.get(anyString()))
                .thenAnswer(inv -> store.get(inv.<String>getArgument(0)));
        lenient().when(redisTemplate.keys(anyString())).thenAnswer(inv ->
                store.keySet().stream()
                        .filter(k -> redisGlobToRegex(inv.<String>getArgument(0)).matcher(k).matches())
                        .collect(Collectors.toSet()));
        lenient().doAnswer(inv -> {
            Set<String> matched = inv.getArgument(0);
            matched.forEach(store::remove);
            return (long) matched.size();
        }).when(redisTemplate).delete(any(Set.class));
    }

    /** Redis KEYS 的 glob：* 任意长度、? 单字符，且必须匹配整个键名 */
    private static Pattern redisGlobToRegex(String glob) {
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append('.');
                default -> {
                    if ("\\.[]{}()+-^$|".indexOf(c) >= 0) {
                        regex.append('\\');
                    }
                    regex.append(c);
                }
            }
        }
        return Pattern.compile(regex.toString());
    }

    /** 按 StatsServiceImpl 的五个调用点写入该用户的全部缓存键 */
    private void seedAllFiveStats(Long userId) {
        cacheService.set(cacheService.buildKey("overview", userId), "{}", 300);
        cacheService.set(cacheService.buildKey("mood-trend", userId, "2026-09-01", "2026-09-07"), "[]", 300);
        cacheService.set(cacheService.buildKey("mood-distribution", userId, "2026-09-01", "2026-09-07"), "[]", 300);
        cacheService.set(cacheService.buildKey("tag-cloud", userId, 50), "[]", 300);
        cacheService.set(cacheService.buildKey("heatmap", userId, 2026), "[]", 300);
    }

    @Test
    @DisplayName("buildKey：userId 紧跟 stats: 前缀，参数排在 name 之后")
    void buildKey_putsUserIdRightAfterPrefix() {
        assertThat(cacheService.buildKey("overview", 123L)).isEqualTo("stats:123:overview");
        assertThat(cacheService.buildKey("tag-cloud", 123L, 50))
                .isEqualTo("stats:123:tag-cloud:50");
        assertThat(cacheService.buildKey("mood-trend", 123L, "2026-09-01", "2026-09-07"))
                .isEqualTo("stats:123:mood-trend:2026-09-01:2026-09-07");
    }

    @Test
    @DisplayName("evictByUser：五个统计键全被删掉（带日期/limit/year 后缀的四类也漏不掉）")
    void evictByUser_removesAllFiveStatKeysIncludingParamSuffixedOnes() {
        seedAllFiveStats(1L);
        assertThat(store).hasSize(5);

        cacheService.evictByUser(1L);

        assertThat(store).isEmpty();
    }

    @Test
    @DisplayName("evictByUser：只清自己，别人的同名键与别人的 overview 都还在")
    void evictByUser_leavesOtherUsersUntouched() {
        seedAllFiveStats(1L);
        cacheService.set(cacheService.buildKey("overview", 2L), "{}", 300);
        cacheService.set(cacheService.buildKey("tag-cloud", 2L, 50), "[]", 300);
        // 前缀同形但非本用户命名空间（userId 是 12，模式串是 stats:1:*）
        cacheService.set(cacheService.buildKey("heatmap", 12L, 2026), "[]", 300);

        cacheService.evictByUser(1L);

        assertThat(store.keySet()).containsExactlyInAnyOrder(
                "stats:2:overview", "stats:2:tag-cloud:50", "stats:12:heatmap:2026");
    }

    @Test
    @DisplayName("evictByUser：无命中时不发起 delete（空集合不进 Redis）")
    void evictByUser_noKeys_skipsDelete() {
        cacheService.evictByUser(99L);

        assertThat(store).isEmpty();
        org.mockito.Mockito.verify(redisTemplate, org.mockito.Mockito.never()).delete(any(Set.class));
    }
}
