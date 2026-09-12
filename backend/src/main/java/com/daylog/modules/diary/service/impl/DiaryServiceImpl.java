package com.daylog.modules.diary.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.daylog.common.exception.BusinessException;
import com.daylog.common.result.ResultCode;
import com.daylog.modules.diary.dto.DiaryQueryDTO;
import com.daylog.modules.diary.dto.DiarySaveDTO;
import com.daylog.modules.diary.entity.Diary;
import com.daylog.modules.diary.entity.DiaryTag;
import com.daylog.modules.diary.mapper.DiaryMapper;
import com.daylog.modules.diary.mapper.DiaryTagMapper;
import com.daylog.modules.diary.service.DiaryService;
import com.daylog.modules.diary.vo.CalendarDayVO;
import com.daylog.modules.diary.vo.DiaryDetailVO;
import com.daylog.modules.diary.vo.DiaryVO;
import com.daylog.modules.file.service.AttachmentService;
import com.daylog.modules.file.vo.AttachmentVO;
import com.daylog.modules.stats.service.StatsCacheService;
import com.daylog.modules.tag.entity.Tag;
import com.daylog.modules.tag.mapper.TagMapper;
import com.daylog.modules.tag.vo.TagVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 日记服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiaryServiceImpl implements DiaryService {

    private final DiaryMapper diaryMapper;
    private final DiaryTagMapper diaryTagMapper;
    private final TagMapper tagMapper;
    private final AttachmentService attachmentService;
    private final StatsCacheService statsCacheService;

    @Override
    public Page<DiaryVO> page(DiaryQueryDTO query, Long userId) {
        int page = query.getPage() == null || query.getPage() < 1 ? 1 : query.getPage();
        int size = query.getSize() == null ? 10 : Math.min(query.getSize(), 50);

        LambdaQueryWrapper<Diary> wrapper = new LambdaQueryWrapper<Diary>()
                .eq(Diary::getUserId, userId)
                .ge(query.getStartDate() != null, Diary::getRecordDate, query.getStartDate())
                .le(query.getEndDate() != null, Diary::getRecordDate, query.getEndDate())
                .eq(query.getMood() != null, Diary::getMoodScore, query.getMood())
                // 关键词同时匹配标题与正文（LIKE '%kw%' 无法走索引，个人项目量级可接受）
                .and(StringUtils.hasText(query.getKeyword()), w -> w
                        .like(Diary::getTitle, query.getKeyword())
                        .or()
                        .like(Diary::getContent, query.getKeyword()))
                .orderByDesc(Diary::getRecordDate);

        // 标签筛选：先查该标签下的日记ID，再 IN 过滤
        if (query.getTagId() != null) {
            List<Long> diaryIds = diaryTagMapper.selectList(
                            new LambdaQueryWrapper<DiaryTag>().eq(DiaryTag::getTagId, query.getTagId()))
                    .stream().map(DiaryTag::getDiaryId).toList();
            if (diaryIds.isEmpty()) {
                return emptyPage(page, size);
            }
            wrapper.in(Diary::getId, diaryIds);
        }

        Page<Diary> diaryPage = diaryMapper.selectPage(Page.of(page, size), wrapper);
        return assembleVOPage(diaryPage);
    }

    @Override
    public DiaryDetailVO getById(Long id, Long userId) {
        Diary diary = getOwnedDiary(id, userId);
        return assembleDetailVO(diary);
    }

    @Override
    public DiaryDetailVO getByDate(String recordDate, Long userId) {
        LocalDate date = LocalDate.parse(recordDate);
        Diary diary = diaryMapper.selectOne(new LambdaQueryWrapper<Diary>()
                .eq(Diary::getUserId, userId)
                .eq(Diary::getRecordDate, date));
        // 无记录返回 null（非 404），前端据此显示"新建"还是"编辑"
        return diary == null ? null : assembleDetailVO(diary);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(DiarySaveDTO dto, Long userId) {
        Diary diary = new Diary();
        copyProperties(dto, diary);
        diary.setUserId(userId);
        diary.setWordCount(countWords(dto.getContent()));

        try {
            diaryMapper.insert(diary);
        } catch (DuplicateKeyException e) {
            // 唯一索引 uk_user_date 兜底：并发提交同一天两篇日记
            throw new BusinessException(ResultCode.DIARY_ALREADY_EXISTS);
        }

        saveRelations(diary.getId(), dto, userId);
        // 附件回填：先上传后关联的闭环
        attachmentService.linkToDiary(dto.getAttachmentIds(), diary.getId(), userId);
        // 统计缓存主动失效
        statsCacheService.evictByUser(userId);
        log.info("日记创建: diaryId={}, userId={}, date={}", diary.getId(), userId, diary.getRecordDate());
        return diary.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, DiarySaveDTO dto, Long userId) {
        Diary diary = getOwnedDiary(id, userId);
        copyProperties(dto, diary);
        diary.setWordCount(countWords(dto.getContent()));

        try {
            diaryMapper.updateById(diary);
        } catch (DuplicateKeyException e) {
            // 改记录日期撞上另一天已有日记
            throw new BusinessException(ResultCode.DIARY_ALREADY_EXISTS);
        }

        // 标签关联全删全建（简单可靠，无需 diff）
        diaryTagMapper.delete(new LambdaQueryWrapper<DiaryTag>().eq(DiaryTag::getDiaryId, id));
        saveRelations(id, dto, userId);
        // 新附件列表回填，不在列表中的旧附件解除关联
        attachmentService.linkToDiary(dto.getAttachmentIds(), id, userId);
        attachmentService.unlinkNotIn(id, dto.getAttachmentIds());
        statsCacheService.evictByUser(userId);
        log.info("日记更新: diaryId={}, userId={}", id, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long userId) {
        getOwnedDiary(id, userId);
        diaryMapper.deleteById(id);
        diaryTagMapper.delete(new LambdaQueryWrapper<DiaryTag>().eq(DiaryTag::getDiaryId, id));
        attachmentService.deleteByDiaryId(id);
        statsCacheService.evictByUser(userId);
        log.info("日记删除: diaryId={}, userId={}", id, userId);
    }

    @Override
    public List<CalendarDayVO> calendar(int year, int month, Long userId) {
        YearMonth yearMonth = YearMonth.of(year, month);
        List<Diary> diaries = diaryMapper.selectList(new LambdaQueryWrapper<Diary>()
                .select(Diary::getRecordDate, Diary::getMoodScore)
                .eq(Diary::getUserId, userId)
                .ge(Diary::getRecordDate, yearMonth.atDay(1))
                .le(Diary::getRecordDate, yearMonth.atEndOfMonth())
                .orderByAsc(Diary::getRecordDate));
        return diaries.stream().map(d -> {
            CalendarDayVO vo = new CalendarDayVO();
            vo.setRecordDate(d.getRecordDate());
            vo.setMoodScore(d.getMoodScore());
            return vo;
        }).collect(Collectors.toList());
    }

    // ==================== 私有装配方法 ====================

    /**
     * 实体转列表 VO（tags / imageCount 由调用方批量填充）
     */
    private DiaryVO toVO(Diary diary) {
        DiaryVO vo = new DiaryVO();
        vo.setId(diary.getId());
        vo.setTitle(diary.getTitle());
        vo.setContent(diary.getContent());
        vo.setMoodScore(diary.getMoodScore());
        vo.setWeather(diary.getWeather());
        vo.setRecordDate(diary.getRecordDate());
        vo.setWordCount(diary.getWordCount());
        vo.setCreatedAt(diary.getCreatedAt());
        return vo;
    }

    /**
     * 分页结果转 VO + 批量装配标签与图片数（避免循环内查库的 N+1 问题）
     */
    private Page<DiaryVO> assembleVOPage(Page<Diary> diaryPage) {
        Page<DiaryVO> voPage = new Page<>(diaryPage.getCurrent(), diaryPage.getSize(), diaryPage.getTotal());
        List<Diary> records = diaryPage.getRecords();
        if (records.isEmpty()) {
            voPage.setRecords(new ArrayList<>());
            return voPage;
        }

        List<Long> diaryIds = records.stream().map(Diary::getId).toList();
        Map<Long, List<TagVO>> tagMap = loadTagsBatch(diaryIds);
        Map<Long, Long> imageCountMap = attachmentService.countByDiaryIds(diaryIds);

        voPage.setRecords(records.stream().map(diary -> {
            DiaryVO vo = toVO(diary);
            vo.setTags(tagMap.getOrDefault(diary.getId(), new ArrayList<>()));
            vo.setImageCount(imageCountMap.getOrDefault(diary.getId(), 0L).intValue());
            return vo;
        }).collect(Collectors.toList()));
        return voPage;
    }

    private DiaryDetailVO assembleDetailVO(Diary diary) {
        DiaryDetailVO vo = new DiaryDetailVO();
        vo.setId(diary.getId());
        vo.setTitle(diary.getTitle());
        vo.setContent(diary.getContent());
        vo.setMoodScore(diary.getMoodScore());
        vo.setWeather(diary.getWeather());
        vo.setRecordDate(diary.getRecordDate());
        vo.setWordCount(diary.getWordCount());
        vo.setCreatedAt(diary.getCreatedAt());
        vo.setTags(loadTagsBatch(List.of(diary.getId()))
                .getOrDefault(diary.getId(), new ArrayList<>()));

        List<AttachmentVO> images = attachmentService.getByDiaryId(diary.getId());
        vo.setImages(images);
        vo.setImageCount(images.size());
        return vo;
    }

    /**
     * 批量查标签关联：一次 IN 查 diary_tag，一次 IN 查 tag，内存分组
     */
    private Map<Long, List<TagVO>> loadTagsBatch(List<Long> diaryIds) {
        List<DiaryTag> relations = diaryTagMapper.selectByDiaryIds(diaryIds);
        if (relations.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Tag> tagMap = tagMapper.selectBatchIds(
                        relations.stream().map(DiaryTag::getTagId).distinct().toList())
                .stream().collect(Collectors.toMap(Tag::getId, Function.identity()));

        return relations.stream()
                .filter(r -> tagMap.containsKey(r.getTagId()))
                .collect(Collectors.groupingBy(DiaryTag::getDiaryId,
                        Collectors.mapping(r -> {
                            Tag tag = tagMap.get(r.getTagId());
                            TagVO vo = new TagVO();
                            vo.setId(tag.getId());
                            vo.setName(tag.getName());
                            vo.setColor(tag.getColor());
                            return vo;
                        }, Collectors.toList())));
    }

    /**
     * 保存日记-标签关联（校验标签归属当前用户，防止引用他人标签）
     */
    private void saveRelations(Long diaryId, DiarySaveDTO dto, Long userId) {
        if (CollectionUtils.isEmpty(dto.getTagIds())) {
            return;
        }
        List<Long> ownedTagIds = tagMapper.selectList(new LambdaQueryWrapper<Tag>()
                        .select(Tag::getId)
                        .eq(Tag::getUserId, userId)
                        .in(Tag::getId, dto.getTagIds()))
                .stream().map(Tag::getId).toList();
        for (Long tagId : ownedTagIds) {
            DiaryTag relation = new DiaryTag();
            relation.setDiaryId(diaryId);
            relation.setTagId(tagId);
            diaryTagMapper.insert(relation);
        }
    }

    private void copyProperties(DiarySaveDTO dto, Diary diary) {
        diary.setTitle(dto.getTitle());
        diary.setContent(dto.getContent());
        diary.setMoodScore(dto.getMoodScore());
        diary.setWeather(dto.getWeather());
        diary.setRecordDate(dto.getRecordDate());
    }

    /**
     * 字数统计：去除 Markdown 语法符号和空白后的纯文字长度
     */
    private int countWords(String content) {
        if (!StringUtils.hasText(content)) {
            return 0;
        }
        return content.replaceAll("[#*`>\\[\\]()!\\-\\s]", "").length();
    }

    /**
     * 查询日记并校验归属（他人资源按不存在处理）
     */
    private Diary getOwnedDiary(Long id, Long userId) {
        Diary diary = diaryMapper.selectById(id);
        if (diary == null || !diary.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "日记不存在");
        }
        return diary;
    }

    private Page<DiaryVO> emptyPage(int page, int size) {
        Page<DiaryVO> voPage = Page.of(page, size, 0);
        voPage.setRecords(new ArrayList<>());
        return voPage;
    }
}
