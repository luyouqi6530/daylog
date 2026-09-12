package com.daylog.modules.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.daylog.common.exception.BusinessException;
import com.daylog.common.result.ResultCode;
import com.daylog.modules.auth.dto.LoginDTO;
import com.daylog.modules.auth.dto.RegisterDTO;
import com.daylog.modules.auth.entity.User;
import com.daylog.modules.auth.mapper.UserMapper;
import com.daylog.modules.auth.vo.LoginVO;
import com.daylog.security.JwtUtils;
import com.daylog.security.TokenBlacklistService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuthServiceImpl 单元测试
 *
 * <p>核心契约（面试必问）：</p>
 * <ol>
 *   <li>登录：用户名不存在 + 密码错误必须返回<strong>同一</strong>提示（防账号枚举）</li>
 *   <li>注册：服务层 count 预判 + DB 唯一索引双层防御</li>
 *   <li>登出：token 为空跳过；非空则写黑名单 TTL = 剩余有效期</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthServiceImpl 单元测试")
class AuthServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @InjectMocks
    private AuthServiceImpl authService;

    // ====================== register ======================

    @Test
    @DisplayName("register：用户名已存在时直接抛 USERNAME_EXISTS，不走 insert")
    void register_existingUsername_throwsUsernameExists() {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("alice");
        dto.setPassword("secret123");

        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> authService.register(dto))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ResultCode.USERNAME_EXISTS.getCode()));

        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    @DisplayName("register：用户名不冲突时 BCrypt 加密 + 插入 DB，返回新 userId")
    void register_newUsername_succeeds() {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("bob");
        dto.setPassword("secret123");
        dto.setNickname(null); // 测试默认昵称回退到 username

        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(passwordEncoder.encode("secret123")).thenReturn("BCRYPT_HASH");
        // 模拟 insert 后回填雪花 ID
        doAnswerWithId(123L);

        Long userId = authService.register(dto);

        assertThat(userId).isEqualTo(123L);
        verify(passwordEncoder).encode("secret123");
        verify(userMapper).insert(any(User.class));
    }

    @Test
    @DisplayName("register：DB 唯一索引兜底——并发导致 DuplicateKeyException 时也抛 USERNAME_EXISTS")
    void register_concurrentDuplicateKey_fallsBackToUsernameExists() {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("alice");
        dto.setPassword("secret123");

        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(passwordEncoder.encode(anyString())).thenReturn("HASH");
        when(userMapper.insert(any(User.class)))
                .thenThrow(new DuplicateKeyException("uk_user_username"));

        assertThatThrownBy(() -> authService.register(dto))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ResultCode.USERNAME_EXISTS.getCode()));
    }

    // ====================== login ======================

    @Test
    @DisplayName("login：用户不存在时抛 USERNAME_OR_PASSWORD_ERROR（1004）")
    void login_userNotFound_throwsUsernameOrPasswordError() {
        LoginDTO dto = new LoginDTO();
        dto.setUsername("ghost");
        dto.setPassword("any");

        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> authService.login(dto))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ResultCode.USERNAME_OR_PASSWORD_ERROR.getCode()));
    }

    @Test
    @DisplayName("login：密码错误时同样抛 1004（与用户不存在同码，防账号枚举）")
    void login_wrongPassword_throwsUsernameOrPasswordError() {
        LoginDTO dto = new LoginDTO();
        dto.setUsername("alice");
        dto.setPassword("wrong");

        User u = new User();
        u.setId(1L);
        u.setUsername("alice");
        u.setPassword("CORRECT_HASH");
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(u);
        when(passwordEncoder.matches("wrong", "CORRECT_HASH")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(dto))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ResultCode.USERNAME_OR_PASSWORD_ERROR.getCode()));
    }

    @Test
    @DisplayName("login：成功时签发 JWT，返回 LoginVO（含 token + UserVO）")
    void login_success_returnsTokenAndUserInfo() {
        LoginDTO dto = new LoginDTO();
        dto.setUsername("alice");
        dto.setPassword("right");

        User u = new User();
        u.setId(12345L);
        u.setUsername("alice");
        u.setNickname("Alice");
        u.setPassword("HASH");

        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(u);
        when(passwordEncoder.matches("right", "HASH")).thenReturn(true);
        when(jwtUtils.generateToken(12345L, "alice")).thenReturn("JWT_TOKEN");

        LoginVO vo = authService.login(dto);

        assertThat(vo.getToken()).isEqualTo("JWT_TOKEN");
        assertThat(vo.getUserInfo().getUsername()).isEqualTo("alice");
        // 安全设计：UserVO 不暴露 getPassword（连 getter 都没有）
        // 因此编译期就能挡住"密码泄露"——这是 VO 隔离的天然安全网
    }

    // ====================== logout ======================

    @Test
    @DisplayName("logout：token 为空（前端未传 Authorization）时直接跳过，不抛异常")
    void logout_emptyToken_skipsSilently() {
        authService.logout(null);
        authService.logout("");

        verify(jwtUtils, never()).parseToken(anyString());
        verify(tokenBlacklistService, never()).blacklist(anyString(), anyLong());
    }

    @Test
    @DisplayName("logout：有效 token 走 parseToken + 写黑名单，TTL = 剩余毫秒")
    void logout_validToken_blacklistsWithRemainingTtl() {
        String token = "VALID_TOKEN";
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        when(jwtUtils.parseToken(token)).thenReturn(claims);
        when(jwtUtils.getRemainingMillis(claims)).thenReturn(60_000L);
        when(jwtUtils.getUserId(claims)).thenReturn(7L);

        authService.logout(token);

        verify(tokenBlacklistService, times(1)).blacklist(token, 60_000L);
    }

    /**
     * 模拟 userMapper.insert 后的雪花 ID 回填
     */
    private void doAnswerWithId(long id) {
        org.mockito.Mockito.doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(id);
            return 1;
        }).when(userMapper).insert(any(User.class));
    }
}