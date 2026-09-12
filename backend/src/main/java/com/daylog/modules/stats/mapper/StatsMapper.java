package com.daylog.modules.stats.mapper;

import com.daylog.modules.stats.vo.HeatmapVO;
import com.daylog.modules.stats.vo.MoodDistributionVO;
import com.daylog.modules.stats.vo.MoodTrendVO;
import com.daylog.modules.stats.vo.TagCloudVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/**
 * 统计 Mapper：聚合查询统一收口在统计模块
 *
 * <p>注：本 Mapper 无实体（纯聚合查询），不继承 BaseMapper，
 * 但必须是接口且被 @MapperScan 扫描到。</p>
 */
@Mapper
public interface StatsMapper {

    @Select("""
            SELECT record_date AS date,
                   ROUND(AVG(mood_score), 1) AS avg_mood,
                   COUNT(*) AS count
            FROM diary
            WHERE user_id = #{userId}
              AND record_date BETWEEN #{startDate} AND #{endDate}
            GROUP BY record_date
            ORDER BY record_date
            """)
    List<MoodTrendVO> selectMoodTrend(@Param("userId") Long userId,
                                      @Param("startDate") LocalDate startDate,
                                      @Param("endDate") LocalDate endDate);

    @Select("""
            SELECT mood_score AS mood_score, COUNT(*) AS count
            FROM diary
            WHERE user_id = #{userId}
              AND record_date BETWEEN #{startDate} AND #{endDate}
            GROUP BY mood_score
            ORDER BY mood_score
            """)
    List<MoodDistributionVO> selectMoodDistribution(@Param("userId") Long userId,
                                                    @Param("startDate") LocalDate startDate,
                                                    @Param("endDate") LocalDate endDate);

    @Select("""
            SELECT record_date AS date, mood_score AS mood_score
            FROM diary
            WHERE user_id = #{userId}
              AND record_date BETWEEN #{startDate} AND #{endDate}
            ORDER BY record_date
            """)
    List<HeatmapVO> selectHeatmap(@Param("userId") Long userId,
                                  @Param("startDate") LocalDate startDate,
                                  @Param("endDate") LocalDate endDate);

    @Select("""
            SELECT t.id AS tag_id, t.name, t.color, COUNT(dt.diary_id) AS `count`
            FROM tag t
            LEFT JOIN diary_tag dt ON dt.tag_id = t.id
            WHERE t.user_id = #{userId}
            GROUP BY t.id, t.name, t.color
            ORDER BY `count` DESC
            LIMIT #{limit}
            """)
    List<TagCloudVO> selectTagCloud(@Param("userId") Long userId,
                                    @Param("limit") int limit);

    @Select("SELECT IFNULL(SUM(word_count), 0) FROM diary WHERE user_id = #{userId}")
    Long selectTotalWords(@Param("userId") Long userId);

    /**
     * 全部记录日期（倒序），用于计算连续记录天数
     */
    @Select("SELECT record_date FROM diary WHERE user_id = #{userId} ORDER BY record_date DESC")
    List<LocalDate> selectRecordDates(@Param("userId") Long userId);
}
