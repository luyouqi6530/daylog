package com.daylog.modules.diary.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 创建/更新日记入参
 */
@Data
public class DiarySaveDTO {

    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题最长 100 字")
    private String title;

    @NotBlank(message = "正文不能为空")
    private String content;

    @NotNull(message = "心情分不能为空")
    @Min(value = 1, message = "心情分取值 1-5")
    @Max(value = 5, message = "心情分取值 1-5")
    private Integer moodScore;

    @Size(max = 20, message = "天气最长 20 字")
    private String weather;

    @NotNull(message = "记录日期不能为空")
    private LocalDate recordDate;

    /** 关联标签ID列表 */
    private List<Long> tagIds;

    /** 先上传后回填的附件ID列表 */
    private List<Long> attachmentIds;
}
