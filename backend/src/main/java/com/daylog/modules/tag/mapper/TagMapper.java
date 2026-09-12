package com.daylog.modules.tag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.daylog.modules.tag.entity.Tag;
import com.daylog.modules.tag.vo.TagVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/**
 * 标签 Mapper
 */
@Mapper
public interface TagMapper extends BaseMapper<Tag> {

    /**
     * 标签列表 + 各自的日记数（LEFT JOIN 保证 0 使用的标签也返回）
     */
    @Select("""
            SELECT t.id, t.name, t.color, COUNT(dt.diary_id) AS `count`
            FROM tag t
            LEFT JOIN diary_tag dt ON dt.tag_id = t.id
            WHERE t.user_id = #{userId}
            GROUP BY t.id, t.name, t.color
            ORDER BY `count` DESC, t.created_at DESC
            """)
    List<TagVO> selectTagsWithCount(@Param("userId") Long userId);
}
