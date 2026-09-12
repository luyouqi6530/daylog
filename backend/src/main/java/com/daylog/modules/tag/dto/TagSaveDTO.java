package com.daylog.modules.tag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建/更新标签入参
 */
@Data
public class TagSaveDTO {

    @NotBlank(message = "标签名不能为空")
    @Size(max = 20, message = "标签名最长 20 字")
    private String name;

    /** HEX 颜色，不传或格式错误时用默认色 */
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "颜色格式应为 #RRGGBB")
    private String color = "#409EFF";
}
