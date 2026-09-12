package com.daylog.modules.stats.vo;

import lombok.Data;

/**
 * 心情分布项
 */
@Data
public class MoodDistributionVO {

    private Integer moodScore;

    private Long count;
}
