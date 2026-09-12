package com.daylog.modules.report.service;

import java.time.LocalDate;

/**
 * AI 周报 Agent 服务（基于 ReAct 多步工具调用循环）
 *
 * <p>与原 {@link WeeklyReportService} 同在 report 模块，是更高阶的"Agent 化"实现。
 * 调用方（controller/scheduler）无需关心是否启用 Agent，统一通过入口进。</p>
 */
public interface WeeklyReportAgentService {

    /**
     * 为指定用户运行 Agent 多步推理，生成周报正文（Markdown）。
     *
     * @param userId    用户 ID
     * @param weekStart 周一日期
     * @return 周报正文（不含 <report> 标签，已清理前后缀）；Agent 流程跑完仍未产出（理论上不会发生）
     *         时返回 null 表示失败可降级
     * @throws IllegalStateException 当周日记不足 3 天时（透传 AgentTools.EXCEPTION_INSUFFICIENT）
     */
    String generateReport(Long userId, LocalDate weekStart);
}
