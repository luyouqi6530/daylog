package com.daylog.modules.report.service.impl;

import com.daylog.common.exception.BusinessException;
import com.daylog.common.result.ResultCode;
import com.daylog.config.DeepSeekProperties;
import com.daylog.modules.report.agent.AgentTools;
import com.daylog.modules.report.agent.model.AgentStep;
import com.daylog.modules.report.agent.model.ParsedToolCall;
import com.daylog.modules.report.agent.model.ToolDefinition;
import com.daylog.modules.report.service.WeeklyReportAgentService;
import com.daylog.util.DeepSeekClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 周报 Agent 实现（ReAct 多步循环 + 自反思重写一次）
 *
 * <p>整体流程：</p>
 * <ol>
 *   <li>组装 system + user prompt（4 个 tool 定义一并下发）</li>
 *   <li>主循环：调 DeepSeek，如果 LLM 决定调 tool 就执行、回填 tool result，继续推进；
 *       当 LLM 给纯文本且包含 {@code <report>...</report>} 标签时视为最终答案，退出循环</li>
 *   <li>主循环结束后，对最终 draft 跑一轮"质检员"评估（无 tool），不通过则再请求一次重写</li>
 *   <li>返回清理后的 Markdown 正文</li>
 * </ol>
 *
 * <p>失败兜底：
 * <ul>
 *   <li>调用超步 → 抛出 AI_GENERATE_FAILED，让上层记录到日志并继续</li>
 *   <li>AgentTools 抛出 {@link AgentTools#EXCEPTION_INSUFFICIENT} → 直接抛出，
 *       上层映射为 AI_INSUFFICIENT_DIARY 业务错误</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyReportAgentServiceImpl implements WeeklyReportAgentService {

    /** Agent 主循环最多走的步数（防止 LLM 死循环） */
    private static final int MAX_AGENT_STEPS = 8;

    /** 最终答案包裹标签 */
    private static final Pattern REPORT_TAG = Pattern.compile(
            "<report>([\\s\\S]*?)</report>", Pattern.CASE_INSENSITIVE);

    /** 质检员用 prompt 要求返回 PASS 或 ISSUES:... */
    private static final Pattern QA_PASS = Pattern.compile("^\\s*PASS\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    private static final String SYSTEM_PROMPT = """
            你是一位温暖、专业的个人日记分析师，正在为用户生成周报。

            # 工作流程（必须遵守）
            1. 第一次调用必须先调用 getUserProfile 了解用户昵称和累计数据。
            2. 接着调用 getWeekDiaries 拉本周日记。若工具返回的 diaries 字段以 INSUFFICIENT_DIARY 开头，应停止所有操作，直接回复：INSUFFICIENT_DIARY。
            3. 根据需要继续调用：getWeekTagUsage（标签分布）、getMoodTrendChart（趋势数据），但不要重复调用同一个工具。
            4. 综合所有工具返回的数据，撰写一篇 Markdown 周报，包含：
               ## 本周概览（2-3 句总结）
               ## 情绪观察（结合心情分变化，指出情绪高点/低点及可能原因）
               ## 本周亮点（值得记住的瞬间）
               ## 下周建议（1-3 条具体可行的建议）
            5. 语气亲切自然，避免"根据提供的内容"这类机械表述，300-500 字。
            6. 用 <report>...</report> 把整篇周报包裹起来作为唯一最终答案。
            7. 不要在 <report> 之外输出任何额外文字。

            # 严格要求
            - 仅在你确认信息充分时输出 <report>，否则继续调用工具
            - 工具返回的 INSUFFICIENT_DIARY 当作终态，立即输出 INSUFFICIENT_DIARY 后停止
            """;

    private static final String REFLECTION_SYSTEM = """
            你是一位严格的周报质检员。检查一份周报草案：
            1. 是否含 "## 本周概览 / ## 情绪观察 / ## 本周亮点 / ## 下周建议" 四个小标题
            2. 是否提及具体日期或心情分（证明基于工具数据而非编造）
            3. 字数是否 ≥ 200
            4. 语气是否亲切自然（不能是清单堆砌或机械翻译）

            只输出两行：
            - 第一行：PASS 或 ISSUES
            - 第二行（仅当 ISSUES）：列出具体问题（最多 3 条），供重写参考
            """;

    private final DeepSeekClient deepSeekClient;
    private final DeepSeekProperties deepSeekProperties;
    private final AgentTools agentTools;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String generateReport(Long userId, LocalDate weekStart) {
        // 1. 构造工具定义（4 个）
        List<ToolDefinition> tools = buildToolDefinitions();

        // 2. 初始 messages
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        messages.add(Map.of("role", "user", "content",
                "请为 userId=" + userId + " 的用户生成周报，周一日期：" + weekStart));

        // 3. Agent 主循环
        String draft = null;
        for (int step = 1; step <= MAX_AGENT_STEPS; step++) {
            log.info("[Agent] 用户={} 周起={} 第 {} 步", userId, weekStart, step);

            AgentStep resp = deepSeekClient.chatWithTools(messages, tools, 0.6);

            // 追加 assistant message（content + tool_calls）
            Map<String, Object> assistantMsg = new LinkedHashMap<>();
            assistantMsg.put("role", "assistant");
            if (resp.content() != null) assistantMsg.put("content", resp.content());
            if (!resp.isFinalAnswer() && resp.toolCalls() != null) {
                assistantMsg.put("tool_calls", serializeToolCallsForRequest(resp.toolCalls()));
            }
            messages.add(assistantMsg);

            if (resp.isFinalAnswer()) {
                // 最终答案：尝试从 content 取 <report>...</report>
                draft = extractReport(resp.content());
                if (draft == null) {
                    // 没有 report 标签但也没调工具 → 直接用 content
                    draft = resp.content() == null ? "" : resp.content().trim();
                }
                break;
            }

            // 工具调用：执行每个工具并回填
            for (ParsedToolCall call : resp.toolCalls()) {
                String toolResult;
                try {
                    Object rawResult = invokeTool(call.name(), call.arguments(), userId, weekStart);
                    toolResult = objectMapper.writeValueAsString(rawResult);
                } catch (Exception e) {
                    String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                    if (msg.startsWith(AgentTools.EXCEPTION_INSUFFICIENT)) {
                        log.info("[Agent] 用户={} 本周日记不足，终止: {}", userId, msg);
                        throw new BusinessException(ResultCode.AI_INSUFFICIENT_DIARY);
                    }
                    log.warn("[Agent] 工具 {} 执行失败: {}", call.name(), msg);
                    // 把错误作为 tool result 回给 LLM，让它能看到失败、有机会改策略
                    toolResult = "{\"error\":\"" + msg.replace("\"", "'") + "\"}";
                }
                Map<String, Object> toolMsg = new LinkedHashMap<>();
                toolMsg.put("role", "tool");
                toolMsg.put("tool_call_id", call.id());
                toolMsg.put("content", toolResult);
                messages.add(toolMsg);
            }
        }

        if (draft == null) {
            log.warn("[Agent] 用户={} 周起={} 达到步数上限仍未产出，判定失败", userId, weekStart);
            throw new BusinessException(ResultCode.AI_GENERATE_FAILED);
        }

        // 4. 自反思：让质检员审一遍，不通过则再改一次
        return reflectAndMaybeRewrite(messages, draft);
    }

    /* -------------------- 工具分发 -------------------- */

    private Object invokeTool(String name, Map<String, Object> args, Long userId, LocalDate weekStart) {
        return switch (name) {
            case "getUserProfile" -> agentTools.getUserProfile(userId);
            case "getWeekDiaries" -> agentTools.getWeekDiaries(userId, asDate(args, "weekStart", weekStart));
            case "getWeekTagUsage" -> agentTools.getWeekTagUsage(userId, asDate(args, "weekStart", weekStart));
            case "getMoodTrendChart" -> agentTools.getMoodTrendChart(userId, asDate(args, "weekStart", weekStart));
            default -> throw new IllegalArgumentException("未知工具：" + name);
        };
    }

    /** 把 LLM 传入的日期字符串参数安全转成 LocalDate，缺省回退到入参 weekStart */
    private LocalDate asDate(Map<String, Object> args, String key, LocalDate fallback) {
        Object raw = args.get(key);
        if (raw == null) return fallback;
        try {
            return LocalDate.parse(raw.toString());
        } catch (Exception e) {
            return fallback;
        }
    }

    /* -------------------- 工具定义（OpenAI JSON Schema 风格） -------------------- */

    private List<ToolDefinition> buildToolDefinitions() {
        Map<String, Object> stringParam = Map.of("type", "string");
        Map<String, Object> integerParam = Map.of("type", "integer");

        Map<String, Object> weekStartSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "weekStart", Map.of(
                                "type", "string",
                                "format", "date",
                                "description", "周一日期，YYYY-MM-DD 格式")
                ),
                "required", List.of("weekStart")
        );

        return List.of(
                new ToolDefinition(
                        "getUserProfile",
                        "获取用户昵称、累计日记数、连续记录天数、累计字数",
                        Map.of("type", "object", "properties", Map.of(), "required", List.of())
                ),
                new ToolDefinition(
                        "getWeekDiaries",
                        "获取指定周内用户的所有日记，每篇含日期、心情分、标题、内容（截断）、标签。必须先调用此工具。少于 3 天时返回 INSUFFICIENT_DIARY。",
                        weekStartSchema
                ),
                new ToolDefinition(
                        "getWeekTagUsage",
                        "获取本周用户使用过的标签及频次排序，用于生成亮点和主题分析",
                        weekStartSchema
                ),
                new ToolDefinition(
                        "getMoodTrendChart",
                        "获取本周 7 天每日心情分（无日记为 null），用于绘制情绪曲线和识别高低点",
                        weekStartSchema
                )
        );
    }

    /* -------------------- 自反思 -------------------- */

    /**
     * 自反思 + 一次改写机会
     */
    private String reflectAndMaybeRewrite(List<Map<String, Object>> originalMessages, String draft) {
        // 用一份独立 context（不带 tool 定义，避免 LLM 又调工具）
        List<Map<String, Object>> qaMessages = new ArrayList<>();
        qaMessages.add(Map.of("role", "system", "content", REFLECTION_SYSTEM));
        qaMessages.add(Map.of("role", "user", "content", "请评审以下周报：\n\n" + draft));

        AgentStep qaResp = deepSeekClient.chatWithTools(qaMessages, null, 0.2);
        String qaVerdict = qaResp.content() == null ? "" : qaResp.content().trim();

        if (QA_PASS.matcher(qaVerdict).find()) {
            log.info("[Agent] 质检 PASS，直接采用");
            return draft;
        }
        log.info("[Agent] 质检未通过，要求重写。反馈：{}", qaVerdict.replace('\n', ' '));

        // 再来一次：把反馈塞回去，要求改写
        List<Map<String, Object>> rewriteMessages = new ArrayList<>(originalMessages);
        rewriteMessages.add(Map.of("role", "user", "content",
                "刚才的草稿被质检指出问题：" + qaVerdict + " 请基于所有工具数据重新写一版，用 <report>...</report> 包裹。"));

        AgentStep rewriteResp = deepSeekClient.chatWithTools(rewriteMessages, null, 0.6);
        String rewritten = extractReport(rewriteResp.content());
        if (rewritten == null && rewriteResp.content() != null) {
            rewritten = rewriteResp.content().trim();
        }
        return rewritten == null ? draft : rewritten;
    }

    /* -------------------- 工具调用请求体序列化 -------------------- */

    /**
     * DeepSeek/OpenAI 工具调用协议要求 assistant message 里 tool_calls 是：
     * [{id, type:"function", function:{name, arguments(str)}}]
     * 这里把 ParsedToolCall（Map<String,Object> arguments）反向序列化
     */
    private List<Map<String, Object>> serializeToolCallsForRequest(List<ParsedToolCall> calls) {
        List<Map<String, Object>> out = new ArrayList<>(calls.size());
        for (ParsedToolCall c : calls) {
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", c.name());
            try {
                function.put("arguments", objectMapper.writeValueAsString(c.arguments()));
            } catch (Exception e) {
                function.put("arguments", "{}");
            }
            Map<String, Object> tc = new LinkedHashMap<>();
            tc.put("id", c.id());
            tc.put("type", "function");
            tc.put("function", function);
            out.add(tc);
        }
        return out;
    }

    /* -------------------- <report> 标签提取 -------------------- */

    private String extractReport(String content) {
        if (content == null) return null;
        Matcher m = REPORT_TAG.matcher(content);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }
}
