package com.daylog.modules.diary.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 日记-标签关联实体，对应 diary_tag 表（联合主键，由业务代码维护）
 */
@Data
@TableName("diary_tag")
public class DiaryTag implements Serializable {

    private Long diaryId;

    private Long tagId;
}
