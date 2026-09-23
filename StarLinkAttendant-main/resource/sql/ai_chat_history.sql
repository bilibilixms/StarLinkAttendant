-- ============================================================
-- AI 助手历史对话表（AI 模块落库）
-- 数据库：starlink_attendant
-- 说明：
--  1. 会话表 ai_chat_session 存"一次对话"的元信息（标题、归属用户）；
--  2. 消息表 ai_chat_message 存会话内的每条消息（用户提问 / AI 回答）；
--  3. 遵循项目逻辑删除规范（deleted_at：NULL 未删，非空 = 删除时间）；
--  4. user_type：user = 用户端（顾客/会员），admin = 管理端（管理员）。
-- ============================================================

CREATE DATABASE IF NOT EXISTS `starlink_attendant` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `starlink_attendant`;

-- ------------------------------------------------------------
-- 1. AI 会话表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `ai_chat_session`;
CREATE TABLE `ai_chat_session` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `session_no`    VARCHAR(32)  NOT NULL COMMENT '对外会话号（唯一，前端/接口用）',
    `user_type`     VARCHAR(10)  NOT NULL DEFAULT 'user' COMMENT '使用端：user=用户端，admin=管理端',
    `user_id`       BIGINT       NULL COMMENT '归属用户ID（会员ID或管理员ID，未登录为空）',
    `user_name`     VARCHAR(64)  NULL COMMENT '归属用户冗余名（展示用，如昵称/工号）',
    `title`         VARCHAR(100) NOT NULL DEFAULT '新对话' COMMENT '会话标题（默认取首条提问前20字）',
    `message_count` INT          NOT NULL DEFAULT 0 COMMENT '消息条数（冗余统计）',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted_at`    DATETIME     NULL COMMENT '逻辑删除时间（NULL=未删）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_session_no` (`session_no`),
    KEY `idx_user` (`user_type`, `user_id`, `deleted_at`),
    KEY `idx_updated` (`updated_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'AI 助手会话表';

-- ------------------------------------------------------------
-- 2. AI 消息表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `ai_chat_message`;
CREATE TABLE `ai_chat_message` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `session_id`    BIGINT       NOT NULL COMMENT '所属会话ID（ai_chat_session.id）',
    `role`          VARCHAR(10)  NOT NULL COMMENT '消息角色：user=用户提问，assistant=AI回答',
    `content`       TEXT         NOT NULL COMMENT '消息内容',
    `thinking_json` TEXT         NULL COMMENT 'AI思考步骤（JSON数组，可空）',
    `cost_ms`       INT          NULL COMMENT '本次回答耗时（毫秒，可空）',
    `model`         VARCHAR(64)  NULL COMMENT '回答所用模型名（可空）',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `deleted_at`    DATETIME     NULL COMMENT '逻辑删除时间（NULL=未删）',
    PRIMARY KEY (`id`),
    KEY `idx_session` (`session_id`, `deleted_at`),
    KEY `idx_created` (`created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'AI 助手消息表';
