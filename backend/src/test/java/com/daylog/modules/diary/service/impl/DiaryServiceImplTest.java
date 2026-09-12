package com.daylog.modules.diary.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.daylog.common.exception.BusinessException;
import com.daylog.common.result.ResultCode;
import com.daylog.modules.diary.dto.DiarySaveDTO;
import com.daylog.modules.diary.entity.Diary;
import com.daylog.modules.diary.entity.DiaryTag;
import com.daylog.modules.diary.mapper.DiaryMapper;
import com.daylog.modules.diary.mapper.DiaryTagMapper;
import com.daylog.modules.file.service.AttachmentService;
import com.daylog.modules.stats.service.StatsCacheService;
import com.daylog.modules.tag.mapper.TagMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DiaryServiceImpl 单元测试
 *
 * <p>聚焦业务核心契约：</p>
 * <ul>
 *   <li>一天一篇：唯一索引兜底 DuplicateKeyException → 业务异常 DIARY_ALREADY_EXISTS</li>
 *   <li>字数统计：去 Markdown 标记 + 空白</li>
 *   <li>标签关联：先删旧关联后插新关联（全删全建）</li>
 *   <li>缓存一致性：增删改后 evictByUser</li>
 *   <li>权限隔离：他人日记按不存在处理</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DiaryServiceImpl 单元测试")
class DiaryServiceImplTest {

    @Mock
    private DiaryMapper diaryMapper;

    @Mock
    private DiaryTagMapper diaryTagMapper;

    @Mock
    private TagMapper tagMapper;

    @Mock
    private AttachmentService attachmentService;

    @Mock
    private StatsCacheService statsCacheService;

    @InjectMocks
    private DiaryServiceImpl diaryService;

    // ====================== 字数统计 ======================

