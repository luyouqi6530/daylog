package com.daylog.modules.tag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.daylog.common.exception.BusinessException;
import com.daylog.common.result.ResultCode;
import com.daylog.modules.diary.entity.DiaryTag;
import com.daylog.modules.diary.mapper.DiaryTagMapper;
import com.daylog.modules.tag.dto.TagSaveDTO;
import com.daylog.modules.tag.entity.Tag;
import com.daylog.modules.tag.mapper.TagMapper;
import com.daylog.modules.tag.service.TagService;
import com.daylog.modules.tag.vo.TagVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 标签服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {

    private final TagMapper tagMapper;
    private final DiaryTagMapper diaryTagMapper;

    @Override
    public List<TagVO> list(Long userId) {
        return tagMapper.selectTagsWithCount(userId);
    }

    @Override
    public Long create(TagSaveDTO dto, Long userId) {
        Long count = tagMapper.selectCount(new LambdaQueryWrapper<Tag>()
                .eq(Tag::getUserId, userId)
                .eq(Tag::getName, dto.getName()));
        if (count > 0) {
            throw new BusinessException(ResultCode.TAG_NAME_EXISTS);
        }

        Tag tag = new Tag();
        tag.setUserId(userId);
        tag.setName(dto.getName());
        tag.setColor(dto.getColor());
        try {
            tagMapper.insert(tag);
        } catch (DuplicateKeyException e) {
            // 并发兜底：唯一索引 uk_user_name
            throw new BusinessException(ResultCode.TAG_NAME_EXISTS);
        }
        return tag.getId();
    }

    @Override
    public void update(Long id, TagSaveDTO dto, Long userId) {
        Tag tag = getOwnedTag(id, userId);

        // 改名时校验同名
        if (!tag.getName().equals(dto.getName())) {
            Long count = tagMapper.selectCount(new LambdaQueryWrapper<Tag>()
                    .eq(Tag::getUserId, userId)
                    .eq(Tag::getName, dto.getName())
                    .ne(Tag::getId, id));
            if (count > 0) {
                throw new BusinessException(ResultCode.TAG_NAME_EXISTS);
            }
        }

        tag.setName(dto.getName());
        tag.setColor(dto.getColor());
        try {
            tagMapper.updateById(tag);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.TAG_NAME_EXISTS);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long userId) {
        getOwnedTag(id, userId);
        // 物理删除标签 + 解除所有日记关联（事务保证两者同成败）
        tagMapper.deleteById(id);
        diaryTagMapper.delete(new LambdaQueryWrapper<DiaryTag>().eq(DiaryTag::getTagId, id));
        log.info("标签删除: tagId={}, userId={}", id, userId);
    }

    /**
     * 查询标签并校验归属，不属于当前用户按不存在处理（不暴露他人资源存在性）
     */
    private Tag getOwnedTag(Long id, Long userId) {
        Tag tag = tagMapper.selectById(id);
        if (tag == null || !tag.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "标签不存在");
        }
        return tag;
    }
}
