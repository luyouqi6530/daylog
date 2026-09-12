-- ============================================================
-- Daylog 数据库初始化脚本
-- 项目: Daylog 每日记录与心情追踪平台
-- 环境: MySQL 8.0+  引擎: InnoDB  字符集: utf8mb4
--
-- 设计说明:
--   1. 主键统一为 BIGINT 雪花ID，由 MyBatis-Plus ASSIGN_ID
--      在应用层生成，避免自增ID暴露业务量、便于未来分库分表
--   2. 通用字段 created_at / updated_at 由数据库自动维护
--   3. 逻辑删除仅用于 user 表（账号注销场景）
--      diary / tag 采用物理删除：因为它们存在联合唯一索引，
--      逻辑删除会导致唯一约束失效（已删记录占据唯一键位置），
--      这是逻辑删除与唯一索引冲突的典型场景
--   4. diary 的 (user_id, record_date) 唯一约束实现"一天一篇"，
--      并发场景下由数据库兜底，服务层做友好提示
-- ============================================================

CREATE DATABASE IF NOT EXISTS `daylog`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `daylog`;

-- ------------------------------------------------------------
-- 1. 用户表
-- ------------------------------------------------------------
CREATE TABLE `user` (
    `id`         BIGINT       NOT NULL                COMMENT '用户ID（雪花ID）',
    `username`   VARCHAR(30)  NOT NULL                COMMENT '用户名（登录账号）',
    `password`   VARCHAR(100) NOT NULL                COMMENT '密码（BCrypt 加密存储）',
    `nickname`   VARCHAR(30)  NOT NULL                COMMENT '昵称',
    `avatar`     VARCHAR(255) DEFAULT NULL            COMMENT '头像 URL',
    `email`      VARCHAR(100) DEFAULT NULL            COMMENT '邮箱',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`    TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除: 0-正常 1-已注销',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE = InnoDB COMMENT = '用户表';

-- ------------------------------------------------------------
-- 2. 日记表（一天一篇，物理删除）
-- ------------------------------------------------------------
CREATE TABLE `diary` (
    `id`          BIGINT       NOT NULL                COMMENT '日记ID（雪花ID）',
    `user_id`     BIGINT       NOT NULL                COMMENT '所属用户ID',
    `title`       VARCHAR(100) NOT NULL                COMMENT '标题',
    `content`     LONGTEXT     NOT NULL                COMMENT '正文（Markdown 格式）',
    `mood_score`  TINYINT      NOT NULL                COMMENT '心情分: 1-5（1最差 5最好）',
    `weather`     VARCHAR(20)  DEFAULT NULL            COMMENT '天气（可选）',
    `record_date` DATE         NOT NULL                COMMENT '记录日期（业务日期，非创建时间）',
    `word_count`  INT          NOT NULL DEFAULT 0      COMMENT '正文字数（冗余存储，加速统计）',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_date` (`user_id`, `record_date`),
    CONSTRAINT `chk_mood` CHECK (`mood_score` BETWEEN 1 AND 5)
) ENGINE = InnoDB COMMENT = '日记表';

-- ------------------------------------------------------------
-- 3. 标签表（物理删除，删除时级联清理关联）
-- ------------------------------------------------------------
CREATE TABLE `tag` (
    `id`         BIGINT      NOT NULL                COMMENT '标签ID（雪花ID）',
    `user_id`    BIGINT      NOT NULL                COMMENT '所属用户ID',
    `name`       VARCHAR(20) NOT NULL                COMMENT '标签名',
    `color`      CHAR(7)     NOT NULL DEFAULT '#409EFF' COMMENT '标签颜色（HEX，如 #409EFF）',
    `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_name` (`user_id`, `name`)
) ENGINE = InnoDB COMMENT = '标签表';

-- ------------------------------------------------------------
-- 4. 日记-标签关联表（多对多）
-- ------------------------------------------------------------
CREATE TABLE `diary_tag` (
    `diary_id` BIGINT NOT NULL COMMENT '日记ID',
    `tag_id`   BIGINT NOT NULL COMMENT '标签ID',
    PRIMARY KEY (`diary_id`, `tag_id`),
    KEY `idx_tag` (`tag_id`)
) ENGINE = InnoDB COMMENT = '日记标签关联表';

-- ------------------------------------------------------------
-- 5. 附件表（日记配图，物理删除）
-- ------------------------------------------------------------
CREATE TABLE `attachment` (
    `id`            BIGINT       NOT NULL COMMENT '附件ID（雪花ID）',
    `user_id`       BIGINT       NOT NULL COMMENT '上传者ID',
    `diary_id`      BIGINT       DEFAULT NULL COMMENT '所属日记ID（上传时未保存日记则为空，保存时回填）',
    `original_name` VARCHAR(255) NOT NULL COMMENT '原始文件名',
    `file_path`     VARCHAR(255) NOT NULL COMMENT '存储相对路径',
    `file_size`     BIGINT       NOT NULL COMMENT '文件大小（字节）',
    `mime_type`     VARCHAR(50)  NOT NULL COMMENT 'MIME 类型',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    PRIMARY KEY (`id`),
    KEY `idx_user` (`user_id`),
    KEY `idx_diary` (`diary_id`)
) ENGINE = InnoDB COMMENT = '附件表';

-- ------------------------------------------------------------
-- 6. AI 周报表（每周一份，重复生成时覆盖更新）
-- ------------------------------------------------------------
CREATE TABLE `weekly_report` (
    `id`             BIGINT        NOT NULL COMMENT '周报ID（雪花ID）',
    `user_id`        BIGINT        NOT NULL COMMENT '所属用户ID',
    `week_start`     DATE          NOT NULL COMMENT '周一日期（该周的唯一标识）',
    `week_end`       DATE          NOT NULL COMMENT '周日日期',
    `diary_count`    INT           NOT NULL COMMENT '本周日记篇数',
    `mood_avg`       DECIMAL(3, 1) NOT NULL COMMENT '本周平均心情分',
    `summary`        TEXT          NOT NULL COMMENT 'AI 生成的周报内容（Markdown）',
    `model`          VARCHAR(50)   NOT NULL COMMENT '生成模型名称（如 deepseek-chat）',
    `created_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_week` (`user_id`, `week_start`)
) ENGINE = InnoDB COMMENT = 'AI 周报表';
