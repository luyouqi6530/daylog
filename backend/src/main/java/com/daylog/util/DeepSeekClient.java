package com.daylog.util;

import com.daylog.common.exception.BusinessException;
import com.daylog.common.result.ResultCode;
import com.daylog.config.DeepSeekProperties;
import com.daylog.modules.report.agent.model.AgentStep;
import com.daylog.modules.report.agent.model.ParsedToolCall;
import com.daylog.modules.report.agent.model.ToolDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek 客户端（OpenAI 兼容 /chat/completions 接口）
 *
 * <p>提供两种用法：
 * <ul>
 *   <li>{@link #chat(String, String)}  单轮对话，用于 Mock 模式兜底</li>
 *   <li>{@link #chatWithTools(List, List, Double)}  多轮工具调用对话，用于 AI Agent</li>
 * </ul>
 *
 * <p>用 Spring 6 自带的 RestClient（同步 HTTP 客户端，WebFlux 之外无需额外依赖），不引入 Spring AI——
 * 本项目只需两个 chat 形态，工具调用自己解析 OpenAI 协议的 tool_calls 字段。</p>
 */
@Slf4j
@Component
public class DeepSeekClient {

    private final DeepSeekProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DeepSeekClient(DeepSeekProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .requestFactory(clientHttpRequestFactory())
                .build();
    }

    /**
     * 单轮对话，返回模型回复文本
     */
    @SuppressWarnings("unchecked")
    public String chat(String systemPrompt, String userPrompt) {
        Map<String, Object> requestBody = Map.of(
                "model", properties.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)),
                "temperature", 0.7
        );

        try {
            Map<String, Object> response = restClient.post()
                    .uri("/chat/completions")
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new IllegalStateException("响应无 choices");
            }
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            return (String) message.get("content");
        } catch (Exception e) {
            log.error("DeepSeek 调用失败: {}", e.getMessage());
            throw new BusinessException(ResultCode.AI_GENERATE_FAILED);
        }
    }

    /**
     * 多轮工具调用对话（ReAct 主循环用）。
     *
     * <p>支持传入完整 messages 历史（含 system/user/assistant/tool 四种 role），
     * 配合 tools 数组，L 模型自行决定下一步要调用哪些工具。
     * 返回的 {@link AgentStep} 描述了 LLM 的下一步动作——是要继续调工具（{@link AgentStep#isFinalAnswer()} 为 false）
     * 还是给出最终答案（true）。</p>
     *
     * @param messages   完整历史对话，每条 role ∈ {system, user, assistant, tool}
     * @param tools      工具定义列表（OpenAI 兼容格式）；传 null 或空 = 不支持工具调用
     * @param temperature 采样温度；null 用默认 0.7
     */
    @SuppressWarnings("unchecked")
    public AgentStep chatWithTools(List<Map<String, Object>> messages,
                                   List<ToolDefinition> tools,
                                   Double temperature) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", properties.getModel());
        requestBody.put("messages", messages);
        requestBody.put("temperature", temperature == null ? 0.7 : temperature);
        if (tools != null && !tools.isEmpty()) {
            List<Map<String, Object>> toolItems = new ArrayList<>(tools.size());
            for (ToolDefinition t : tools) {
                toolItems.add(t.toRequestItem());
            }
            requestBody.put("tools", toolItems);
        }

        try {
            Map<String, Object> response = restClient.post()
                    .uri("/chat/completions")
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new IllegalStateException("响应无 choices");
            }
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");

            String content = (String) message.get("content");

            // 解析 tool_calls
            List<ParsedToolCall> parsed = new ArrayList<>();
            Object rawToolCalls = message.get("tool_calls");
            if (rawToolCalls instanceof List<?> list && !list.isEmpty()) {
                for (Object obj : list) {
                    if (obj instanceof Map<?, ?> tc) {
                        Map<String, Object> function = (Map<String, Object>) tc.get("function");
                        String callId = (String) tc.get("id");
                        String name = (String) function.get("name");
                        // arguments 在协议里是 JSON 字符串，需要反序列化
                        Object argsRaw = function.get("arguments");
                        Map<String, Object> argsMap;
                        if (argsRaw == null || argsRaw.toString().isBlank()) {
                            argsMap = Map.of();
                        } else {
                            try {
                                argsMap = objectMapper.readValue(argsRaw.toString(), Map.class);
                            } catch (Exception e) {
                                log.warn("无法解析 tool_call.arguments: {}", argsRaw);
                                argsMap = Map.of();
                            }
                        }
                        parsed.add(new ParsedToolCall(callId, name, argsMap));
                    }
                }
            }
            log.debug("DeepSeek 工具调用 step 解析: content_len={}, tool_calls={}",
                    content == null ? 0 : content.length(), parsed.size());
            return new AgentStep(content, parsed);
        } catch (Exception e) {
            log.error("DeepSeek 工具调用请求失败: {}", e.getMessage());
            throw new BusinessException(ResultCode.AI_GENERATE_FAILED);
        }
    }

    /**
     * 基于 SimpleClientHttpRequestFactory 配置连接/读取超时
     */
    private org.springframework.http.client.SimpleClientHttpRequestFactory clientHttpRequestFactory() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()));
        return factory;
    }
}
