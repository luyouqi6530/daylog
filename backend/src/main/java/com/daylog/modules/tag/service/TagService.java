package com.daylog.modules.tag.service;

import com.daylog.modules.tag.dto.TagSaveDTO;
import com.daylog.modules.tag.vo.TagVO;

import java.util.List;

/**
 * 标签服务
 */
public interface TagService {

    /** 标签列表（含各自日记数） */
    List<TagVO> list(Long userId);

    Long create(TagSaveDTO dto, Long userId);

    void update(Long id, TagSaveDTO dto, Long userId);

    void delete(Long id, Long userId);
}
