package com.daylog.security;

import com.daylog.common.exception.BusinessException;
import com.daylog.common.result.ResultCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 安全上下文工具类
 *
 * <p>Controller / Service 层通过它获取当前登录用户ID，
 * 避免与 Spring Security API 直接耦合。</p>
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * 获取当前登录用户ID，未登录时抛 401
     */
    public static Long getCurrentUserId() {
        LoginUser loginUser = getLoginUser();
        if (loginUser == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return loginUser.getUserId();
    }

    /**
     * 获取当前登录用户，未登录返回 null
     */
    public static LoginUser getLoginUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser;
        }
        return null;
    }
}
