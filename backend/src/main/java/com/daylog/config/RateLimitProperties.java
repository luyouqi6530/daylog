package com.daylog.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 登录/注册接口限流配置（绑定 daylog.rate-limit.*）
 */
@Data
@Component
@ConfigurationProperties(prefix = "daylog.rate-limit")
public class RateLimitProperties {

    /** 登录计数窗口（秒） */
    private int loginWindowSeconds = 60;

    /** 同一客户端 IP 在一个窗口内允许的登录请求数 */
    private int loginMaxAttempts = 10;

    /** 注册计数窗口（秒） */
    private int registerWindowSeconds = 3600;

    /** 同一客户端 IP 在一个窗口内允许的注册请求数 */
    private int registerMaxAttempts = 5;
}
