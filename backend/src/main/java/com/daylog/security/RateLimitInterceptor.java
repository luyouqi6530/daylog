package com.daylog.security;

import com.daylog.common.result.Result;
import com.daylog.common.result.ResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录/注册接口的 IP 限流拦截器
 *
 * <p>挂在 Spring MVC 的拦截器链上（不是 Servlet 过滤器）：这两个路径在
 * SecurityConfig 里是 permitAll，请求一定会走到 DispatcherServlet，
 * 所以拦在这里既能覆盖到它们，又能用 addPathPatterns 声明式地限定范围。</p>
 *
 * <p>一个类配两个实例（login / register 各一套窗口与阈值），阈值由
 * WebMvcConfig 用 new 的方式注入，因此本类不加 @Component。</p>
 */
@Slf4j
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    /** nginx 用 proxy_set_header X-Real-IP $remote_addr 覆盖同名头，客户端伪造不了 */
    private static final String REAL_IP_HEADER = "X-Real-IP";

    /** key 里 IP 的最大长度，防止异常头部把 Redis key 撑爆 */
    private static final int MAX_IDENTITY_LENGTH = 64;

    private final String action;
    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;
    private final int windowSeconds;
    private final int maxAttempts;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String clientIp = resolveClientIp(request);
        long count = rateLimitService.countInWindow(action, clientIp, windowSeconds);
        if (count <= maxAttempts) {
            // count == -1 是 Redis 不可用，此处按放行处理：可用性优先于严格限流
            return true;
        }
        log.warn("限流拦截: action={}, ip={}, count={}, max={}", action, clientIp, count, maxAttempts);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(windowSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                Result.fail(ResultCode.TOO_MANY_REQUESTS)));
        return false;
    }

    /**
     * 取客户端真实 IP
     *
     * <p>后端在 nginx 之后，{@code getRemoteAddr()} 只会看到容器网段，
     * 所有访客会被塞进同一个桶。这里优先信任 X-Real-IP；不用
     * X-Forwarded-For 是因为 nginx 对它做的是 append 语义
     * （$proxy_add_x_forwarded_for），客户端自带的值会原样带进来。</p>
     */
    private String resolveClientIp(HttpServletRequest request) {
        String realIp = request.getHeader(REAL_IP_HEADER);
        String ip = (realIp == null || realIp.isBlank()) ? request.getRemoteAddr() : realIp;
        if (ip == null) {
            return "unknown";
        }
        return ip.length() > MAX_IDENTITY_LENGTH ? ip.substring(0, MAX_IDENTITY_LENGTH) : ip;
    }
}
