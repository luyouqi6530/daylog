package com.daylog.modules.stats.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.daylog.modules.diary.entity.Diary;
import com.daylog.modules.diary.mapper.DiaryMapper;
import com.daylog.modules.stats.mapper.StatsMapper;
import com.daylog.modules.stats.service.StatsCacheService;
import com.daylog.modules.stats.service.StatsService;
import com.daylog.modules.stats.vo.HeatmapVO;
import com.daylog.modules.stats.vo.MoodDistributionVO;
import com.daylog.modules.stats.vo.MoodTrendVO;
import com.daylog.modules.stats.vo.StatsOverviewVO;
import com.daylog.modules.stats.vo.TagCloudVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 统计服务实现
 *
 * <p>所有查询走 Redis 缓存（TTL 5 分钟），日记增删改时由 DiaryService
 * 调用 StatsCacheService.evictByUser 主动失效。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {

    /** 缓存 TTL：5 分钟 */
    private static final long CACHE_TTL_SECONDS = 5 * 60;

    /** 心情趋势最大查询范围：一个季度 */
    private static final int MAX_TREND_RANGE_DAYS = 92;

    private final StatsMapper statsMapper;
    private final DiaryMapper diaryMapper;
    private final StatsCacheService cacheService;
    private final ObjectMapper objectMapper;

    @Override
    public StatsOverviewVO overview(Long userId) {
        String key = cacheService.buildKey("overview", userId);
        StatsOverviewVO cached = getFromCache(key, new TypeReference<>() {
        });
        if (cached != null) {
            return cached;
        }

        StatsOverviewVO vo = new StatsOverviewVO();
        vo.setTotalDiaries(diaryMapper.selectCount(
                new LambdaQueryWrapper<Diary>().eq(Diary::getUserId, userId)));
        vo.setTotalWords(statsMapper.selectTotalWords(userId));
        vo.setMonthCount(diaryMapper.selectCount(
                new LambdaQueryWrapper<Diary>()
                        .eq(Diary::getUserId, userId)
                        .ge(Diary::getRecordDate, LocalDate.now().withDayOfMonth(1))));

        computeStreaks(userId, vo);

        putIntoCache(key, vo);
        return vo;
    }

    @Override
    public List<MoodTrendVO> moodTrend(Long userId, LocalDate startDate, LocalDate endDate) {
        LocalDate today = LocalDate.now();
        if (startDate == null) {
            startDate = today.minusDays(29);
        }
        if (endDate == null || endDate.isAfter(today)) {
            endDate = today;
        }
        // 范围保护：最长一个季度，防止恶意大范围查询拖垮数据库
        if (ChronoUnit.DAYS.between(startDate, endDate) > MAX_TREND_RANGE_DAYS) {
            startDate = endDate.minusDays(MAX_TREND_RANGE_DAYS);
        }

        LocalDate finalStart = startDate;
        String key = cacheService.buildKey("mood-trend", userId, finalStart, endDate);
        List<MoodTrendVO> cached = getFromCache(key, new TypeReference<>() {
        });
        if (cached != null) {
            return cached;
        }

        List<MoodTrendVO> list = statsMapper.selectMoodTrend(userId, finalStart, endDate);
        putIntoCache(key, list);
        return list;
    }

    @Override
    public List<MoodDistributionVO> moodDistribution(Long userId, LocalDate startDate, LocalDate endDate) {
        LocalDate today = LocalDate.now();
        if (startDate == null) {
            startDate = today.minusDays(89);
        }
        if (endDate == null || endDate.isAfter(today)) {
            endDate = today;
        }

        LocalDate finalStart = startDate;
        String key = cacheService.buildKey("mood-distribution", userId, finalStart, endDate);
        List<MoodDistributionVO> cached = getFromCache(key, new TypeReference<>() {
        });
        if (cached != null) {
            return cached;
        }

        List<MoodDistributionVO> list = statsMapper.selectMoodDistribution(userId, finalStart, endDate);
        putIntoCache(key, list);
        return list;
    }

    @Override
    public List<TagCloudVO> tagCloud(Long userId, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        String key = cacheService.buildKey("tag-cloud", userId, safeLimit);
        List<TagCloudVO> cached = getFromCache(key, new TypeReference<>() {
        });
        if (cached != null) {
            return cached;
        }

        List<TagCloudVO> list = statsMapper.selectTagCloud(userId, safeLimit);
        putIntoCache(key, list);
        return list;
    }

    @Override
    public List<HeatmapVO> heatmap(Long userId, int year) {
        String key = cacheService.buildKey("heatmap", userId, year);
        List<HeatmapVO> cached = getFromCache(key, new TypeReference<>() {
        });
        if (cached != null) {
            return cached;
        }

        List<HeatmapVO> list = statsMapper.selectHeatmap(userId,
                LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
        putIntoCache(key, list);
        return list;
    }

    /**
     * 计算当前连续与最长连续记录天数
     *
     * <p>个人项目数据量小，直接加载全部记录日期到内存遍历；
     * 若日记上万条，可改用 SQL 窗口函数（DATEDIFF 分组法）。</p>
     */
    private void computeStreaks(Long userId, StatsOverviewVO vo) {
        List<LocalDate> dates = statsMapper.selectRecordDates(userId);
        if (dates.isEmpty()) {
            vo.setCurrentStreak(0);
            vo.setLongestStreak(0);
            return;
        }

        // ---- 最长连续：一次遍历 ----
        int longest = 1;
        int current = 1;
        for (int i = 1; i < dates.size(); i++) {
            if (dates.get(i - 1).minusDays(1).equals(dates.get(i))) {
                current++;
                longest = Math.max(longest, current);
            } else {
                current = 1;
            }
        }

        // ---- 当前连续：从最新一条往前数 ----
        // 今天没写但昨天写了， streak 仍算连续中（今天还有机会写）
        LocalDate latest = dates.get(0);
        LocalDate today = LocalDate.now();
        if (latest.isBefore(today.minusDays(1))) {
            vo.setCurrentStreak(0);
        } else {
            int streak = 1;
            for (int i = 1; i < dates.size(); i++) {
                if (dates.get(i - 1).minusDays(1).equals(dates.get(i))) {
                    streak++;
                } else {
                    break;
                }
            }
            vo.setCurrentStreak(streak);
        }
        vo.setLongestStreak(longest);
    }

    /**
     * 读缓存：命中反序列化，异常（结构变更等）视为未命中并删 key
     */
    private <T> T getFromCache(String key, TypeReference<T> type) {
        try {
            String json = cacheService.get(key);
            return json == null ? null : objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("统计缓存反序列化失败，按未命中处理: {}", key);
            return null;
        }
    }

    private void putIntoCache(String key, Object value) {
        try {
            cacheService.set(key, objectMapper.writeValueAsString(value), CACHE_TTL_SECONDS);
        } catch (Exception e) {
            // 缓存写失败不影响主流程，下次查询再写
            log.warn("统计缓存写入失败: {}", key);
        }
    }
}
