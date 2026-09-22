-- ============================================================
-- 会员成长值（经验）功能：升级体系
-- 规则：消费/充值获得成长值，达到阈值自动升级
-- ============================================================

USE `starlink_attendant`;

-- 会员表新增累计成长值字段（1元=1经验，实付金额向下取整）
ALTER TABLE `member`
    ADD COLUMN `growth_value` INT NOT NULL DEFAULT 0 COMMENT '累计成长值（1元=1经验）' AFTER `available_points`;

-- 成长值流水表
CREATE TABLE `member_growth_log` (
    `id`              BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `member_id`       BIGINT UNSIGNED  NOT NULL                 COMMENT '会员ID → member.id',
    `growth`          INT              NOT NULL                 COMMENT '本次获得成长值（正数）',
    `growth_before`   INT              NOT NULL DEFAULT 0       COMMENT '变动前累计成长值',
    `growth_after`    INT              NOT NULL DEFAULT 0       COMMENT '变动后累计成长值',
    `biz_type`        TINYINT          NOT NULL                 COMMENT '业务类型：1-收银直接消费 2-余额充值',
    `biz_id`          BIGINT UNSIGNED  DEFAULT NULL             COMMENT '关联业务ID（订单ID/充值记录ID）',
    `level_before_id` BIGINT UNSIGNED  DEFAULT NULL             COMMENT '变动前等级ID',
    `level_after_id`  BIGINT UNSIGNED  DEFAULT NULL             COMMENT '变动后等级ID（含自动升级后）',
    `is_upgraded`     TINYINT          NOT NULL DEFAULT 0       COMMENT '本次是否触发自动升级：0-否 1-是',
    `remark`          VARCHAR(255)     DEFAULT NULL             COMMENT '备注',
    `created_at`      DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)            COMMENT '创建时间',
    `updated_at`      DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted_at`      DATETIME(3)      DEFAULT NULL             COMMENT '逻辑删除（NULL=未删除）',
    `version`         INT UNSIGNED     NOT NULL DEFAULT 1       COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    KEY `idx_member_id` (`member_id`),
    KEY `idx_biz_type` (`biz_type`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员成长值流水表';