package com.daylog.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * DeepSeek 配置项（绑定 daylog.ai.deepseek.*）
 */
@Data
@Component
@ConfigurationProperties(prefix = "daylog.ai.deepseek")
public class DeepSeekProperties {

    /** API 基础地址（OpenAI 兼容格式） */
    private String baseUrl;

    private String apiKey;

    private String model;

    /** 请求超时（秒） */
    private int timeoutSeconds = 60;
}
