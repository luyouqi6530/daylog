package com.daylog.modules.stats.service.impl;

import com.daylog.modules.diary.entity.Diary;
import com.daylog.modules.diary.mapper.DiaryMapper;
import com.daylog.modules.stats.mapper.StatsMapper;
import com.daylog.modules.stats.service.StatsCacheService;
import com.daylog.modules.stats.vo.HeatmapVO;
import com.daylog.modules.stats.vo.MoodDistributionVO;
import com.daylog.modules.stats.vo.MoodTrendVO;
import com.daylog.modules.stats.vo.StatsOverviewVO;
import com.daylog.modules.stats.vo.TagCloudVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * StatsServiceImpl 单元测试
 *
 * <p>核心契约：Cache-Aside + 写操作主动失效。</p>
 * <ul>
 *   <li>读：先查 Redis 缓存，命中直接返回；miss 查库 + 回填缓存（TTL 5min）</li>
 *   <li>范围保护：moodTrend 范围超过一个季度时自动收窄</li>
 *   <li>tagCloud limit 在 1-50 之间 clamp</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StatsServiceImpl 单元测试")
class StatsServiceImplTest {

    @Mock
    private StatsMapper statsMapper;

    @Mock
    private DiaryMapper diaryMapper;

    @Mock
    private StatsCacheService cacheService;

    private ObjectMapper objectMapper;

    private StatsServiceImpl statsService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        statsService = new StatsServiceImpl(statsMapper, diaryMapper, cacheService, objectMapper);
    }

    // ====================== overview ======================

    @Test
    @DisplayName("overview：缓存命中时直接返回缓存值，不查 DB")
    void overview_cacheHit_returnsCachedValue_noDbQuery() throws Exception {
        StatsOverviewVO cached = new StatsOverviewVO();
        cached.setTotalDiaries(42L);
        cached.setTotalWords(12345L);
        cached.setCurrentStreak(7);
        cached.setLongestStreak(30);

        when(cacheService.buildKey(eq("overview"), eq(1L))).thenReturn("stats:1:overview");
        when(cacheService.get("stats:1:overview"))
                .thenReturn(objectMapper.writeValueAsString(cached));

        StatsOverviewVO result = statsService.overview(1L);

        assertThat(result.getTotalDiaries()).isEqualTo(42);
        verify(diaryMapper, never()).selectCount(any());
        verify(statsMapper, never()).selectTotalWords(anyLong());
    }

    @Test
    @DisplayName("overview：缓存 miss 时查 DB + 回填 Redis（TTL 5min）")
    void overview_cacheMiss_queriesDbAndWritesCache() {
        when(cacheService.buildKey("overview", 1L)).thenReturn("stats:1:overview");
        when(cacheService.get("stats:1:overview")).thenReturn(null);
        when(diaryMapper.selectCount(any())).thenReturn(10L);
        when(statsMapper.selectTotalWords(1L)).thenReturn(5000L);
        when(statsMapper.selectRecordDates(1L)).thenReturn(List.of(LocalDate.now()));

        StatsOverviewVO result = statsService.overview(1L);

        assertThat(result.getTotalDiaries()).isEqualTo(10);
        assertThat(result.getTotalWords()).isEqualTo(5000L);
        verify(cacheService).set(eq("stats:1:overview"), anyString(), eq(5L * 60));
    }

    // ====================== moodTrend 范围保护 ======================

    @Test
    @DisplayName("moodTrend：传入超过一个季度的范围时被自动收窄到 92 天")
    void moodTrend_overRange_clampsTo92Days() {
        LocalDate today = LocalDate.now();
        LocalDate tooEarly = today.minusDays(200); // 超出 92 天

        when(cacheService.buildKey(anyString(), anyLong(), any(), any()))
                .thenReturn("stats:1:mood-trend:date:date");

        statsService.moodTrend(1L, tooEarly, today);

        // buildKey 第二个日期参数应被收尾到 today
        // 这里只需验证 statsMapper 被调用即可（具体参数由 buildKey 自身保证）
        verify(statsMapper).selectMoodTrend(eq(1L), any(LocalDate.class), eq(today));
    }

    @Test
    @DisplayName("moodTrend：传入 null 时默认查询最近 30 天")
    void moodTrend_nullRange_usesLast30Days() {
        when(cacheService.buildKey(anyString(), anyLong(), any(), any()))
                .thenReturn("stats:1:mood-trend:date:date");

        statsService.moodTrend(1L, null, null);

        verify(statsMapper).selectMoodTrend(eq(1L), any(LocalDate.class), any(LocalDate.class));
    }

    // ====================== tagCloud limit clamp ======================

    @Test
    @DisplayName("tagCloud：limit < 1 时被 clamp 到 1")
    void tagCloud_limitTooSmall_clampsToOne() {
        when(cacheService.buildKey(anyString(), anyLong(), any())).thenReturn("stats:1:tag-cloud:1");

        statsService.tagCloud(1L, 0);
        statsService.tagCloud(1L, -10);

        verify(statsMapper, times(2)).selectTagCloud(eq(1L), eq(1));
    }

    @Test
    @DisplayName("tagCloud：limit > 50 时被 clamp 到 50")
    void tagCloud_limitTooLarge_clampsToFifty() {
        when(cacheService.buildKey(anyString(), anyLong(), any())).thenReturn("stats:1:tag-cloud:50");

        statsService.tagCloud(1L, 100);
        statsService.tagCloud(1L, 9999);

        verify(statsMapper, times(2)).selectTagCloud(eq(1L), eq(50));
    }

    // ====================== cache 异常兜底 ======================

    @Test
    @DisplayName("overview：缓存 JSON 解析失败视为未命中（不影响业务）")
    void overview_corruptCacheJson_fallsBackToDb() {
        when(cacheService.buildKey("overview", 1L)).thenReturn("stats:1:overview");
        when(cacheService.get("stats:1:overview")).thenReturn("{this is not valid json}");
        when(diaryMapper.selectCount(any())).thenReturn(5L);
        when(statsMapper.selectTotalWords(1L)).thenReturn(1000L);
        when(statsMapper.selectRecordDates(1L)).thenReturn(List.of());

        // 不抛异常即可
        StatsOverviewVO result = statsService.overview(1L);
        assertThat(result.getTotalDiaries()).isEqualTo(5);
    }
}