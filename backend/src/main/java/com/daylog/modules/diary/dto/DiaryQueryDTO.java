package com.daylog.modules.diary.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 日记列表查询入参（GET query 参数绑定）
 */
@Data
public class DiaryQueryDTO {

    private LocalDate startDate;

    private LocalDate endDate;

    /** 心情分筛选 1-5 */
    private Integer mood;

    /** 标签筛选 */
    private Long tagId;

    /** 标题/正文关键词 */
    private String keyword;

    /** 页码，默认 1 */
    private Integer page = 1;

    /** 每页条数，默认 10，最大 50 */
    private Integer size = 10;
}
