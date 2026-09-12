package com.daylog.modules.tag.vo;

import lombok.Data;

/**
 * 标签视图对象（列表 / 标签云共用）
 */
@Data
public class TagVO {

    private Long id;

    private String name;

    private String color;

    /** 该标签下的日记数 */
    private Integer count;
}
