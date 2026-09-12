package com.daylog.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LoginUser 单元测试
 *
 * <p>LoginUser 是无状态认证下的轻量 UserDetails：不持有密码，所有 enabled 标志位恒为 true，
 * 永远返回空权限集合。验证这些契约避免重构时被悄悄破坏。</p>
 */
@DisplayName("LoginUser 单元测试")
class LoginUserTest {

    @Test
    @DisplayName("字段读写：userId / username 正确暴露")
    void getters_returnConstructorValues() {
        LoginUser u = new LoginUser(100L, "alice");

        assertThat(u.getUserId()).isEqualTo(100L);
        assertThat(u.getUsername()).isEqualTo("alice");
    }

    @Test
    @DisplayName("UserDetails 契约：getAuthorities 始终为空集合（项目无角色体系）")
    void authorities_alwaysEmpty() {
        LoginUser u = new LoginUser(1L, "u");

        assertThat(u.getAuthorities()).isEmpty();
    }

    @Test
    @DisplayName("UserDetails 契约：getPassword 始终 null（JWT 无状态认证不持有凭据）")
    void password_alwaysNull() {
        LoginUser u = new LoginUser(1L, "u");

        assertThat(u.getPassword()).isNull();
    }

    @Test
    @DisplayName("UserDetails 契约：所有 enabled/expired/locked 标志恒为 true（不锁定用户）")
    void allEnabledFlags_areTrue() {
        LoginUser u = new LoginUser(1L, "u");

        assertThat(u.isAccountNonExpired()).isTrue();
        assertThat(u.isAccountNonLocked()).isTrue();
        assertThat(u.isCredentialsNonExpired()).isTrue();
        assertThat(u.isEnabled()).isTrue();
    }
}