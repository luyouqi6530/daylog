package com.daylog.modules.tag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.daylog.common.exception.BusinessException;
import com.daylog.common.result.ResultCode;
import com.daylog.modules.diary.mapper.DiaryTagMapper;
import com.daylog.modules.tag.dto.TagSaveDTO;
import com.daylog.modules.tag.entity.Tag;
import com.daylog.modules.tag.mapper.TagMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TagServiceImpl 单元测试
 *
 * <p>核心契约：</p>
 * <ul>
 *   <li>同用户下标签名唯一：服务层预判 + DB 唯一索引兜底</li>
 *   <li>删除标签必须级联清理 diary_tag 关联（事务保证）</li>
 *   <li>他人标签按不存在处理（不暴露资源存在性）</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TagServiceImpl 单元测试")
class TagServiceImplTest {

    @Mock
    private TagMapper tagMapper;

    @Mock
    private DiaryTagMapper diaryTagMapper;

    @InjectMocks
    private TagServiceImpl tagService;

    // ====================== create ======================

    @Test
    @DisplayName("create：同名已存在时抛 TAG_NAME_EXISTS")
    void create_duplicateName_throwsTagNameExists() {
        TagSaveDTO dto = new TagSaveDTO();
        dto.setName("学习");
        dto.setColor("#1890ff");

        when(tagMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> tagService.create(dto, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ResultCode.TAG_NAME_EXISTS.getCode()));

        verify(tagMapper, never()).insert(any(Tag.class));
    }

    @Test
    @DisplayName("create：DB 唯一索引兜底——并发 DuplicateKey 时抛 TAG_NAME_EXISTS")
    void create_concurrentDuplicateKey_throwsTagNameExists() {
        TagSaveDTO dto = new TagSaveDTO();
        dto.setName("学习");
        dto.setColor("#1890ff");

        when(tagMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(tagMapper.insert(any(Tag.class)))
                .thenThrow(new DuplicateKeyException("uk_user_name"));

        assertThatThrownBy(() -> tagService.create(dto, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ResultCode.TAG_NAME_EXISTS.getCode()));
    }

    // ====================== delete ======================

    @Test
    @DisplayName("delete：他人标签抛 NOT_FOUND，不删除数据")
    void delete_otherUsersTag_throwsNotFound() {
        Tag foreign = new Tag();
        foreign.setId(99L);
        foreign.setUserId(2L);
        when(tagMapper.selectById(99L)).thenReturn(foreign);

        assertThatThrownBy(() -> tagService.delete(99L, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ResultCode.NOT_FOUND.getCode()));

        verify(tagMapper, never()).deleteById(any());
        verify(diaryTagMapper, never()).delete(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("delete：自己的标签——先删 tag 行 + 再删关联（事务保证）")
    void delete_ownTag_cascadesToDiaryTag() {
        Tag owned = new Tag();
        owned.setId(10L);
        owned.setUserId(1L);
        when(tagMapper.selectById(10L)).thenReturn(owned);

        tagService.delete(10L, 1L);

        verify(tagMapper).deleteById(10L);
        verify(diaryTagMapper).delete(any(LambdaQueryWrapper.class));
    }

    // ====================== update ======================

    @Test
    @DisplayName("update：标签名相同（仅改颜色）时跳过唯一性校验")
    void update_sameName_skipsDuplicateCheck() {
        Tag owned = new Tag();
        owned.setId(10L);
        owned.setUserId(1L);
        owned.setName("学习");
        owned.setColor("#1890ff");
        when(tagMapper.selectById(10L)).thenReturn(owned);

        TagSaveDTO dto = new TagSaveDTO();
        dto.setName("学习"); // 同名
        dto.setColor("#52c41a"); // 仅改颜色

        tagService.update(10L, dto, 1L);

        // name 没变，selectCount 都不调
        verify(tagMapper, never()).selectCount(any(LambdaQueryWrapper.class));
        verify(tagMapper).updateById(any(Tag.class));
    }

    @Test
    @DisplayName("update：改名时若与现有标签重名，抛 TAG_NAME_EXISTS")
    void update_renameToExisting_throws() {
        Tag owned = new Tag();
        owned.setId(10L);
        owned.setUserId(1L);
        owned.setName("旧名");
        when(tagMapper.selectById(10L)).thenReturn(owned);
        when(tagMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        TagSaveDTO dto = new TagSaveDTO();
        dto.setName("新名冲突");
        dto.setColor("#000");

        assertThatThrownBy(() -> tagService.update(10L, dto, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ResultCode.TAG_NAME_EXISTS.getCode()));
    }
}