package com.daylog.modules.auth.controller;

import com.daylog.common.exception.BusinessException;
import com.daylog.common.result.ResultCode;
import com.daylog.config.RateLimitProperties;
import com.daylog.modules.auth.dto.LoginDTO;
import com.daylog.modules.auth.dto.RegisterDTO;
import com.daylog.modules.auth.service.AuthService;
import com.daylog.modules.auth.vo.LoginVO;
import com.daylog.modules.auth.vo.UserVO;
import com.daylog.security.JwtAuthenticationFilter;
import com.daylog.security.RateLimitService;
import com.daylog.security.TokenBlacklistService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AuthController MockMvc 测试（@WebMvcTest 切片）
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li>合法请求 → 200 + code=200 + data 中含 token</li>
 *   <li>参数非法（用户名 4 位以下）→ 400（参数校验由 @Valid 触发）</li>
 *   <li>账号/密码错误 → code=1004（防账号枚举的统一提示）</li>
 *   <li>注册时用户名已存在 → code=1001</li>
 * </ul>
 *
 * <p>关键配置说明：</p>
 * <ul>
 *   <li>{@code excludeAutoConfiguration = SecurityAutoConfiguration.class}
 *       —— 禁用 Spring Security 自动配置，否则它会去拿 UserDetailsService / SecurityFilterChain</li>
 *   <li>{@code excludeFilters} 排除 JwtAuthenticationFilter —— 它是 @Component（Filter 子类），
 *       WebMvcTest 默认扫描，构造函数需要 JwtUtils，会因找不到 bean 而启动失败</li>
 *   <li>{@code excludeFilters} 排除 TokenBlacklistService —— 同上，避免被 JwtAuthenticationFilter 间接拉起</li>
 *   <li>{@code @AutoConfigureMockMvc(addFilters = false)} —— 双保险：哪怕有过滤器被注册也不参与</li>
 * </ul>
 */
@WebMvcTest(controllers = AuthController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                        classes = {JwtAuthenticationFilter.class, TokenBlacklistService.class})
        })
@AutoConfigureMockMvc(addFilters = false) // 兜底：万一过滤器被注册也不参与
@DisplayName("AuthController MockMvc 测试")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    /**
     * @WebMvcTest 会把 WebMvcConfigurer（即 WebMvcConfig）拉进切片，而它现在构造依赖
     * 限流的两个 bean；切片不加载 Redis 自动配置，所以这里 mock 掉。
     * 注意 MockMvc 仍会执行 MVC 拦截器（addFilters=false 只关 Servlet 过滤器），
     * mock 的计数默认返回 0 → 永远不触发限流，登录/注册用例照常通过。
     */
    @MockBean
    private RateLimitService rateLimitService;

    @MockBean
    private RateLimitProperties rateLimitProperties;

    // ====================== login ======================

    @Test
    @DisplayName("POST /auth/login: 合法请求返回 200，data 含 token 与 userInfo")
    void login_success() throws Exception {
        LoginDTO dto = new LoginDTO();
        dto.setUsername("alice");
        dto.setPassword("secret123");

        LoginVO vo = new LoginVO();
        vo.setToken("JWT_TOKEN_123");
        UserVO user = new UserVO();
        user.setId(12345L);
        user.setUsername("alice");
        vo.setUserInfo(user);

        when(authService.login(any(LoginDTO.class))).thenReturn(vo);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("操作成功"))
                .andExpect(jsonPath("$.data.token").value("JWT_TOKEN_123"))
                .andExpect(jsonPath("$.data.userInfo.username").value("alice"));
    }

    @Test
    @DisplayName("POST /auth/login: 密码错误时 code=1004（与用户不存在同码，防枚举）")
    void login_wrongCredentials_returnsCode1004() throws Exception {
        LoginDTO dto = new LoginDTO();
        dto.setUsername("alice");
        dto.setPassword("wrong");

        when(authService.login(any(LoginDTO.class)))
                .thenThrow(new BusinessException(ResultCode.USERNAME_OR_PASSWORD_ERROR));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk()) // 全局异常处理器把它转成 Result
                .andExpect(jsonPath("$.code").value(1004))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    @DisplayName("POST /auth/login: 缺字段触发 @Valid 校验 → code=400")
    void login_missingFields_returns400() throws Exception {
        // username 留空触发 @NotBlank
        LoginDTO dto = new LoginDTO();
        dto.setUsername("");
        dto.setPassword("secret123");

        // 设计决策：GlobalExceptionHandler 把 MethodArgumentNotValidException 统一包成 Result.fail(BAD_REQUEST)，
        // HTTP 状态码保持 200，业务码是 400。前端按业务码判断，不是 HTTP 状态码。
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ====================== register ======================

    @Test
    @DisplayName("POST /auth/register: 合法请求返回 200，data=userId")
    void register_success() throws Exception {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("new_user_2026");
        dto.setPassword("secret123");

        when(authService.register(any(RegisterDTO.class))).thenReturn(1234567890123456789L);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(1234567890123456789L));
    }

    @Test
    @DisplayName("POST /auth/register: 用户名已存在 → code=1001")
    void register_usernameExists_returnsCode1001() throws Exception {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("alice");
        dto.setPassword("secret123");

        when(authService.register(any(RegisterDTO.class)))
                .thenThrow(new BusinessException(ResultCode.USERNAME_EXISTS));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.message").value("用户名已存在"));
    }

    @Test
    @DisplayName("POST /auth/register: 用户名非法格式（少于 4 位）→ code=400")
    void register_invalidUsername_returns400() throws Exception {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("ab"); // < 4 位
        dto.setPassword("secret123");

        // 同 login：@Valid 失败被 GlobalExceptionHandler 转成 code=400，HTTP 仍 200
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }
}