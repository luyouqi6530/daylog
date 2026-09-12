package com.daylog.modules.auth.vo;

import lombok.Data;

/**
 * 登录响应：token + 用户信息
 */
@Data
public class LoginVO {

    /**
     * JWT 令牌
     */
    private String token;

    /**
     * 用户信息
     */
    private UserVO userInfo;
}
