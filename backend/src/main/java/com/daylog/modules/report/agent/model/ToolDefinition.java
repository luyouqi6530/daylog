package com.daylog.modules.report.agent.model;

import java.util.Map;

/**
 * OpenAI 兼容格式的工具定义（一个 tool = type=function + function 子对象）
 *
 * <p>直接以 Map 形式传给 DeepSeek 请求体，避免引入额外的 JSON Schema Builder 依赖。</p>
 *
 * @param name         工具名（Agent 服务侧按此分发）
 * @param description  工具作用（LLM 看到，决定何时调用）
 * @param parameters   JSON Schema：{"type":"object","properties":{...},"required":[...],...}
 */
public record ToolDefinition(String name, String description, Map<String, Object> parameters) {

    /**
     * 转成 DeepSeek/OpenAI 请求体中 tools 数组的一个元素
     */
    public Map<String, Object> toRequestItem() {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", name,
                        "description", description,
                        "parameters", parameters
                )
        );
    }
}
