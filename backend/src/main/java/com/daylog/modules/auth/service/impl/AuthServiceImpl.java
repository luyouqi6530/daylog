package com.daylog.modules.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.daylog.common.exception.BusinessException;
import com.daylog.common.result.ResultCode;
import com.daylog.modules.auth.dto.LoginDTO;
import com.daylog.modules.auth.dto.RegisterDTO;
import com.daylog.modules.auth.entity.User;
import com.daylog.modules.auth.mapper.UserMapper;
import com.daylog.modules.auth.service.AuthService;
import com.daylog.modules.auth.vo.LoginVO;
import com.daylog.modules.auth.vo.UserVO;
import com.daylog.security.JwtUtils;
import com.daylog.security.SecurityUtils;
import com.daylog.security.TokenBlacklistService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 认证服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final TokenBlacklistService tokenBlacklistService;

    @Override
    public Long register(RegisterDTO dto) {
        // 服务层友好校验 + 数据库唯一索引兜底（双层防御）
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, dto.getUsername()));
        if (count > 0) {
            throw new BusinessException(ResultCode.USERNAME_EXISTS);
        }

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        // 昵称不传默认同用户名
        user.setNickname(StringUtils.hasText(dto.getNickname()) ? dto.getNickname() : dto.getUsername());

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 并发注册同名用户的极端场景，由唯一索引兜底
            throw new BusinessException(ResultCode.USERNAME_EXISTS);
        }
        log.info("新用户注册成功: userId={}, username={}", user.getId(), user.getUsername());
        return user.getId();
    }

    @Override
    public LoginVO login(LoginDTO dto) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, dto.getUsername()));
        // 用户不存在与密码错误返回同一提示，避免账号枚举攻击
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
        }

        String token = jwtUtils.generateToken(user.getId(), user.getUsername());

        LoginVO loginVO = new LoginVO();
        loginVO.setToken(token);
        loginVO.setUserInfo(toUserVO(user));
        log.info("用户登录成功: userId={}", user.getId());
        return loginVO;
    }

    @Override
    public void logout(String token) {
        if (!StringUtils.hasText(token)) {
            return;
        }
        Claims claims = jwtUtils.parseToken(token);
        // 黑名单 TTL = 剩余有效期，到期自动清理
        tokenBlacklistService.blacklist(token, jwtUtils.getRemainingMillis(claims));
        log.info("用户登出: userId={}", jwtUtils.getUserId(claims));
    }

    @Override
    public UserVO getCurrentUserInfo() {
        Long userId = SecurityUtils.getCurrentUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户不存在或已注销");
        }
        return toUserVO(user);
    }

    /**
     * 实体转 VO，密码等敏感字段天然被隔离
     */
    private UserVO toUserVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        return vo;
    }
}
