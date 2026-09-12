package com.daylog.modules.report.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.daylog.common.exception.BusinessException;
import com.daylog.common.result.ResultCode;
import com.daylog.config.DeepSeekProperties;
import com.daylog.modules.diary.entity.Diary;
import com.daylog.modules.diary.mapper.DiaryMapper;
import com.daylog.modules.report.dto.GenerateReportDTO;
import com.daylog.modules.report.entity.WeeklyReport;
import com.daylog.modules.report.mapper.WeeklyReportMapper;
import com.daylog.modules.report.service.WeeklyReportAgentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WeeklyReportServiceImpl 单元测试
 *
 * <p>核心契约（4 象限决策）：</p>
 * <pre>
 *              apiKey 配置
 *                  │
 *           ┌──────┴───────┐
 *           │              │
 *         空/无           有
 *           │              │
 *       mock-no-key    Agent 路径
 *                          │
 *                  ┌───────┼──────────┐
 *                  │       │          │
 *              成功    业务错      工程异常
 *                  │    AI_NO_      (网络/解析/其他)
 *                  │    DIARY_         │
 *               deepseek- WEEK     重新抛出   降级 mock
 *               agent      │                  │
 *                          └→ throw         mock-fallback
 * </pre>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WeeklyReportServiceImpl 单元测试")
class WeeklyReportServiceImplTest {

    @Mock
    private WeeklyReportMapper weeklyReportMapper;

    @Mock
    private DiaryMapper diaryMapper;

    @Mock
    private WeeklyReportAgentService agentService;

    @Mock
    private DeepSeekProperties deepSeekProperties;

    @InjectMocks
    private WeeklyReportServiceImpl reportService;

    // ====================== 参数校验 ======================

