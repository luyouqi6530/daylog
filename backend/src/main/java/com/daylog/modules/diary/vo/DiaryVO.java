package com.daylog.modules.diary.vo;

import com.daylog.modules.tag.vo.TagVO;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 日记列表项视图对象
 */
@Data
public class DiaryVO {

    private Long id;

    private String title;

    private String content;

    private Integer moodScore;

    private String weather;

    private LocalDate recordDate;

    private Integer wordCount;

    private List<TagVO> tags = new ArrayList<>();

    /** 配图数量（列表页不返回图片明细，只给个角标） */
    private Integer imageCount = 0;

    private LocalDateTime createdAt;
}