    @Test
    @DisplayName("countWords：去除 Markdown 符号与空白后剩余字符数")
    void countWords_stripsMarkdown() {
        // 通过反射访问私有方法，验证"看不见但关键"的字数计算逻辑
        java.lang.reflect.Method method;
        try {
            method = DiaryServiceImpl.class.getDeclaredMethod("countWords", String.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        method.setAccessible(true);
        try {
            // "# Hello **world**!\n- 列表项" → 去掉 #*-`>[]()!- 和空白
            int n = (int) method.invoke(diaryService, "# Hello **world**!\n- 列表项");
            assertThat(n).isEqualTo("Helloworld列表项".length());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("countWords：空内容返回 0")
    void countWords_empty_returnsZero() throws Exception {
        java.lang.reflect.Method method = DiaryServiceImpl.class
                .getDeclaredMethod("countWords", String.class);
        method.setAccessible(true);
        // 显式包成 Object[] 让 invoke 知道是 1 个参数（避免 null 被当作"空参数数组"歧义）
        assertThat((int) method.invoke(diaryService, new Object[]{null})).isEqualTo(0);
        assertThat((int) method.invoke(diaryService, new Object[]{"   \n  "})).isEqualTo(0);
    }

    // ====================== create ======================

    @Test
    @DisplayName("create：唯一索引冲突（一天第二篇）抛 DIARY_ALREADY_EXISTS")
    void create_duplicateDate_throwsAlreadyExists() {
        DiarySaveDTO dto = buildDto(LocalDate.now());

        when(diaryMapper.insert(any(Diary.class)))
                .thenThrow(new DuplicateKeyException("uk_user_date"));

        assertThatThrownBy(() -> diaryService.create(dto, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ResultCode.DIARY_ALREADY_EXISTS.getCode()));

        verify(statsCacheService, never()).evictByUser(anyLong());
    }

    @Test
    @DisplayName("create：成功路径——写库 + 标签关联 + 附件回填 + 缓存失效")
    void create_success_evictsCache() {
        // tagIds 留空，让 saveRelations 提前返回，避开 Tag 实体的 lambda cache 依赖
        // （MP 的 lambda cache 需要 @TableName 扫描，纯单测无 MyBatis 上下文）
        DiarySaveDTO dto = buildDto(LocalDate.now());

        // insert 后回填 ID
        org.mockito.Mockito.doAnswer(inv -> {
            Diary d = inv.getArgument(0);
            d.setId(999L);
            return 1;
        }).when(diaryMapper).insert(any(Diary.class));

        diaryService.create(dto, 1L);

        // 验证调用链路：insert + attachment.linkToDiary + cache.evict（saveRelations 空集跳过）
        verify(diaryMapper).insert(any(Diary.class));
        verify(diaryTagMapper, never()).insert(any(DiaryTag.class));
        verify(attachmentService).linkToDiary(dto.getAttachmentIds(), 999L, 1L);
        verify(statsCacheService).evictByUser(1L);
    }

    // ====================== update ======================

    @Test
    @DisplayName("update：跨用户访问他人日记抛 NOT_FOUND，不修改数据")
    void update_otherUsersDiary_throwsNotFound() {
        Diary owned = new Diary();
        owned.setId(100L);
        owned.setUserId(2L); // 归属其他用户
        when(diaryMapper.selectById(100L)).thenReturn(owned);

        assertThatThrownBy(() -> diaryService.update(100L, buildDto(LocalDate.now()), 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ResultCode.NOT_FOUND.getCode()));

        verify(diaryMapper, never()).updateById(any(Diary.class));
    }

    @Test
    @DisplayName("update：成功路径——先删旧标签关联 + 重建 + 缓存失效")
    void update_success_clearsAndRebuildsRelations() {
        Long userId = 1L;
        Long diaryId = 100L;

        Diary owned = new Diary();
        owned.setId(diaryId);
        owned.setUserId(userId);
        when(diaryMapper.selectById(diaryId)).thenReturn(owned);

        // tagIds 留空避开 lambda cache
        DiarySaveDTO dto = buildDto(LocalDate.now());

        diaryService.update(diaryId, dto, userId);

        // 旧关联先 delete（按 diary_id）
        verify(diaryTagMapper).delete(any(LambdaQueryWrapper.class));
        verify(statsCacheService).evictByUser(userId);
    }

    // ====================== delete ======================

    @Test
    @DisplayName("delete：成功路径——日记 + 标签关联 + 附件 + 缓存失效")
    void delete_success_cascadesAndEvicts() {
        Long userId = 1L;
        Long diaryId = 100L;

        Diary owned = new Diary();
        owned.setId(diaryId);
        owned.setUserId(userId);
        when(diaryMapper.selectById(diaryId)).thenReturn(owned);

        diaryService.delete(diaryId, userId);

        verify(diaryMapper).deleteById(diaryId);
        verify(diaryTagMapper).delete(any(LambdaQueryWrapper.class));
        verify(attachmentService).deleteByDiaryId(diaryId);
        verify(statsCacheService).evictByUser(userId);
    }

    @Test
    @DisplayName("delete：日记不存在抛 NOT_FOUND")
    void delete_notFound_throws() {
        when(diaryMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> diaryService.delete(999L, 1L))
                .isInstanceOf(BusinessException.class);

        verify(diaryMapper, never()).deleteById(anyLong());
    }

    // ====================== page ======================

    @Test
    @DisplayName("page：标签筛选时若该标签无关联日记，直接返回空分页")
    void page_byTagWithNoRelations_returnsEmpty() {
        when(diaryTagMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        Page<?> result = diaryService.page(
                new com.daylog.modules.diary.dto.DiaryQueryDTO() {{
                    setPage(1);
                    setSize(10);
                    setTagId(99L);
                }}, 1L);

        assertThat(result.getRecords()).isEmpty();
        assertThat(result.getTotal()).isEqualTo(0);
        verify(diaryMapper, never()).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    // ====================== helper ======================

    private DiarySaveDTO buildDto(LocalDate date) {
        DiarySaveDTO dto = new DiarySaveDTO();
        dto.setTitle("今天写了 100 字日记");
        dto.setContent("今天学到了 # Spring Boot # 单元测试。");
        dto.setMoodScore(5);
        dto.setWeather("晴");
        dto.setRecordDate(date);
        return dto;
    }
}