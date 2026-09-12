package com.daylog.modules.stats.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 心情趋势项
 */
@Data
public class MoodTrendVO {

    private LocalDate date;

    /** 当天心情分（一天一篇，即该篇分数） */
    private BigDecimal avgMood;

    private Long count;
}
