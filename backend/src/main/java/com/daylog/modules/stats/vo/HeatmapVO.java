package com.daylog.modules.stats.vo;

import lombok.Data;

import java.time.LocalDate;

/**
 * 记录热力图项（GitHub 风格贡献图数据源）
 */
@Data
public class HeatmapVO {

    private LocalDate date;

    private Integer moodScore;
}
