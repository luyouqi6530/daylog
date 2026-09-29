package com.daylog.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RateLimitInterceptor 单元测试
 *
 * <p>验证三件事：阈值的边界（等于 max 放行、大于 max 拦截）、
 * 客户端 IP 的取值来源（信 X-Real-IP，不信 getRemoteAddr）、
 * 以及 Redis 不可用（count=-1）时按放行处理。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimitInterceptor 单元测试")
class RateLimitInterceptorTest {

    private static final int WINDOW_SECONDS = 60;
    private static final int MAX_ATTEMPTS = 10;

    @Mock
    private RateLimitService rateLimitService;

    private RateLimitInterceptor interceptor;

    private MockHttpServletRequest request;

    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        interceptor = new RateLimitInterceptor(
                "login", rateLimitService, new ObjectMapper(), WINDOW_SECONDS, MAX_ATTEMPTS);
        request = new MockHttpServletRequest("POST", "/auth/login");
        request.setRemoteAddr("172.18.0.4"); // nginx 容器网段，绝不能当客户端身份用
        response = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("未超阈值放行，且不写响应体")
    void preHandle_underLimit_allows() throws Exception {
        request.addHeader("X-Real-IP", "203.0.113.9");
        when(rateLimitService.countInWindow("login", "203.0.113.9", WINDOW_SECONDS)).thenReturn(5L);

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    @DisplayName("边界：第 max 次仍放行，第 max+1 次返回 429 + Retry-After + code 4001")
    void preHandle_atBoundary_blocksOnlyAfterMax() throws Exception {
        request.addHeader("X-Real-IP", "203.0.113.9");
        when(rateLimitService.countInWindow(anyString(), anyString(), anyInt()))
                .thenReturn((long) MAX_ATTEMPTS, (long) MAX_ATTEMPTS + 1);

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(request, blocked, new Object())).isFalse();
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isEqualTo(String.valueOf(WINDOW_SECONDS));
        assertThat(blocked.getContentAsString())
                .contains("\"code\":4001")
                .contains("操作过于频繁");
    }

    @Test
    @DisplayName("限流维度取 X-Real-IP（nginx 覆盖式写入，客户端伪造不了）")
    void preHandle_usesRealIpHeader() throws Exception {
        request.addHeader("X-Real-IP", "198.51.100.7");
        when(rateLimitService.countInWindow(anyString(), anyString(), anyInt())).thenReturn(1L);

        interceptor.preHandle(request, response, new Object());

        verify(rateLimitService).countInWindow("login", "198.51.100.7", WINDOW_SECONDS);
    }

    @Test
    @DisplayName("没有 X-Real-IP 时退回 getRemoteAddr（本机直连后端的场景）")
    void preHandle_fallsBackToRemoteAddr() throws Exception {
        when(rateLimitService.countInWindow(anyString(), anyString(), anyInt())).thenReturn(1L);

        interceptor.preHandle(request, response, new Object());

        verify(rateLimitService).countInWindow("login", "172.18.0.4", WINDOW_SECONDS);
    }

    @Test
    @DisplayName("异常长的 X-Real-IP 被截断到 64 字符，防止把 Redis key 撑爆")
    void preHandle_truncatesOverlongIdentity() throws Exception {
        request.addHeader("X-Real-IP", "9".repeat(200));
        when(rateLimitService.countInWindow(anyString(), anyString(), anyInt())).thenReturn(1L);

        interceptor.preHandle(request, response, new Object());

        verify(rateLimitService).countInWindow("login", "9".repeat(64), WINDOW_SECONDS);
    }

    @Test
    @DisplayName("Redis 不可用（count=-1）时放行：可用性优先于严格限流")
    void preHandle_whenCounterUnavailable_allows() throws Exception {
        request.addHeader("X-Real-IP", "203.0.113.9");
        when(rateLimitService.countInWindow(anyString(), anyString(), anyInt())).thenReturn(-1L);

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    @DisplayName("窗口与阈值来自构造参数：register 用另一套配置")
    void interceptor_isConfigurable() throws Exception {
        RateLimitInterceptor register = new RateLimitInterceptor(
                "register", rateLimitService, new ObjectMapper(), 3600, 5);
        request.addHeader("X-Real-IP", "203.0.113.9");
        when(rateLimitService.countInWindow("register", "203.0.113.9", 3600)).thenReturn(6L);

        assertThat(register.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(429);
    }
}
