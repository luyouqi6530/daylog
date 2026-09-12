package com.daylog.modules.stats.vo;

import lombok.Data;

/**
 * 数据总览视图对象
 */
@Data
public class StatsOverviewVO {

    /** 累计日记篇数 */
    private Long totalDiaries;

    /** 累计字数 */
    private Long totalWords;

    /** 当前连续记录天数（今天或昨天有记录则连续中） */
    private Integer currentStreak;

    /** 最长连续记录天数 */
    private Integer longestStreak;

    /** 本月记录篇数 */
    private Long monthCount;
}
