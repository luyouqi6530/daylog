package com.daylog.modules.auth.service;

import com.daylog.modules.auth.dto.LoginDTO;
import com.daylog.modules.auth.dto.RegisterDTO;
import com.daylog.modules.auth.vo.LoginVO;
import com.daylog.modules.auth.vo.UserVO;

/**
 * 认证服务
 */
public interface AuthService {

    /**
     * 注册
     *
     * @return 新用户ID
     */
    Long register(RegisterDTO dto);

    /**
     * 登录
     *
     * @return token + 用户信息
     */
    LoginVO login(LoginDTO dto);

    /**
     * 登出（token 加入 Redis 黑名单）
     *
     * @param token 请求头中的 JWT 原文
     */
    void logout(String token);

    /**
     * 查询当前登录用户信息
     */
    UserVO getCurrentUserInfo();
}
