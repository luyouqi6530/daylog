package com.daylog.modules.stats.service;

import com.daylog.modules.stats.vo.HeatmapVO;
import com.daylog.modules.stats.vo.MoodDistributionVO;
import com.daylog.modules.stats.vo.MoodTrendVO;
import com.daylog.modules.stats.vo.StatsOverviewVO;
import com.daylog.modules.stats.vo.TagCloudVO;

import java.time.LocalDate;
import java.util.List;

/**
 * 统计服务
 */
public interface StatsService {

    StatsOverviewVO overview(Long userId);

    List<MoodTrendVO> moodTrend(Long userId, LocalDate startDate, LocalDate endDate);

    List<MoodDistributionVO> moodDistribution(Long userId, LocalDate startDate, LocalDate endDate);

    List<TagCloudVO> tagCloud(Long userId, int limit);

    List<HeatmapVO> heatmap(Long userId, int year);
}
