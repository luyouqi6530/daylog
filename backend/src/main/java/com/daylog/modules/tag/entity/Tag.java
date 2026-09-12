package com.daylog.modules.tag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 标签实体，对应 tag 表（物理删除，删除时级联清理 diary_tag 关联）
 */
@Data
@TableName("tag")
public class Tag implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String name;

    /** HEX 颜色，如 #409EFF */
    private String color;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