    @Test
    @DisplayName("generate：weekStart 非周一抛 BAD_REQUEST")
    void generate_weekStartNotMonday_throwsBadRequest() {
        GenerateReportDTO dto = new GenerateReportDTO();
        dto.setWeekStartDate(LocalDate.of(2026, 9, 8)); // 周二

        assertThatThrownBy(() -> reportService.generate(1L, dto))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ResultCode.BAD_REQUEST.getCode()));

        verify(agentService, never()).generateReport(anyLong(), any(LocalDate.class));
    }

    @Test
    @DisplayName("generate：该周没有日记抛 AI_NO_DIARY_THIS_WEEK")
    void generate_noDiariesThisWeek_throwsNoDiary() {
        GenerateReportDTO dto = new GenerateReportDTO();
        dto.setWeekStartDate(LocalDate.of(2026, 9, 7)); // 周一
        when(diaryMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        assertThatThrownBy(() -> reportService.generate(1L, dto))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ResultCode.AI_NO_DIARY_THIS_WEEK.getCode()));
    }

    // ====================== 无 key → mock-no-key ======================

    @Test
    @DisplayName("generate：apiKey 为空时走 mock-no-key，不调 Agent")
    void generate_noApiKey_usesMockFallback() {
        when(deepSeekProperties.getApiKey()).thenReturn(null);
        GenerateReportDTO dto = new GenerateReportDTO();
        dto.setWeekStartDate(LocalDate.of(2026, 9, 7));
        when(diaryMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(buildDiaries());
        when(weeklyReportMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        // insert 后回填 ID
        org.mockito.Mockito.doAnswer(inv -> {
            WeeklyReport r = inv.getArgument(0);
            r.setId(888L);
            return 1;
        }).when(weeklyReportMapper).insert(any(WeeklyReport.class));

        Long id = reportService.generate(1L, dto);

        assertThat(id).isEqualTo(888L);
        verify(agentService, never()).generateReport(anyLong(), any(LocalDate.class));
        verify(weeklyReportMapper).insert(any(WeeklyReport.class));
    }

    // ====================== Agent 路径 ======================

    @Test
    @DisplayName("generate：apiKey 有 + Agent 成功 → model=deepseek-agent，存 insert")
    void generate_agentSucceeds_storesAsDeepseekAgent() {
        when(deepSeekProperties.getApiKey()).thenReturn("sk-real-key");
        GenerateReportDTO dto = new GenerateReportDTO();
        dto.setWeekStartDate(LocalDate.of(2026, 9, 7));
        when(diaryMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(buildDiaries());
        when(agentService.generateReport(1L, LocalDate.of(2026, 9, 7)))
                .thenReturn("# 周报\nAI 生成的精彩内容");
        when(weeklyReportMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        org.mockito.Mockito.doAnswer(inv -> {
            WeeklyReport r = inv.getArgument(0);
            r.setId(777L);
            return 1;
        }).when(weeklyReportMapper).insert(any(WeeklyReport.class));

        reportService.generate(1L, dto);

        org.mockito.ArgumentCaptor<WeeklyReport> captor =
                org.mockito.ArgumentCaptor.forClass(WeeklyReport.class);
        verify(weeklyReportMapper).insert(captor.capture());
        WeeklyReport saved = captor.getValue();
        assertThat(saved.getModel()).isEqualTo("deepseek-agent");
        assertThat(saved.getSummary()).contains("AI 生成的精彩内容");
        assertThat(saved.getDiaryCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("generate：Agent 抛 AI_NO_DIARY_THIS_WEEK → 业务异常继续上抛（不降级 mock）")
    void generate_agentThrowsInsufficientDiary_propagates() {
        when(deepSeekProperties.getApiKey()).thenReturn("sk-key");
        GenerateReportDTO dto = new GenerateReportDTO();
        dto.setWeekStartDate(LocalDate.of(2026, 9, 7));
        // 服务层本身已经把"无日记"挡了，但 Agent 内部工具 < 3 篇也会抛；
        // 此处模拟 Agent 自己抛业务异常
        when(diaryMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(buildDiaries());
        when(agentService.generateReport(eq(1L), any(LocalDate.class)))
                .thenThrow(new BusinessException(ResultCode.AI_NO_DIARY_THIS_WEEK));

        assertThatThrownBy(() -> reportService.generate(1L, dto))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ResultCode.AI_NO_DIARY_THIS_WEEK.getCode()));

        verify(weeklyReportMapper, never()).insert(any(WeeklyReport.class));
        verify(weeklyReportMapper, never()).updateById(any(WeeklyReport.class));
    }

    @Test
    @DisplayName("generate：Agent 抛其他 BusinessException（如网络/密钥错）→ 降级 mock-fallback")
    void generate_agentThrowsOtherBusinessException_fallsBackToMock() {
        when(deepSeekProperties.getApiKey()).thenReturn("sk-bad-key");
        GenerateReportDTO dto = new GenerateReportDTO();
        dto.setWeekStartDate(LocalDate.of(2026, 9, 7));
        when(diaryMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(buildDiaries());
        when(agentService.generateReport(eq(1L), any(LocalDate.class)))
                .thenThrow(new BusinessException(ResultCode.AI_GENERATE_FAILED));
        when(weeklyReportMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        org.mockito.Mockito.doAnswer(inv -> {
            WeeklyReport r = inv.getArgument(0);
            r.setId(666L);
            return 1;
        }).when(weeklyReportMapper).insert(any(WeeklyReport.class));

        Long id = reportService.generate(1L, dto);

        assertThat(id).isEqualTo(666L);
        org.mockito.ArgumentCaptor<WeeklyReport> captor =
                org.mockito.ArgumentCaptor.forClass(WeeklyReport.class);
        verify(weeklyReportMapper).insert(captor.capture());
        assertThat(captor.getValue().getModel()).isEqualTo("mock-fallback");
    }

    @Test
    @DisplayName("generate：Agent 抛 RuntimeException（非业务异常）→ 同样降级 mock-fallback")
    void generate_agentThrowsRuntime_fallsBackToMock() {
        when(deepSeekProperties.getApiKey()).thenReturn("sk-key");
        GenerateReportDTO dto = new GenerateReportDTO();
        dto.setWeekStartDate(LocalDate.of(2026, 9, 7));
        when(diaryMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(buildDiaries());
        when(agentService.generateReport(eq(1L), any(LocalDate.class)))
                .thenThrow(new RuntimeException("DeepSeek API timeout"));
        when(weeklyReportMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        org.mockito.Mockito.doAnswer(inv -> {
            WeeklyReport r = inv.getArgument(0);
            r.setId(555L);
            return 1;
        }).when(weeklyReportMapper).insert(any(WeeklyReport.class));

        reportService.generate(1L, dto);

        org.mockito.ArgumentCaptor<WeeklyReport> captor =
                org.mockito.ArgumentCaptor.forClass(WeeklyReport.class);
        verify(weeklyReportMapper).insert(captor.capture());
        assertThat(captor.getValue().getModel()).isEqualTo("mock-fallback");
    }

    // ====================== upsert ======================

    @Test
    @DisplayName("generate：同周已存在周报时走 updateById 而非 insert")
    void generate_existingReport_updatesInsteadOfInsert() {
        when(deepSeekProperties.getApiKey()).thenReturn(null);
        GenerateReportDTO dto = new GenerateReportDTO();
        dto.setWeekStartDate(LocalDate.of(2026, 9, 7));
        when(diaryMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(buildDiaries());

        WeeklyReport existing = new WeeklyReport();
        existing.setId(333L);
        existing.setUserId(1L);
        when(weeklyReportMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        Long id = reportService.generate(1L, dto);

        assertThat(id).isEqualTo(333L);
        verify(weeklyReportMapper).updateById(any(WeeklyReport.class));
        verify(weeklyReportMapper, never()).insert(any(WeeklyReport.class));
    }

    // ====================== helper ======================

    private List<Diary> buildDiaries() {
        Diary d1 = new DayOfWeekHelper().build(LocalDate.of(2026, 9, 7), 5, "周一例会");
        Diary d2 = new DayOfWeekHelper().build(LocalDate.of(2026, 9, 8), 4, "周二编码");
        Diary d3 = new DayOfWeekHelper().build(LocalDate.of(2026, 9, 9), 3, "周三复盘");
        return List.of(d1, d2, d3);
    }

    /** 单纯为了在 helper 中少写几行代码 */
    static class DayOfWeekHelper {
        Diary build(LocalDate date, int mood, String title) {
            Diary d = new Diary();
            d.setRecordDate(date);
            d.setMoodScore(mood);
            d.setTitle(title);
            return d;
        }
    }
}