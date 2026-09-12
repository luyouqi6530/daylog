package com.daylog.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求参数
 */
@Data
public class RegisterDTO {

    /**
     * 用户名：4-30 位字母/数字/下划线
     */
    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9_]{4,30}$", message = "用户名须为 4-30 位字母、数字或下划线")
    private String username;

    /**
     * 密码：6-32 位（前端明文传输依赖 HTTPS，生产环境必须上 TLS）
     */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度须为 6-32 位")
    private String password;

    /**
     * 昵称：不传默认同用户名
     */
    @Size(max = 30, message = "昵称最长 30 个字符")
    private String nickname;
}
