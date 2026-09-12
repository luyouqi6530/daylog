package com.daylog.modules.report.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.daylog.modules.auth.entity.User;
import com.daylog.modules.auth.mapper.UserMapper;
import com.daylog.modules.diary.entity.Diary;
import com.daylog.modules.diary.entity.DiaryTag;
import com.daylog.modules.diary.mapper.DiaryMapper;
import com.daylog.modules.diary.mapper.DiaryTagMapper;
import com.daylog.modules.stats.mapper.StatsMapper;
import com.daylog.modules.stats.vo.MoodTrendVO;
import com.daylog.modules.tag.entity.Tag;
import com.daylog.modules.tag.mapper.TagMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 周报 Agent 工具集
 *
 * <p>每个方法作为 LLM 可调用的一个 tool，参数尽量保持简单（基本类型 + 日期字符串），
 * 返回值是 JsonNode/Map 友好的简单结构，方便序列化成 JSON 字符串塞回对话上下文。</p>
 *
 * <p>工具名（与 {@link com.daylog.modules.report.agent.model.ToolDefinition} 一致）：
 * <ul>
 *   <li>{@link #getWeekDiaries(Long, LocalDate)}  - 取一周日记摘要</li>
 *   <li>{@link #getWeekTagUsage(Long, LocalDate)}  - 取一周标签使用计数</li>
 *   <li>{@link #getUserProfile(Long)}  - 取用户画像（昵称、记录天数、最近活跃）</li>
 *   <li>{@link #getMoodTrendChart(Long, LocalDate)}  - 取心情趋势数据</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentTools {

    /** 单篇日记内容最大保留字数（防止 tool 返回内容过长撑爆上下文窗口） */
    private static final int MAX_CONTENT_PER_DIARY = 400;

    /** 工具调用抛出的特殊异常标识：本周日记不足以生成报告（Agent 收到后停止外层循环） */
    public static final String EXCEPTION_INSUFFICIENT = "INSUFFICIENT_DIARY";

    private final DiaryMapper diaryMapper;
    private final DiaryTagMapper diaryTagMapper;
    private final TagMapper tagMapper;
    private final UserMapper userMapper;
    private final StatsMapper statsMapper;

    /**
     * 工具 1：拉取一周日记摘要（含日期、心情分、标题、截断内容、标签）
     *
     * @param userId    用户 ID
     * @param weekStart 周一日期
     * @return List，每条为一个日记摘要字典；空列表表示本周无日记
     * @throws IllegalStateException 当本周日记少于 3 天时抛 INSUFFICIENT_DIARY
     */
    public List<Map<String, Object>> getWeekDiaries(Long userId, LocalDate weekStart) {
        if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
            // 强制周一，方便 Agent 误传时直接报错
            throw new IllegalArgumentException("weekStart 必须是周一，当前：" + weekStart + " 是 " + weekStart.getDayOfWeek());
        }
        LocalDate weekEnd = weekStart.plusDays(6);

        List<Diary> diaries = diaryMapper.selectList(new LambdaQueryWrapper<Diary>()
                .eq(Diary::getUserId, userId)
                .ge(Diary::getRecordDate, weekStart)
                .le(Diary::getRecordDate, weekEnd)
                .orderByAsc(Diary::getRecordDate));

        if (diaries.size() < 3) {
            // 不足 3 天视为样本不足：抛错让 Agent 服务捕获并以友好提示结束
            throw new IllegalStateException(EXCEPTION_INSUFFICIENT
                    + "（本周仅 " + diaries.size() + " 篇日记，少于 3 篇阈值）");
        }

        // 批量取标签（避免 N+1）
        List<Long> diaryIds = diaries.stream().map(Diary::getId).toList();
        Map<Long, List<String>> idToTags = new LinkedHashMap<>();
        if (!diaryIds.isEmpty()) {
            for (DiaryTag dt : diaryTagMapper.selectByDiaryIds(diaryIds)) {
                idToTags.computeIfAbsent(dt.getDiaryId(), k -> new ArrayList<>()).add(String.valueOf(dt.getTagId()));
            }
            // 加载标签名缓存
            Map<Long, String> tagIdToName = new LinkedHashMap<>();
            for (Tag tag : tagMapper.selectList(new LambdaQueryWrapper<Tag>().eq(Tag::getUserId, userId))) {
                tagIdToName.put(tag.getId(), tag.getName());
            }
            // 把 tagId 换成 tagName
            for (Map.Entry<Long, List<String>> e : idToTags.entrySet()) {
                List<String> names = new ArrayList<>(e.getValue().size());
                for (String tid : e.getValue()) {
                    try {
                        names.add(tagIdToName.getOrDefault(Long.parseLong(tid), tid));
                    } catch (NumberFormatException ignore) {
                        names.add(tid);
                    }
                }
                e.setValue(names);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>(diaries.size());
        for (Diary d : diaries) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", d.getRecordDate().toString());
            item.put("mood", d.getMoodScore());
            item.put("title", d.getTitle());
            item.put("content", truncate(d.getContent(), MAX_CONTENT_PER_DIARY));
            item.put("tags", idToTags.getOrDefault(d.getId(), List.of()));
            result.add(item);
        }
        return result;
    }

    /**
     * 工具 2：本周标签使用情况（按频次倒序）
     */
    public List<Map<String, Object>> getWeekTagUsage(Long userId, LocalDate weekStart) {
        if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("weekStart 必须是周一");
        }
        LocalDate weekEnd = weekStart.plusDays(6);

        List<Tag> userTags = tagMapper.selectList(new LambdaQueryWrapper<Tag>().eq(Tag::getUserId, userId));
        if (userTags.isEmpty()) return List.of();

        Map<Long, String> tagIdToName = new LinkedHashMap<>();
        for (Tag t : userTags) tagIdToName.put(t.getId(), t.getName());

        List<Diary> weekDiaries = diaryMapper.selectList(new LambdaQueryWrapper<Diary>()
                .eq(Diary::getUserId, userId)
                .ge(Diary::getRecordDate, weekStart)
                .le(Diary::getRecordDate, weekEnd));
        List<Long> diaryIds = weekDiaries.stream().map(Diary::getId).toList();
        if (diaryIds.isEmpty()) return List.of();

        Map<String, Long> nameToCount = new LinkedHashMap<>();
        for (DiaryTag dt : diaryTagMapper.selectByDiaryIds(diaryIds)) {
            String name = tagIdToName.get(dt.getTagId());
            if (name != null) {
                nameToCount.merge(name, 1L, Long::sum);
            }
        }
        return nameToCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("tag", e.getKey());
                    item.put("count", e.getValue());
                    return item;
                })
                .toList();
    }

    /**
     * 工具 3：用户画像（昵称、连续记录天数、累计日记数、累计平均心情）
     */
    public Map<String, Object> getUserProfile(Long userId) {
        Map<String, Object> profile = new LinkedHashMap<>();
        User user = userMapper.selectById(userId);
        profile.put("nickname", user == null ? "" : user.getNickname());

        // 连续记录天数：从今天往前数，连续有日记到哪天断
        List<LocalDate> datesDesc = statsMapper.selectRecordDates(userId);
        int consecutive = 0;
        if (datesDesc != null && !datesDesc.isEmpty()) {
            LocalDate expected = LocalDate.now();
            for (LocalDate d : datesDesc) {
                if (d.equals(expected)) {
                    consecutive++;
                    expected = expected.minusDays(1);
                } else {
                    break;
                }
            }
        }
        profile.put("consecutiveDays", consecutive);
        profile.put("totalDiaries", datesDesc == null ? 0 : datesDesc.size());
        profile.put("totalWords", statsMapper.selectTotalWords(userId) == null ? 0 : statsMapper.selectTotalWords(userId));
        return profile;
    }

    /**
     * 工具 4：心情日趋势数据（周一到周日，缺日记日期补 null）
     */
    public List<Map<String, Object>> getMoodTrendChart(Long userId, LocalDate weekStart) {
        if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("weekStart 必须是周一");
        }
        LocalDate weekEnd = weekStart.plusDays(6);

        List<MoodTrendVO> raw = statsMapper.selectMoodTrend(userId, weekStart, weekEnd);
        Map<LocalDate, BigDecimal> dateToMood = new LinkedHashMap<>();
        for (MoodTrendVO vo : raw) dateToMood.put(vo.getDate(), vo.getAvgMood());

        List<Map<String, Object>> result = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            LocalDate d = weekStart.plusDays(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", d.toString());
            item.put("mood", dateToMood.get(d));
            result.add(item);
        }
        return result;
    }

    private String truncate(String content, int max) {
        if (content == null) return "";
        return content.length() > max ? content.substring(0, max) + "……" : content;
    }
}
