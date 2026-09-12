package com.daylog.modules.auth.vo;

import lombok.Data;

/**
 * 用户信息视图对象（对外输出，不含密码等敏感字段）
 *
 * <p>id 为雪花ID（19 位 Long），由 JacksonConfig 统一序列化为字符串，
 * 避免前端 JS Number 精度丢失。</p>
 */
@Data
public class UserVO {

    private Long id;

    private String username;

    private String nickname;

    private String avatar;
}
