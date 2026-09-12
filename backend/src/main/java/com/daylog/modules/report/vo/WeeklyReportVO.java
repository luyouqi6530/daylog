package com.daylog.modules.report.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 周报视图对象
 */
@Data
public class WeeklyReportVO {

    private Long id;

    private LocalDate weekStart;

    private LocalDate weekEnd;

    private Integer diaryCount;

    private BigDecimal moodAvg;

    private String summary;

    private String model;

    private LocalDateTime createdAt;
}
