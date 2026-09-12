package com.daylog.modules.diary.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.daylog.modules.diary.entity.Diary;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/**
 * 日记 Mapper：单表 CRUD 走 MyBatis-Plus BaseMapper，零手写 SQL
 *
 * <p>统计聚合查询（心情趋势/分布/热力图）归 stats 模块的 StatsMapper，
 * 模块职责单一，避免 Mapper 成为杂物间。</p>
 */
@Mapper
public interface DiaryMapper extends BaseMapper<Diary> {

    /**
     * 指定日期范围内有日记的用户ID列表（AI 周报定时任务用）
     */
    @Select("""
            SELECT DISTINCT user_id
            FROM diary
            WHERE record_date BETWEEN #{startDate} AND #{endDate}
            """)
    List<Long> selectUserIdsBetween(@Param("startDate") LocalDate startDate,
                                    @Param("endDate") LocalDate endDate);
}
