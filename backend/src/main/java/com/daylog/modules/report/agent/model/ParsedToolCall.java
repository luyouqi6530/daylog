package com.daylog.modules.report.agent.model;

import java.util.Map;

/**
 * 解析后的单次 tool_call（来自 DeepSeek/OpenAI 工具调用响应）
 *
 * @param id        LLM 给本次调用的唯一 id，回传结果时需要 echo
 * @param name      工具名（与工具定义中的 name 一致）
 * @param arguments 已 JSON 反序列化的参数（key=参数名, value=String/Double/Boolean/List/Map）
 */
public record ParsedToolCall(String id, String name, Map<String, Object> arguments) {
}
