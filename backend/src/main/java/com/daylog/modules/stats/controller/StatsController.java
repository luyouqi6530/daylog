package com.daylog.modules.stats.controller;

import com.daylog.common.result.Result;
import com.daylog.modules.stats.service.StatsService;
import com.daylog.modules.stats.vo.HeatmapVO;
import com.daylog.modules.stats.vo.MoodDistributionVO;
import com.daylog.modules.stats.vo.MoodTrendVO;
import com.daylog.modules.stats.vo.StatsOverviewVO;
import com.daylog.modules.stats.vo.TagCloudVO;
import com.daylog.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 统计接口（Redis 缓存 TTL 5 分钟，日记写操作主动失效）
 */
@Tag(name = "统计模块", description = "总览 / 心情趋势 / 分布 / 标签云 / 热力图")
@RestController
@RequestMapping("/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @Operation(summary = "数据总览")
    @GetMapping("/overview")
    public Result<StatsOverviewVO> overview() {
        return Result.success(statsService.overview(SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "心情趋势")
    @GetMapping("/mood-trend")
    public Result<List<MoodTrendVO>> moodTrend(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(statsService.moodTrend(SecurityUtils.getCurrentUserId(), startDate, endDate));
    }

    @Operation(summary = "心情分布")
    @GetMapping("/mood-distribution")
    public Result<List<MoodDistributionVO>> moodDistribution(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(statsService.moodDistribution(SecurityUtils.getCurrentUserId(), startDate, endDate));
    }

    @Operation(summary = "标签云")
    @GetMapping("/tag-cloud")
    public Result<List<TagCloudVO>> tagCloud(@RequestParam(defaultValue = "20") int limit) {
        return Result.success(statsService.tagCloud(SecurityUtils.getCurrentUserId(), limit));
    }

    @Operation(summary = "记录热力图")
    @GetMapping("/heatmap")
    public Result<List<HeatmapVO>> heatmap(@RequestParam(required = false) Integer year) {
        // 不传默认查今年
        int targetYear = year != null ? year : LocalDate.now().getYear();
        return Result.success(statsService.heatmap(SecurityUtils.getCurrentUserId(), targetYear));
    }
}
