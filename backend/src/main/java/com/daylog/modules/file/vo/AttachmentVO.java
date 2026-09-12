package com.daylog.modules.file.vo;

import lombok.Data;

/**
 * 附件视图对象
 */
@Data
public class AttachmentVO {

    private Long id;

    /** 访问 URL，如 /files/2026/09/uuid.png */
    private String url;

    private String originalName;
}
