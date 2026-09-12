package com.daylog.modules.diary.vo;

import lombok.Data;

import java.time.LocalDate;

/**
 * 日历视图项：某天的记录日期 + 心情分
 */
@Data
public class CalendarDayVO {

    private LocalDate recordDate;

    private Integer moodScore;
}
