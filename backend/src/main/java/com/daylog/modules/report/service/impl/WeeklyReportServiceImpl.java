package com.daylog.modules.report.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.daylog.common.exception.BusinessException;
import com.daylog.common.result.ResultCode;
import com.daylog.config.DeepSeekProperties;
import com.daylog.modules.diary.entity.Diary;
import com.daylog.modules.diary.mapper.DiaryMapper;
import com.daylog.modules.report.dto.GenerateReportDTO;
import com.daylog.modules.report.entity.WeeklyReport;
import com.daylog.modules.report.mapper.WeeklyReportMapper;
import com.daylog.modules.report.service.WeeklyReportAgentService;
import com.daylog.modules.report.service.WeeklyReportService;
import com.daylog.modules.report.vo.WeeklyReportVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AI 周报服务实现
 *
 * <p>generate() 是入口，内部按配置自动选择：</p>
 * <ul>
 *   <li>配置了有效的 DeepSeek API key → 走 ReAct Agent 多步推理（{@link WeeklyReportAgentService}）</li>
 *   <li>未配置 / key 为空 → 走本地模板兜底（Mock）保证功能可演示</li>
 * </ul>
 *
 * <p>页面 / 调度器层无需感知分支，这是接口层入口路由策略的常见模式。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyReportServiceImpl implements WeeklyReportService {

    private final WeeklyReportMapper weeklyReportMapper;
    private final DiaryMapper diaryMapper;
    private final DeepSeekProperties deepSeekProperties;
    private final WeeklyReportAgentService weeklyReportAgentService;

    @Override
    public Long generate(Long userId, GenerateReportDTO dto) {
        LocalDate weekStart = dto.getWeekStartDate();
        if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "周起始日期必须为周一");
        }
        LocalDate weekEnd = weekStart.plusDays(6);

        List<Diary> diaries = diaryMapper.selectList(new LambdaQueryWrapper<Diary>()
                .eq(Diary::getUserId, userId)
                .ge(Diary::getRecordDate, weekStart)
                .le(Diary::getRecordDate, weekEnd)
                .orderByAsc(Diary::getRecordDate));
        if (diaries.isEmpty()) {
            throw new BusinessException(ResultCode.AI_NO_DIARY_THIS_WEEK);
        }

        BigDecimal moodAvg = diaries.stream()
                .map(Diary::getMoodScore)
                .map(BigDecimal::valueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(diaries.size()), 1, RoundingMode.HALF_UP);

        String summary;
        String modelTag;
        if (isAgentEnabled()) {
            // 真实 Agent 模式：仅有"日志不足"的业务错才原样抛（用户应知道），其他失败一律降级
            try {
                summary = weeklyReportAgentService.generateReport(userId, weekStart);
                modelTag = "deepseek-agent";
                log.info("[周报 Agent] 用户={} 周起={} 生成成功", userId, weekStart);
            } catch (BusinessException be) {
                if (be.getCode() == ResultCode.AI_NO_DIARY_THIS_WEEK.getCode()) {
                    throw be; // 业务错"日志不足"必须告知前端，不降级
                }
                log.error("[周报 Agent] 调用失败，降级到 Mock: {}", be.getMessage());
                summary = mockSummary(diaries, weekStart, moodAvg);
                modelTag = "mock-fallback";
            } catch (Exception e) {
                log.error("[周报 Agent] 调用失败，降级到 Mock: {}", e.getMessage());
                summary = mockSummary(diaries, weekStart, moodAvg);
                modelTag = "mock-fallback";
            }
        } else {
            // 未配 key：Mock 兜底
            summary = mockSummary(diaries, weekStart, moodAvg);
            modelTag = "mock-no-key";
        }

        // upsert
        WeeklyReport existing = weeklyReportMapper.selectOne(new LambdaQueryWrapper<WeeklyReport>()
                .eq(WeeklyReport::getUserId, userId)
                .eq(WeeklyReport::getWeekStart, weekStart));

        WeeklyReport report = existing != null ? existing : new WeeklyReport();
        report.setUserId(userId);
        report.setWeekStart(weekStart);
        report.setWeekEnd(weekEnd);
        report.setDiaryCount(diaries.size());
        report.setMoodAvg(moodAvg);
        report.setSummary(summary);
        report.setModel(modelTag);

        if (existing != null) {
            weeklyReportMapper.updateById(report);
        } else {
            weeklyReportMapper.insert(report);
        }
        log.info("周报生成: userId={}, weekStart={}, diaryCount={}, model={}",
                userId, weekStart, diaries.size(), modelTag);
        return report.getId();
    }

    @Override
    public Page<WeeklyReportVO> page(Long userId, int page, int size) {
        int pageNum = Math.max(page, 1);
        int pageSize = Math.min(Math.max(size, 1), 50);
        Page<WeeklyReport> reportPage = weeklyReportMapper.selectPage(
                Page.of(pageNum, pageSize),
                new LambdaQueryWrapper<WeeklyReport>()
                        .eq(WeeklyReport::getUserId, userId)
                        .orderByDesc(WeeklyReport::getWeekStart));

        Page<WeeklyReportVO> voPage = new Page<>(reportPage.getCurrent(), reportPage.getSize(), reportPage.getTotal());
        voPage.setRecords(reportPage.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public WeeklyReportVO getById(Long id, Long userId) {
        WeeklyReport report = weeklyReportMapper.selectById(id);
        if (report == null || !report.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "周报不存在");
        }
        return toVO(report);
    }

    /* -------------------- 私有 -------------------- */

    /**
     * Agent 启用判定：配置了非空 apiKey 即认为启用
     */
    private boolean isAgentEnabled() {
        return deepSeekProperties != null && StringUtils.hasText(deepSeekProperties.getApiKey());
    }

    /**
     * Mock 模式下的本地模板周报（不调外部 LLM，保证 demo 可用）
     */
    private String mockSummary(List<Diary> diaries, LocalDate weekStart, BigDecimal moodAvg) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 本周概览\n\n");
        sb.append("本周（").append(weekStart).append(" ~ ").append(weekStart.plusDays(6))
                .append("）共记录了 ").append(diaries.size()).append(" 篇日记，平均心情 ")
                .append(moodAvg).append(" 分。\n\n");

        sb.append("## 情绪观察\n\n");
        sb.append("本周心情在 ");
        diaries.stream().min((a, b) -> Integer.compare(a.getMoodScore(), b.getMoodScore()))
                .ifPresent(d -> sb.append(d.getRecordDate()).append("（").append(d.getMoodScore()).append(" 分）出现低点，"));
        diaries.stream().max((a, b) -> Integer.compare(a.getMoodScore(), b.getMoodScore()))
                .ifPresent(d -> sb.append(d.getRecordDate()).append("（").append(d.getMoodScore()).append(" 分）出现高点。\n\n"));

        sb.append("## 本周亮点\n\n");
        diaries.stream().limit(2).forEach(d ->
                sb.append("- 《").append(d.getTitle()).append("》（")
                        .append(d.getRecordDate()).append("，心情 ").append(d.getMoodScore()).append("）\n"));
        sb.append("\n## 下周建议\n\n");
        sb.append("- 保持记录习惯，每天一篇\n");
        sb.append("- 关注情绪低点的诱因，尝试复盘\n");
        sb.append("- 在心情低的日子里留一点积极记录\n\n");
        sb.append("> （本页为 Mock 模板生成。要生成 AI 智能周报，请在后端配置 DeepSeek API Key 后重启。）");
        return sb.toString();
    }

    private WeeklyReportVO toVO(WeeklyReport report) {
        WeeklyReportVO vo = new WeeklyReportVO();
        vo.setId(report.getId());
        vo.setWeekStart(report.getWeekStart());
        vo.setWeekEnd(report.getWeekEnd());
        vo.setDiaryCount(report.getDiaryCount());
        vo.setMoodAvg(report.getMoodAvg());
        vo.setSummary(report.getSummary());
        vo.setModel(report.getModel());
        vo.setCreatedAt(report.getCreatedAt());
        return vo;
    }
}
