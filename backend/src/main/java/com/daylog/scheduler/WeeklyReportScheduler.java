package com.daylog.scheduler;

import com.daylog.modules.diary.mapper.DiaryMapper;
import com.daylog.modules.report.dto.GenerateReportDTO;
import com.daylog.modules.report.service.WeeklyReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/**
 * AI 周报定时任务
 *
 * <p>每周一 08:00 为"上周有日记"的用户自动生成周报。
 * 单用户失败重试 3 次，最终失败记日志告警，不阻塞其他用户。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeeklyReportScheduler {

    private static final int MAX_RETRY = 3;

    private final DiaryMapper diaryMapper;
    private final WeeklyReportService weeklyReportService;

    /**
     * cron: 秒 分 时 日 月 周（周一 08:00:00）
     */
    @Scheduled(cron = "0 0 8 * * MON")
    public void generateWeeklyReports() {
        // 上周范围：上周一 ~ 上周日
        LocalDate lastMonday = LocalDate.now().with(DayOfWeek.MONDAY).minusWeeks(1);
        LocalDate lastSunday = lastMonday.plusDays(6);

        List<Long> userIds = diaryMapper.selectUserIdsBetween(lastMonday, lastSunday);
        if (userIds.isEmpty()) {
            log.info("上周无用户写日记，跳过周报生成");
            return;
        }
        log.info("开始为 {} 位用户生成上周周报（{} ~ {}）", userIds.size(), lastMonday, lastSunday);

        GenerateReportDTO dto = new GenerateReportDTO();
        dto.setWeekStartDate(lastMonday);

        int success = 0;
        int failed = 0;
        for (Long userId : userIds) {
            boolean ok = generateWithRetry(userId, dto);
            if (ok) {
                success++;
            } else {
                failed++;
            }
        }
        log.info("周报生成完成：成功 {}，失败 {}", success, failed);
    }

    private boolean generateWithRetry(Long userId, GenerateReportDTO dto) {
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                weeklyReportService.generate(userId, dto);
                return true;
            } catch (Exception e) {
                // "该周没有日记"是正常业务情况，不算失败
                if (e.getMessage() != null && e.getMessage().contains("没有日记")) {
                    return true;
                }
                log.warn("用户 {} 周报生成第 {} 次失败: {}", userId, attempt, e.getMessage());
            }
        }
        log.error("用户 {} 周报生成最终失败（已重试 {} 次）", userId, MAX_RETRY);
        return false;
    }
}
