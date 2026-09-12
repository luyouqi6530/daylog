package com.daylog.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 认证过滤器
 *
 * <p>每个请求只执行一次（OncePerRequestFilter），流程：</p>
 * <ol>
 *   <li>取 Authorization: Bearer &lt;token&gt; 头</li>
 *   <li>无 token 直接放行（由 Security 的授权规则决定是否 401）</li>
 *   <li>校验黑名单（登出过的 token 拒绝）</li>
 *   <li>解析并验签，成功则把 LoginUser 写入 SecurityContext</li>
 * </ol>
 *
 * <p>注意：这里不从数据库加载用户——JWT 无状态的优势就是
 * 每次请求不需要查库，用户信息直接从 token 中还原。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String TOKEN_PREFIX = "Bearer ";

    private final JwtUtils jwtUtils;
    private final TokenBlacklistService tokenBlacklistService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (StringUtils.hasText(token) && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                if (tokenBlacklistService.isBlacklisted(token)) {
                    // 已登出的 token：不放行，直接交给认证入口点返回 401
                    filterChain.doFilter(request, response);
                    return;
                }
                Claims claims = jwtUtils.parseToken(token);
                LoginUser loginUser = new LoginUser(jwtUtils.getUserId(claims),
                        claims.get("username", String.class));
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception e) {
                // token 无效：不中断过滤器链，保持未认证状态，
                // 受保护接口会由 AuthenticationEntryPoint 统一返回 401 JSON
                log.debug("JWT 认证失败: {}", e.getMessage());
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 解析请求头中的 token
     */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(AUTH_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(TOKEN_PREFIX)) {
            return header.substring(TOKEN_PREFIX.length());
        }
        return null;
    }
}
