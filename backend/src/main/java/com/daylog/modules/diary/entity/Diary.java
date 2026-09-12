package com.daylog.modules.diary.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 日记实体，对应 diary 表
 *
 * <p>物理删除（与逻辑删除和唯一索引 uk_user_date 冲突，见 schema.sql 设计说明）</p>
 */
@Data
@TableName("diary")
public class Diary implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String title;

    /** 正文（Markdown） */
    private String content;

    /** 心情分 1-5 */
    private Integer moodScore;

    private String weather;

    /** 记录日期（业务日期） */
    private LocalDate recordDate;

    /** 正文字数（冗余存储，加速统计，由服务层计算 */
    private Integer wordCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
