package com.daylog.modules.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 附件实体，对应 attachment 表
 *
 * <p>设计：先上传后关联。上传时 diary_id 为空，日记保存时回填；
 * 24 小时内未关联的附件由定时任务清理，防止孤儿文件占用存储。</p>
 */
@Data
@TableName("attachment")
public class Attachment implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    /** 所属日记ID，上传时未保存日记则为空 */
    private Long diaryId;

    private String originalName;

    /** 存储相对路径，如 2026/09/uuid.png */
    private String filePath;

    private Long fileSize;

    private String mimeType;

    private LocalDateTime createdAt;
}
