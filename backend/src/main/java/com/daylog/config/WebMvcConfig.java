package com.daylog.config;

import com.daylog.security.RateLimitInterceptor;
import com.daylog.security.RateLimitService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 *
 * <p>两件事：</p>
 * <ul>
 *   <li>把 /files/** 映射到本地磁盘上传目录，图片由后端静态资源直接服务</li>
 *   <li>给 /auth/login 与 /auth/register 挂 IP 限流拦截器（这两个路径在
 *       SecurityConfig 里是 permitAll，公网上会被扫描器爆破，必须自己挡住）</li>
 * </ul>
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${daylog.upload.dir}")
    private String uploadDir;

    private final RateLimitService rateLimitService;

    private final RateLimitProperties rateLimitProperties;

    private final ObjectMapper objectMapper;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // file: 协议前缀，路径必须以 / 结尾
        registry.addResourceHandler("/files/**")
                .addResourceLocations("file:" + uploadDir + "/");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RateLimitInterceptor(
                        "login", rateLimitService, objectMapper,
                        rateLimitProperties.getLoginWindowSeconds(),
                        rateLimitProperties.getLoginMaxAttempts()))
                .addPathPatterns("/auth/login");

        registry.addInterceptor(new RateLimitInterceptor(
                        "register", rateLimitService, objectMapper,
                        rateLimitProperties.getRegisterWindowSeconds(),
                        rateLimitProperties.getRegisterMaxAttempts()))
                .addPathPatterns("/auth/register");
    }
}
