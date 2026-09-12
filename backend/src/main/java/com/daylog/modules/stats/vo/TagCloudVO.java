package com.daylog.modules.stats.vo;

import lombok.Data;

/**
 * 标签云项（与 TagVO 字段一致但属于统计视角，独立维护避免模块耦合）
 */
@Data
public class TagCloudVO {

    private Long tagId;

    private String name;

    private String color;

    private Integer count;
}
