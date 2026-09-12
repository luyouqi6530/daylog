package com.daylog.modules.report.agent.model;

import java.util.List;

/**
 * Agent 单步响应（解析 DeepSeek 返回的 message）
 *
 * <p>两种形态：① 最终答案（content 非空、toolCalls 为空）② 工具调用（toolCalls 非空，content 可能为空）</p>
 *
 * @param content   模型回复正文（最终答案时携带，工具调用时通常为空）
 * @param toolCalls 本步要执行的工具调用
 */
public record AgentStep(String content, List<ParsedToolCall> toolCalls) {

    public boolean isFinalAnswer() {
        return toolCalls == null || toolCalls.isEmpty();
    }
}
