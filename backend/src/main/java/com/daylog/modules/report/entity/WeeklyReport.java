package com.daylog.modules.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * AI 周报实体，对应 weekly_report 表
 *
 * <p>同周重复生成时覆盖更新（唯一索引 uk_user_week + 服务层 upsert）</p>
 */
@Data
@TableName("weekly_report")
public class WeeklyReport implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    /** 周一日期，该周唯一标识 */
    private LocalDate weekStart;

    private LocalDate weekEnd;

    private Integer diaryCount;

    /** 本周平均心情分 */
    private BigDecimal moodAvg;

    /** AI 生成的周报内容（Markdown） */
    private String summary;

    private String model;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
