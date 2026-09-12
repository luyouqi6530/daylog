package com.daylog.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * 登录用户主体，实现 Spring Security 的 UserDetails
 *
 * <p>本项目无角色权限体系，authorities 固定为空。
 * JWT 无状态认证下不从数据库实时加载，凭据字段全部置空。</p>
 */
@Getter
public class LoginUser implements UserDetails {

    private final Long userId;

    private final String username;

    public LoginUser(Long userId, String username) {
        this.userId = userId;
        this.username = username;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.emptyList();
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
