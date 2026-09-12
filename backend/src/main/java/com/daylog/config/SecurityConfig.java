package com.daylog.config;

import com.daylog.common.result.Result;
import com.daylog.common.result.ResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.daylog.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Spring Security 配置
 *
 * <p>核心思路：前后端分离 + JWT，完全无状态：</p>
 * <ul>
 *   <li>关闭 CSRF（无 cookie 会话，不受 CSRF 攻击面影响）</li>
 *   <li>Session 策略 STATELESS，不创建 HttpSession</li>
 *   <li>自定义 JWT 过滤器插在用户名密码过滤器之前</li>
 *   <li>认证失败返回统一 JSON（而非默认的 302/403 HTML）</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    private final ObjectMapper objectMapper;

    /**
     * 安全过滤器链
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 认证接口放行
                        .requestMatchers("/auth/register", "/auth/login").permitAll()
                        // 图片静态资源放行（上传接口本身仍需登录）
                        .requestMatchers("/files/**").permitAll()
                        // Swagger 接口文档放行（仅开发环境，生产由 profile 控制关闭）
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // Spring Boot 默认错误转发路径放行（否则错误响应本身会被 401 拦截）
                        .requestMatchers("/error").permitAll()
                        // 其余接口一律需要认证
                        .anyRequest().authenticated())
                .exceptionHandling(exception ->
                        exception.authenticationEntryPoint(unauthorizedEntryPoint()))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 密码加密器：BCrypt（自带盐，抗彩虹表）
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 禁用 JWT 过滤器的 Servlet 全局自动注册。
     *
     * <p>过滤器标了 @Component 后，Spring Boot 会把它自动注册进
     * Servlet 容器的全局过滤器链；而 Security 里 addFilterBefore 又
     * 注册了一次——同一请求过滤器会执行两遍。此 Bean 将自动注册关闭，
     * 只保留 Security 过滤器链中的那一份。</p>
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(
            JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * 认证失败（未登录 / token 无效）时返回统一 JSON 结构
     */
    private AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(
                    Result.fail(ResultCode.UNAUTHORIZED)));
        };
    }
}
