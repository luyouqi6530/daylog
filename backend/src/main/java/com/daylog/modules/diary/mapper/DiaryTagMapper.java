package com.daylog.modules.diary.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.daylog.modules.diary.entity.DiaryTag;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/**
 * 日记-标签关联 Mapper
 */
@Mapper
public interface DiaryTagMapper extends BaseMapper<DiaryTag> {

    /**
     * 查询一批日记各自关联的标签ID（列表页批量装配 tags 用，避免 N+1 查询）
     */
    @Select("""
            <script>
            SELECT diary_id, tag_id FROM diary_tag
            WHERE diary_id IN
            <foreach collection="diaryIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<DiaryTag> selectByDiaryIds(@Param("diaryIds") List<Long> diaryIds);
}
