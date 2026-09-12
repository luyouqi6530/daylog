package com.daylog.modules.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户实体，对应 user 表
 *
 * <p>逻辑删除：仅 user 表使用（账号注销场景）。
 * MyBatis-Plus 全局配置了 logic-delete-field=deleted，
 * MP 的查询会自动追加 deleted=0 条件。</p>
 */
@Data
@TableName("`user`")
public class User implements Serializable {

    /**
     * 用户ID（雪花ID，应用层生成）
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 用户名（登录账号，唯一）
     */
    private String username;

    /**
     * 密码（BCrypt 密文，绝不返回给前端）
     */
    private String password;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 头像 URL
     */
    private String avatar;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 创建时间（数据库维护）
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间（数据库维护）
     */
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除: 0-正常 1-已注销
     */
    @TableLogic
    private Integer deleted;
}
