# 网吧管理系统 — 数据库建表 SQL

> 文档版本：v2.0（Demo 单店版） | 最后更新：2026-07-12
> 基于 database-design.md v2.0 生成

---

## 1. 建表前准备

```sql
-- ============================================================
-- 数据库创建
-- ============================================================
CREATE DATABASE IF NOT EXISTS `internet_cafe`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE `internet_cafe`;

-- ============================================================
-- 全局设置
-- ============================================================
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
```

---

## 2. 建表语句

> 建表顺序已按外键依赖关系排序。所有表统一包含公共字段 `id / created_at / updated_at / deleted_at / version`。

### 2.1 会员域

#### member_level — 会员等级表

```sql
CREATE TABLE `member_level` (
    `id`                  BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `level_name`          VARCHAR(32)      NOT NULL                 COMMENT '等级名称：普通/银卡/金卡/钻石',
    `level_order`         TINYINT          NOT NULL                 COMMENT '排序序号（数值越大等级越高）',
    `min_growth`          INT              NOT NULL DEFAULT 0       COMMENT '成长值下限',
    `max_growth`          INT              NOT NULL                 COMMENT '成长值上限',
    `discount_rate`       DECIMAL(5,2)     NOT NULL DEFAULT 100.00  COMMENT '折扣率（%），100=无折扣',
    `hourly_discount`     DECIMAL(5,2)     NOT NULL DEFAULT 100.00  COMMENT '上机折扣率（%）',
    `recharge_bonus_rate` DECIMAL(5,2)     NOT NULL DEFAULT 0.00    COMMENT '充值赠送比例（%）',
    `points_multiple`     DECIMAL(5,2)     NOT NULL DEFAULT 1.00    COMMENT '积分获取倍数',
    `auto_upgrade`        TINYINT          NOT NULL DEFAULT 1       COMMENT '是否自动升级：0-否 1-是',
    `icon_url`            VARCHAR(255)     DEFAULT NULL             COMMENT '等级图标URL',
    -- 公共字段
    `created_at`          DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)            COMMENT '创建时间',
    `updated_at`          DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted_at`          DATETIME(3)      DEFAULT NULL             COMMENT '逻辑删除（NULL=未删除）',
    `version`             INT UNSIGNED     NOT NULL DEFAULT 1       COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员等级表';
```

#### member — 会员主表

```sql
CREATE TABLE `member` (
    `id`                  BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `member_no`           VARCHAR(32)      NOT NULL                 COMMENT '会员编号（自动生成）',
    `real_name`           VARCHAR(32)      DEFAULT NULL             COMMENT '真实姓名',
    `gender`              TINYINT          NOT NULL DEFAULT 0       COMMENT '性别：0-未知 1-男 2-女',
    `phone`               VARCHAR(20)      NOT NULL                 COMMENT '手机号（登录账号）',
    `id_card`             VARCHAR(18)      DEFAULT NULL             COMMENT '身份证号（加密存储）',
    `id_card_hash`        VARCHAR(64)      DEFAULT NULL             COMMENT '身份证哈希（用于去重）',
    `birthday`            DATE             DEFAULT NULL             COMMENT '出生日期',
    `level_id`            BIGINT UNSIGNED  DEFAULT NULL             COMMENT '当前等级ID → member_level.id',
    `total_points`        BIGINT           NOT NULL DEFAULT 0       COMMENT '累计获得积分',
    `available_points`    BIGINT           NOT NULL DEFAULT 0       COMMENT '可用积分',
    `total_recharge`      DECIMAL(12,2)    NOT NULL DEFAULT 0.00    COMMENT '累计充值金额',
    `balance`             DECIMAL(12,2)    NOT NULL DEFAULT 0.00    COMMENT '账户余额',
    `total_consumption`   DECIMAL(12,2)    NOT NULL DEFAULT 0.00    COMMENT '累计消费金额',
    `last_login_time`     DATETIME         DEFAULT NULL             COMMENT '最后登录时间',
    `last_online_time`    DATETIME         DEFAULT NULL             COMMENT '最后上机时间',
    `total_online_hours`  INT              NOT NULL DEFAULT 0       COMMENT '累计上机时长（分钟）',
    `face_feature`        VARCHAR(255)     DEFAULT NULL             COMMENT '人脸特征向量（密文）',
    `password_hash`       VARCHAR(128)     NOT NULL                 COMMENT '登录密码哈希',
    `register_source`     TINYINT          NOT NULL DEFAULT 1       COMMENT '注册来源：1-前台 2-后台导入',
    `status`              TINYINT          NOT NULL DEFAULT 1       COMMENT '状态：1-正常 2-冻结 3-黑名单',
    `blacklist_reason`    VARCHAR(255)     DEFAULT NULL             COMMENT '黑名单原因',
    `tag`                 VARCHAR(255)     DEFAULT NULL             COMMENT '标签（JSON数组，用于画像）',
    -- 公共字段
    `created_at`          DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)            COMMENT '创建时间',
    `updated_at`          DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted_at`          DATETIME(3)      DEFAULT NULL             COMMENT '逻辑删除（NULL=未删除）',
    `version`             INT UNSIGNED     NOT NULL DEFAULT 1       COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_member_no` (`member_no`),
    UNIQUE KEY `uk_phone` (`phone`),
    KEY `idx_level_id` (`level_id`),
    KEY `idx_id_card_hash` (`id_card_hash`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员主表';
```

#### member_points_log — 积分流水表

```sql
CREATE TABLE `member_points_log` (
    `id`              BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `member_id`       BIGINT UNSIGNED  NOT NULL                 COMMENT '会员ID → member.id',
    `points`          INT              NOT NULL                 COMMENT '变动积分（正=增加，负=消耗）',
    `balance_before`  INT              NOT NULL                 COMMENT '变动前积分',
    `balance_after`   INT              NOT NULL                 COMMENT '变动后积分',
    `biz_type`        TINYINT          NOT NULL                 COMMENT '业务类型：1-消费获得 2-充值赠送 3-兑换消耗 4-活动奖励 5-手动调整 6-过期扣除',
    `biz_id`          BIGINT UNSIGNED  DEFAULT NULL             COMMENT '关联业务ID',
    `remark`          VARCHAR(255)     DEFAULT NULL             COMMENT '备注',
    `expire_at`       DATETIME         DEFAULT NULL             COMMENT '过期时间',
    -- 公共字段
    `created_at`      DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)            COMMENT '创建时间',
    `updated_at`      DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted_at`      DATETIME(3)      DEFAULT NULL             COMMENT '逻辑删除（NULL=未删除）',
    `version`         INT UNSIGNED     NOT NULL DEFAULT 1       COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    KEY `idx_member_id` (`member_id`),
    KEY `idx_biz_type` (`biz_type`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='积分流水表';
```

#### member_recharge — 充值记录表

```sql
CREATE TABLE `member_recharge` (
    `id`                BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `member_id`         BIGINT UNSIGNED  NOT NULL                 COMMENT '会员ID → member.id',
    `recharge_no`       VARCHAR(32)      NOT NULL                 COMMENT '充值单号',
    `recharge_amount`   DECIMAL(12,2)    NOT NULL                 COMMENT '充值金额（实付）',
    `bonus_amount`      DECIMAL(12,2)    NOT NULL DEFAULT 0.00    COMMENT '赠送金额',
    `total_amount`      DECIMAL(12,2)    NOT NULL                 COMMENT '到账总额 = 充值 + 赠送',
    `balance_before`    DECIMAL(12,2)    NOT NULL                 COMMENT '充值前余额',
    `balance_after`     DECIMAL(12,2)    NOT NULL                 COMMENT '充值后余额',
    `payment_method`    TINYINT          NOT NULL                 COMMENT '支付方式：1-现金 2-会员余额',
    `payment_channel`   VARCHAR(32)      DEFAULT NULL             COMMENT '支付通道（Demo 项目预留）',
    `trade_no`          VARCHAR(64)      DEFAULT NULL             COMMENT '交易流水号（Demo 项目预留）',
    `operator_id`       BIGINT UNSIGNED  DEFAULT NULL             COMMENT '操作员工ID → employee.id（自助为NULL）',
    `campaign_id`       BIGINT UNSIGNED  DEFAULT NULL             COMMENT '关联活动ID → campaign.id',
    `status`            TINYINT          NOT NULL DEFAULT 0       COMMENT '状态：0-待支付 1-成功 2-失败 3-已退款',
    `paid_at`           DATETIME         DEFAULT NULL             COMMENT '支付完成时间',
    -- 公共字段
    `created_at`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)            COMMENT '创建时间',
    `updated_at`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted_at`        DATETIME(3)      DEFAULT NULL             COMMENT '逻辑删除（NULL=未删除）',
    `version`           INT UNSIGNED     NOT NULL DEFAULT 1       COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_recharge_no` (`recharge_no`),
    KEY `idx_member_id` (`member_id`),
    KEY `idx_paid_at` (`paid_at`),
    KEY `idx_operator_id` (`operator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='充值记录表';
```

---

### 2.2 上机管理域

#### seat_area — 机位区域表

```sql
CREATE TABLE `seat_area` (
    `id`            BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `area_name`     VARCHAR(32)      NOT NULL                 COMMENT '区域名称：如 A区/电竞区/包间',
    `area_color`    VARCHAR(7)       NOT NULL DEFAULT '#1890ff' COMMENT '区域标识色（十六进制）',
    `sort_order`    INT              NOT NULL DEFAULT 0       COMMENT '排序',
    `is_active`     TINYINT          NOT NULL DEFAULT 1       COMMENT '是否启用：0-否 1-是',
    -- 公共字段
    `created_at`    DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)            COMMENT '创建时间',
    `updated_at`    DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted_at`    DATETIME(3)      DEFAULT NULL             COMMENT '逻辑删除（NULL=未删除）',
    `version`       INT UNSIGNED     NOT NULL DEFAULT 1       COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='机位区域表';
```

#### tariff_plan — 费率方案表

```sql
CREATE TABLE `tariff_plan` (
    `id`                BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `plan_name`         VARCHAR(64)      NOT NULL                 COMMENT '方案名称',
    `plan_type`         TINYINT          NOT NULL                 COMMENT '方案类型：1-普通按时 2-包时 3-混合',
    `is_default`        TINYINT          NOT NULL DEFAULT 0       COMMENT '是否默认方案：0-否 1-是',
    `priority`          INT              NOT NULL DEFAULT 0       COMMENT '优先级（数值高优先匹配）',
    `applicable_areas`  VARCHAR(255)     DEFAULT NULL             COMMENT '适用区域ID列表（JSON）',
    `applicable_levels` VARCHAR(255)     DEFAULT NULL             COMMENT '适用会员等级ID列表（JSON）',
    `effective_days`    VARCHAR(20)      DEFAULT NULL             COMMENT '有效星期（1-7逗号分隔，空=全周）',
    `effective_start`   TIME             DEFAULT NULL             COMMENT '生效时段开始',
    `effective_end`     TIME             DEFAULT NULL             COMMENT '生效时段结束',
    `status`            TINYINT          NOT NULL DEFAULT 1       COMMENT '状态：0-停用 1-启用',
    -- 公共字段
    `created_at`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)            COMMENT '创建时间',
    `updated_at`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted_at`        DATETIME(3)      DEFAULT NULL             COMMENT '逻辑删除（NULL=未删除）',
    `version`           INT UNSIGNED     NOT NULL DEFAULT 1       COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    KEY `idx_plan_type` (`plan_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='费率方案表';
```

#### computer — 终端设备表

```sql
CREATE TABLE `computer` (
    `id`              BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `area_id`         BIGINT UNSIGNED  DEFAULT NULL             COMMENT '所属区域ID → seat_area.id',
    `computer_no`     VARCHAR(16)      NOT NULL                 COMMENT '机位编号，如 A-001',
    `computer_name`   VARCHAR(64)      DEFAULT NULL             COMMENT '设备名称',
    `seat_label`      VARCHAR(32)      DEFAULT NULL             COMMENT '座位标签（靠窗/双人/电竞椅）',
    `device_type`     TINYINT          NOT NULL DEFAULT 1       COMMENT '设备类型：1-普通PC 2-电竞PC 3-包间 4-PS5/主机',
    `cpu`             VARCHAR(64)      DEFAULT NULL             COMMENT 'CPU型号',
    `gpu`             VARCHAR(64)      DEFAULT NULL             COMMENT 'GPU型号',
    `memory`          VARCHAR(32)      DEFAULT NULL             COMMENT '内存规格',
    `screen_size`     VARCHAR(32)      DEFAULT NULL             COMMENT '屏幕尺寸',
    `mac_address`     VARCHAR(17)      DEFAULT NULL             COMMENT 'MAC地址',
    `ip_address`      VARCHAR(15)      DEFAULT NULL             COMMENT 'IP地址',
    `is_online`       TINYINT          NOT NULL DEFAULT 0       COMMENT '是否在线（心跳上报）：0-否 1-是',
    `status`          TINYINT          NOT NULL DEFAULT 0       COMMENT '运营状态：0-空闲 1-使用中 2-锁定 3-维修 4-关机',
    `tariff_plan_id`  BIGINT UNSIGNED  DEFAULT NULL             COMMENT '当前费率方案ID → tariff_plan.id',
    `sort_order`      INT              NOT NULL DEFAULT 0       COMMENT '座位图排序',
    `pos_x`           INT              DEFAULT NULL             COMMENT '座位图X坐标',
    `pos_y`           INT              DEFAULT NULL             COMMENT '座位图Y坐标',
    `is_active`       TINYINT          NOT NULL DEFAULT 1       COMMENT '是否启用：0-否 1-是',
    -- 公共字段
    `created_at`      DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)            COMMENT '创建时间',
    `updated_at`      DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted_at`      DATETIME(3)      DEFAULT NULL             COMMENT '逻辑删除（NULL=未删除）',
    `version`         INT UNSIGNED     NOT NULL DEFAULT 1       COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_computer_no` (`computer_no`),
    KEY `idx_area_id` (`area_id`),
    KEY `idx_status` (`status`),
    KEY `idx_is_online` (`is_online`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='终端设备表';
```

#### session — 上机会话表

```sql
CREATE TABLE `session` (
    `id`                BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `computer_id`       BIGINT UNSIGNED  NOT NULL                 COMMENT '终端ID → computer.id',
    `member_id`         BIGINT UNSIGNED  DEFAULT NULL             COMMENT '会员ID → member.id（NULL表示散客）',
    `session_no`        VARCHAR(32)      NOT NULL                 COMMENT '会话编号',
    `auth_method`       TINYINT          NOT NULL DEFAULT 1       COMMENT '认证方式：1-刷卡 2-前台手动 3-临时密码',
    `tariff_plan_id`    BIGINT UNSIGNED  DEFAULT NULL             COMMENT '适用费率ID → tariff_plan.id',
    `start_time`        DATETIME(3)      NOT NULL                 COMMENT '上机时间',
    `end_time`          DATETIME(3)      DEFAULT NULL             COMMENT '下机时间',
    `expected_minutes`  INT              DEFAULT NULL             COMMENT '预计时长（预约/包时场景）',
    `billed_minutes`    INT              NOT NULL DEFAULT 0       COMMENT '已计费时长（分钟）',
    `free_minutes`      INT              NOT NULL DEFAULT 0       COMMENT '赠送/优惠时长',
    `total_amount`      DECIMAL(12,2)    NOT NULL DEFAULT 0.00    COMMENT '总费用',
    `discount_amount`   DECIMAL(12,2)    NOT NULL DEFAULT 0.00    COMMENT '优惠金额',
    `paid_amount`       DECIMAL(12,2)    NOT NULL DEFAULT 0.00    COMMENT '实付金额',
    `status`            TINYINT          NOT NULL DEFAULT 0       COMMENT '状态：0-上机中 1-临时下机 2-已下机 3-强制下机 4-异常中断',
    `pause_count`       INT              NOT NULL DEFAULT 0       COMMENT '临时下机次数',
    `pause_duration`    INT              NOT NULL DEFAULT 0       COMMENT '临时下机总时长（分钟）',
    `operator_id`       BIGINT UNSIGNED  DEFAULT NULL             COMMENT '操作员工ID → employee.id',
    `remark`            VARCHAR(255)     DEFAULT NULL             COMMENT '备注',
    -- 公共字段
    `created_at`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)            COMMENT '创建时间',
    `updated_at`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted_at`        DATETIME(3)      DEFAULT NULL             COMMENT '逻辑删除（NULL=未删除）',
    `version`           INT UNSIGNED     NOT NULL DEFAULT 1       COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_session_no` (`session_no`),
    KEY `idx_computer_id` (`computer_id`),
    KEY `idx_member_id` (`member_id`),
    KEY `idx_status` (`status`),
    KEY `idx_start_time` (`start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='上机会话表';
```

#### session_timing — 上机计时明细表

```sql
CREATE TABLE `session_timing` (
    `id`                BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `session_id`        BIGINT UNSIGNED  NOT NULL                 COMMENT '所属会话ID → session.id',
    `timing_type`       TINYINT          NOT NULL                 COMMENT '计时类型：1-正常计费 2-临时下机 3-免计费时段',
    `rate_type`         TINYINT          DEFAULT NULL             COMMENT '费率类型：1-按时 2-包时',
    `rate_price`        DECIMAL(10,4)    DEFAULT NULL             COMMENT '当前费率单价（元/分钟）',
    `start_time`        DATETIME(3)      NOT NULL                 COMMENT '段开始时间',
    `end_time`          DATETIME(3)      DEFAULT NULL             COMMENT '段结束时间',
    `duration_minutes`  INT              NOT NULL DEFAULT 0       COMMENT '段时长',
    `amount`            DECIMAL(10,2)    NOT NULL DEFAULT 0.00    COMMENT '段费用',
    -- 公共字段
    `created_at`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)            COMMENT '创建时间',
    `updated_at`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted_at`        DATETIME(3)      DEFAULT NULL             COMMENT '逻辑删除（NULL=未删除）',
    `version`           INT UNSIGNED     NOT NULL DEFAULT 1       COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    KEY `idx_session_id` (`session_id`),
    KEY `idx_timing_type` (`timing_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='上机计时明细表';
```

#### reservation — 预约表

```sql
CREATE TABLE `reservation` (
    `id`                BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `member_id`         BIGINT UNSIGNED  NOT NULL                 COMMENT '会员ID → member.id',
    `computer_id`       BIGINT UNSIGNED  DEFAULT NULL             COMMENT '指定机位ID → computer.id（NULL表示到店分配）',
    `reservation_no`    VARCHAR(32)      NOT NULL                 COMMENT '预约编号',
    `reservation_date`  DATE             NOT NULL                 COMMENT '预约日期',
    `start_time`        DATETIME         NOT NULL                 COMMENT '预约开始时间',
    `end_time`          DATETIME         NOT NULL                 COMMENT '预约结束时间',
    `deposit_amount`    DECIMAL(10,2)    NOT NULL DEFAULT 0.00    COMMENT '预约保证金',
    `status`            TINYINT          NOT NULL DEFAULT 0       COMMENT '状态：0-待确认 1-已确认 2-已上机 3-已取消 4-超时未到',
    `cancel_reason`     VARCHAR(255)     DEFAULT NULL             COMMENT '取消原因',
    `checked_in_at`     DATETIME         DEFAULT NULL             COMMENT '实际到店时间',
    `session_id`        BIGINT UNSIGNED  DEFAULT NULL             COMMENT '关联上机会话ID → session.id',
    -- 公共字段
    `created_at`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)            COMMENT '创建时间',
    `updated_at`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted_at`        DATETIME(3)      DEFAULT NULL             COMMENT '逻辑删除（NULL=未删除）',
    `version`           INT UNSIGNED     NOT NULL DEFAULT 1       COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_reservation_no` (`reservation_no`),
    KEY `idx_reservation_date` (`reservation_date`),
    KEY `idx_member_id` (`member_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='预约表';
```

---

### 2.3 计费域

#### tariff_rate — 费率明细表

```sql
CREATE TABLE `tariff_rate` (
    `id`                BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `plan_id`           BIGINT UNSIGNED  NOT NULL                 COMMENT '所属方案ID → tariff_plan.id',
    `rate_name`         VARCHAR(64)      DEFAULT NULL             COMMENT '费率名称',
    `rate_type`         TINYINT          NOT NULL                 COMMENT '费率类型：1-首价 2-续价 3-包时价',
    `first_minutes`     INT              NOT NULL DEFAULT 0       COMMENT '首段时间（分钟，首价专用）',
    `first_price`       DECIMAL(10,2)    NOT NULL DEFAULT 0.00    COMMENT '首段价格',
    `renewal_price`     DECIMAL(10,4)    NOT NULL DEFAULT 0.00    COMMENT '续价单价（元/分钟）',
    `max_daily_charge`  DECIMAL(10,2)    DEFAULT NULL             COMMENT '每日封顶（NULL=不封顶）',
    `min_charge_minutes` INT             NOT NULL DEFAULT 1       COMMENT '最小计费单位（分钟）',
    `round_rule`        TINYINT          NOT NULL DEFAULT 1       COMMENT '取整规则：1-向上 2-向下 3-四舍五入',
    -- 公共字段
    `created_at`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)            COMMENT '创建时间',
    `updated_at`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted_at`        DATETIME(3)      DEFAULT NULL             COMMENT '逻辑删除（NULL=未删除）',
    `version`           INT UNSIGNED     NOT NULL DEFAULT 1       COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    KEY `idx_plan_id` (`plan_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='费率明细表';
```

#### billing_record — 计费记录表

```sql
CREATE TABLE `billing_record` (
    `id`                  BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `session_id`          BIGINT UNSIGNED  NOT NULL                 COMMENT '上机会话ID → session.id',
    `member_id`           BIGINT UNSIGNED  DEFAULT NULL             COMMENT '会员ID → member.id',
    `computer_id`         BIGINT UNSIGNED  DEFAULT NULL             COMMENT '终端ID → computer.id',
    `record_no`           VARCHAR(32)      DEFAULT NULL             COMMENT '计费流水号',
    `tariff_plan_id`      BIGINT UNSIGNED  DEFAULT NULL             COMMENT '费率方案ID → tariff_plan.id',
    `maintenance_minutes` INT              NOT NULL DEFAULT 0       COMMENT '时间段内计费时长',
    `unit_price`          DECIMAL(10,4)    DEFAULT NULL             COMMENT '时间段内均价',
    `total_amount`        DECIMAL(10,2)    NOT NULL DEFAULT 0.00    COMMENT '费用',
    `discount_amount`     DECIMAL(10,2)    NOT NULL DEFAULT 0.00    COMMENT '优惠金额',
    `final_amount`        DECIMAL(10,2)    NOT NULL DEFAULT 0.00    COMMENT '实收金额',
    `billing_start`       DATETIME(3)      NOT NULL                 COMMENT '计费周期开始',
    `billing_end`         DATETIME(3)      NOT NULL                 COMMENT '计费周期结束',
    `is_settled`          TINYINT          NOT NULL DEFAULT 0       COMMENT '是否已结算：0-否 1-是',
    `settled_at`          DATETIME         DEFAULT NULL             COMMENT '结算时间',
    -- 公共字段
    `created_at`          DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)            COMMENT '创建时间',
    `updated_at`          DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted_at`          DATETIME(3)      DEFAULT NULL             COMMENT '逻辑删除（NULL=未删除）',
    `version`             INT UNSIGNED     NOT NULL DEFAULT 1       COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_record_no` (`record_no`),
    KEY `idx_session_id` (`session_id`),
    KEY `idx_member_id` (`member_id`),
    KEY `idx_is_settled` (`is_settled`),
    KEY `idx_billing_end` (`billing_end`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='计费记录表';
```

---

### 2.4 商品与库存域

#### product_category — 商品分类表

```sql
CREATE TABLE `product_category` (
    `id`            BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `parent_id`     BIGINT UNSIGNED  DEFAULT NULL             COMMENT '父分类ID → product_category.id（NULL=一级分类）',
    `category_name` VARCHAR(32)      NOT NULL                 COMMENT '分类名称',
    `icon`          VARCHAR(64)      DEFAULT NULL             COMMENT '图标',
    `sort_order`    INT              NOT NULL DEFAULT 0       COMMENT '排序',
    `level`         TINYINT          NOT NULL DEFAULT 1       COMMENT '层级',
    `is_active`     TINYINT          NOT NULL DEFAULT 1       COMMENT '是否启用：0-否 1-是',
    -- 公共字段
    `created_at`    DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)            COMMENT '创建时间',
    `updated_at`    DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted_at`    DATETIME(3)      DEFAULT NULL             COMMENT '逻辑删除（NULL=未删除）',
    `version`       INT UNSIGNED     NOT NULL DEFAULT 1       COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品分类表';
```

#### product — 商品表

```sql
CREATE TABLE `product` (
    `id`            BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `category_id`   BIGINT UNSIGNED  DEFAULT NULL             COMMENT '分类ID → product_category.id',
    `product_code`  VARCHAR(32)      DEFAULT NULL             COMMENT '商品编码（条码）',
    `product_name`  VARCHAR(100)     NOT NULL                 COMMENT '商品名称',
    `product_type`  TINYINT          NOT NULL DEFAULT 1       COMMENT '类型：1-食品 2-饮料 3-虚拟商品 4-日用品 5-网游点卡',
    `unit`          VARCHAR(10)      NOT NULL DEFAULT '份'     COMMENT '单位',
    `cost_price`    DECIMAL(10,2)    NOT NULL DEFAULT 0.00    COMMENT '成本价',
    `retail_price`  DECIMAL(10,2)    NOT NULL                 COMMENT '零售价',
    `member_price`  DECIMAL(10,2)    DEFAULT NULL             COMMENT '会员价',
    `image_url`     VARCHAR(255)     DEFAULT NULL             COMMENT '商品图片',
    `is_vip_only`   TINYINT          NOT NULL DEFAULT 0       COMMENT '仅会员购买：0-否 1-是',
    `is_active`     TINYINT          NOT NULL DEFAULT 1       COMMENT '上下架：0-下架 1-上架',
    -- 公共字段
    `created_at`    DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)            COMMENT '创建时间',
    `updated_at`    DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted_at`    DATETIME(3)      DEFAULT NULL             COMMENT '逻辑删除（NULL=未删除）',
    `version`       INT UNSIGNED     NOT NULL DEFAULT 1       COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_code` (`product_code`),
    KEY `idx_category_id` (`category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品表';
```

#### supplier — 供应商表

```sql
CREATE TABLE `supplier` (
    `id`              BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `supplier_code`   VARCHAR(32)      DEFAULT NULL             COMMENT '供应商编码',
    `supplier_name`   VARCHAR(100)     NOT NULL                 COMMENT '供应商名称',
    `contact_person`  VARCHAR(32)      DEFAULT NULL             COMMENT '联系人',
    `contact_phone`   VARCHAR(20)      DEFAULT NULL             COMMENT '联系电话',
    `address`         VARCHAR(200)     DEFAULT NULL             COMMENT '地址',
    `credit_level`    TINYINT          NOT NULL DEFAULT 3       COMMENT '信用等级 1-5',
    `payment_terms`   VARCHAR(100)     DEFAULT NULL             COMMENT '结算方式',
    `is_active`       TINYINT          NOT NULL DEFAULT 1       COMMENT '是否启用：0-否 1-是',
    -- 公共字段
    `created_at`      DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)            COMMENT '创建时间',
    `updated_at`      DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted_at`      DATETIME(3)      DEFAULT NULL             COMMENT '逻辑删除（NULL=未删除）',
    `version`         INT UNSIGNED     NOT NULL DEFAULT 1       COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_supplier_code` (`supplier_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='供应商表';
```

#### inventory — 库存表

```sql
CREATE TABLE `inventory` (
    `id`                 BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `product_id`         BIGINT UNSIGNED  NOT NULL                 COMMENT '商品ID → product.id',
    `batch_no`           VARCHAR(64)      DEFAULT NULL             COMMENT '批次号',
    `quantity`           INT              NOT NULL DEFAULT 0       COMMENT '当前库存量',
    `frozen_quantity`    INT              NOT NULL DEFAULT 0       COMMENT '冻结库存（未完成订单占用）',
    `available_quantity` INT              NOT NULL DEFAULT 0       COMMENT '可用库存（quantity - frozen）',
    `min_stock`          INT              NOT NULL DEFAULT 0       COMMENT '最低库存预警值',
    `max_stock`          INT              NOT NULL DEFAULT 99999   COMMENT '最高库存预警值',
    `production_date`    DATE             DEFAULT NULL             COMMENT '生产日期',
    `expiry_date`        DATE             DEFAULT NULL             COMMENT '过期日期',
    -- 公共字段
    `created_at`         DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)            COMMENT '创建时间',
    `updated_at`         DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted_at`         DATETIME(3)      DEFAULT NULL             COMMENT '逻辑删除（NULL=未删除）',
    `version`            INT UNSIGNED     NOT NULL DEFAULT 1       COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_batch` (`product_id`, `batch_no`),
    KEY `idx_expiry_date` (`expiry_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存表';
```

#### inventory_log — 库存变动日志表

```sql
CREATE TABLE `inventory_log` (
    `id`              BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `product_id`      BIGINT UNSIGNED  NOT NULL                 COMMENT '商品ID → product.id',
    `batch_no`        VARCHAR(64)      DEFAULT NULL             COMMENT '批次号',
    `change_type`     TINYINT          NOT NULL                 COMMENT '变动类型：1-采购入库 2-销售出库 3-盘盈 4-盘亏 5-损耗 6-退货入库',
    `change_quantity` INT              NOT NULL                 COMMENT '变动数量（正=入库，负=出库）',
    `balance_before`  INT              NOT NULL                 COMMENT '变动前数量',
    `balance_after`   INT              NOT NULL                 COMMENT '变动后数量',
    `ref_biz_type`    VARCHAR(32)      DEFAULT NULL             COMMENT '关联业务类型',
    `ref_biz_id`      BIGINT UNSIGNED  DEFAULT NULL             COMMENT '关联业务ID',
    `remark`          VARCHAR(255)     DEFAULT NULL             COMMENT '备注',
    -- 公共字段
    `created_at`      DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)            COMMENT '创建时间',
    `updated_at`      DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted_at`      DATETIME(3)      DEFAULT NULL             COMMENT '逻辑删除（NULL=未删除）',
    `version`         INT UNSIGNED     NOT NULL DEFAULT 1       COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    KEY `idx_product_id` (`product_id`),
    KEY `idx_change_type` (`change_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存变动日志表';
```

#### purchase_order — 采购单

```sql
CREATE TABLE `purchase_order` (
    `id`            BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `supplier_id`   BIGINT UNSIGNED  DEFAULT NULL             COMMENT '供应商ID → supplier.id',
    `order_no`      VARCHAR(32)      DEFAULT NULL             COMMENT '采购单号',
    `total_amount`  DECIMAL(12,2)    NOT NULL DEFAULT 0.00    COMMENT '采购总金额',
    `status`        TINYINT          NOT NULL DEFAULT 0       COMMENT '状态：0-待审核 1-已审核 2-已入库 3-已取消',
    `audit_by`      BIGINT UNSIGNED  DEFAULT NULL             COMMENT '审核人ID → employee.id',
    `audit_at`      DATETIME         DEFAULT NULL             COMMENT '审核时间',
    `remark`        VARCHAR(255)     DEFAULT NULL             COMMENT '备注',
    -- 公共字段
    `created_at`    DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)            COMMENT '创建时间',
    `updated_at`    DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted_at`    DATETIME(3)      DEFAULT NULL             COMMENT '逻辑删除（NULL=未删除）',
    `version`       INT UNSIGNED     NOT NULL DEFAULT 1       COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_supplier_id` (`supplier_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采购单';
```

#### purchase_order_item — 采购明细

```sql
CREATE TABLE `purchase_order_item` (
    `id`                BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `order_id`          BIGINT UNSIGNED  NOT NULL                 COMMENT '采购单ID → purchase_order.id',
    `product_id`        BIGINT UNSIGNED  NOT NULL                 COMMENT '商品ID → product.id',
    `quantity`          INT              NOT NULL                 COMMENT '采购数量',
    `unit_price`        DECIMAL(10,2)    NOT NULL                 COMMENT '采购单价',
    `total_price`       DECIMAL(12,2)    NOT NULL                 COMMENT '小计',
    `received_quantity` INT              NOT NULL DEFAULT 0       COMMENT '已入库数量',
    `remark`            VARCHAR(255)     DEFAULT NULL             COMMENT '备注',
    -- 公共字段
    `created_at`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)            COMMENT '创建时间',
    `updated_at`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted_at`        DATETIME(3)      DEFAULT NULL             COMMENT '逻辑删除（NULL=未删除）',
    `version`           INT UNSIGNED     NOT NULL DEFAULT 1       COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采购明细';
```

---

### 2.5 订单与收银域

#### orders — 订单主表

```sql
CREATE TABLE `orders` (
    `id`                BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `order_no`          VARCHAR(32)      NOT NULL                 COMMENT '订单号',
    `order_type`        TINYINT          NOT NULL DEFAULT 1       COMMENT '订单类型：1-商品销售 2-上机结算 3-充值 4-套餐',
    `member_id`         BIGINT UNSIGNED  DEFAULT NULL             COMMENT '会员ID → member.id（NULL=散客）',
    `session_id`        BIGINT UNSIGNED  DEFAULT NULL             COMMENT '关联上机会话ID → session.id',
    `total_amount`      DECIMAL(12,2)    NOT NULL DEFAULT 0.00    COMMENT '订单总额',
    `discount_amount`   DECIMAL(12,2)    NOT NULL DEFAULT 0.00    COMMENT '优惠金额',
    `payable_amount`    DECIMAL(12,2)    NOT NULL                 COMMENT '应付金额',
    `paid_amount`       DECIMAL(12,2)    NOT NULL DEFAULT 0.00    COMMENT '已付金额',
    `status`            TINYINT          NOT NULL DEFAULT 0       COMMENT '状态：0-待支付 1-已支付 2-部分退款 3-已退款 4-已取消',
    `remark`            VARCHAR(255)     DEFAULT NULL             COMMENT '备注',
    `operator_id`       BIGINT UNSIGNED  DEFAULT NULL             COMMENT '收银员ID → employee.id',
    `paid_at`           DATETIME         DEFAULT NULL             COMMENT '支付完成时间',
    -- 公共字段
    `created_at`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)            COMMENT '创建时间',
    `updated_at`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted_at`        DATETIME(3)      DEFAULT NULL             COMMENT '逻辑删除（NULL=未删除）',
    `version`           INT UNSIGNED     NOT NULL DEFAULT 1       COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_member_id` (`member_id`),
    KEY `idx_status` (`status`),
    KEY `idx_paid_at` (`paid_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单主表';
```

#### order_item — 订单明细表

```sql
CREATE TABLE `order_item` (
    `id`            BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `order_id`      BIGINT UNSIGNED  NOT NULL                 COMMENT '订单ID → orders.id',
    `item_type`     TINYINT          NOT NULL DEFAULT 1       COMMENT '明细类型：1-商品 2-上机时长 3-包时段 4-套餐',
    `product_id`    BIGINT UNSIGNED  DEFAULT NULL             COMMENT '商品ID → product.id（商品类型时关联）',
    `product_name`  VARCHAR(100)     NOT NULL                 COMMENT '快照-商品名称',
    `unit_price`    DECIMAL(10,2)    NOT NULL                 COMMENT '快照-单价',
    `quantity`      INT              NOT NULL DEFAULT 1       COMMENT '数量',
    `subtotal`      DECIMAL(12,2)    NOT NULL                 COMMENT '小计',
    `discount`      DECIMAL(10,2)    NOT NULL DEFAULT 0.00    COMMENT '行优惠分摊',
    -- 公共字段
    `created_at`    DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)            COMMENT '创建时间',
    `updated_at`    DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted_at`    DATETIME(3)      DEFAULT NULL             COMMENT '逻辑删除（NULL=未删除）',
    `version`       INT UNSIGNED     NOT NULL DEFAULT 1       COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单明细表';
```

#### payment_record — 支付记录表

```sql
CREATE TABLE `payment_record` (
    `id`                BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `payment_no`        VARCHAR(32)      NOT NULL                 COMMENT '支付流水号',
    `order_id`          BIGINT UNSIGNED  DEFAULT NULL             COMMENT '关联订单ID → orders.id',
    `member_id`         BIGINT UNSIGNED  DEFAULT NULL             COMMENT '会员ID → member.id',
    `payment_method`    TINYINT          NOT NULL                 COMMENT '支付方式：1-现金 2-会员余额',
    `trade_no`          VARCHAR(64)      DEFAULT NULL             COMMENT '交易流水号（Demo 项目预留）',
    `total_amount`      DECIMAL(12,2)    NOT NULL                 COMMENT '支付总金额',
    `refund_amount`     DECIMAL(12,2)    NOT NULL DEFAULT 0.00    COMMENT '已退款金额',
    `payment_status`    TINYINT          NOT NULL DEFAULT 0       COMMENT '状态：0-待支付 1-支付成功 2-支付失败 3-已退款',
    `paid_at`           DATETIME         DEFAULT NULL             COMMENT '支付完成时间',
    `operator_id`       BIGINT UNSIGNED  DEFAULT NULL             COMMENT '操作人员ID → employee.id',
    `idempotent_key`    VARCHAR(64)      DEFAULT NULL             COMMENT '幂等键（防重复支付）',
    -- 公共字段
    `created_at`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)            COMMENT '创建时间',
    `updated_at`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted_at`        DATETIME(3)      DEFAULT NULL             COMMENT '逻辑删除（NULL=未删除）',
    `version`           INT UNSIGNED     NOT NULL DEFAULT 1       COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_payment_no` (`payment_no`),
    UNIQUE KEY `uk_idempotent_key` (`idempotent_key`),
    UNIQUE KEY `uk_trade_no` (`trade_no`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_member_id` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付记录表';
```

#### refund_record — 退款记录表

```sql
CREATE TABLE `refund_record` (
    `id`              BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `refund_no`       VARCHAR(32)      NOT NULL                 COMMENT '退款单号',
    `order_id`        BIGINT UNSIGNED  NOT NULL                 COMMENT '原订单ID → orders.id',
    `payment_id`      BIGINT UNSIGNED  DEFAULT NULL             COMMENT '原支付记录ID → payment_record.id',
    `member_id`       BIGINT UNSIGNED  DEFAULT NULL             COMMENT '会员ID → member.id',
    `refund_amount`   DECIMAL(12,2)    NOT NULL                 COMMENT '退款金额',
    `refund_type`     TINYINT          NOT NULL                 COMMENT '退款类型：1-全额退款 2-部分退款',
    `refund_reason`   VARCHAR(255)     DEFAULT NULL             COMMENT '退款原因',
    `refund_method`   TINYINT          DEFAULT NULL             COMMENT '退款方式：1-现金退 2-退余额',
    `status`          TINYINT          NOT NULL DEFAULT 0       COMMENT '状态：0-待审核 1-已审核 2-已完成 3-已拒绝',
    `audit_by`        BIGINT UNSIGNED  DEFAULT NULL             COMMENT '审核人ID → employee.id',
    `audit_at`        DATETIME         DEFAULT NULL             COMMENT '审核时间',
    `operator_id`     BIGINT UNSIGNED  DEFAULT NULL             COMMENT '操作人ID → employee.id',
    -- 公共字段
    `created_at`      DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)            COMMENT '创建时间',
    `updated_at`      DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted_at`      DATETIME(3)      DEFAULT NULL             COMMENT '逻辑删除（NULL=未删除）',
    `version`         INT UNSIGNED     NOT NULL DEFAULT 1       COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_refund_no` (`refund_no`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_payment_id` (`payment_id`),
    KEY `idx_member_id` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='退款记录表';
```

#### cashier_shift — 收银班次表

```sql
CREATE TABLE `cashier_shift` (
    `id`                BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `employee_id`       BIGINT UNSIGNED  NOT NULL                 COMMENT '收银员ID → employee.id',
    `shift_no`          VARCHAR(32)      DEFAULT NULL             COMMENT '班次编号',
    `open_at`           DATETIME         NOT NULL                 COMMENT '开班时间',
    `close_at`          DATETIME         DEFAULT NULL             COMMENT '结班时间',
    `opening_balance`   DECIMAL(12,2)    NOT NULL DEFAULT 0.00    COMMENT '开班备用金',
    `cash_income`       DECIMAL(12,2)    NOT NULL DEFAULT 0.00    COMMENT '现金收入',
    `cash_expenditure`  DECIMAL(12,2)    NOT NULL DEFAULT 0.00    COMMENT '现金支出（退款等）',
    `cash_expected`     DECIMAL(12,2)    DEFAULT NULL             COMMENT '预期现金余额',
    `cash_actual`       DECIMAL(12,2)    DEFAULT NULL             COMMENT '实际现金余额',
    `cash_diff`         DECIMAL(12,2)    DEFAULT NULL             COMMENT '长短款金额',
    `total_income`      DECIMAL(14,2)    NOT NULL DEFAULT 0.00    COMMENT '总营收（含所有支付方式）',
    `order_count`       INT              NOT NULL DEFAULT 0       COMMENT '开单笔数',
    `status`            TINYINT          NOT NULL DEFAULT 0       COMMENT '状态：0-进行中 1-已结班 2-已审核',
    `audit_by`          BIGINT UNSIGNED  DEFAULT NULL             COMMENT '审核人ID → employee.id',
    -- 公共字段
    `created_at`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)            COMMENT '创建时间',
    `updated_at`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted_at`        DATETIME(3)      DEFAULT NULL             COMMENT '逻辑删除（NULL=未删除）',
    `version`           INT UNSIGNED     NOT NULL DEFAULT 1       COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_shift_no` (`shift_no`),
    KEY `idx_employee_id` (`employee_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收银班次表';
```

#### daily_settlement — 日结记录表

```sql
CREATE TABLE `daily_settlement` (
    `id`                  BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `settle_date`         DATE             NOT NULL                 COMMENT '营业日期',
    `settle_no`           VARCHAR(32)      DEFAULT NULL             COMMENT '日结编号',
    `total_revenue`       DECIMAL(14,2)    NOT NULL                 COMMENT '总营收',
    `online_revenue`      DECIMAL(14,2)    NOT NULL DEFAULT 0.00    COMMENT '上机收入',
    `product_revenue`     DECIMAL(14,2)    NOT NULL DEFAULT 0.00    COMMENT '商品收入',
    `recharge_revenue`    DECIMAL(14,2)    NOT NULL DEFAULT 0.00    COMMENT '充值收入',
    `total_recharge`      DECIMAL(14,2)    NOT NULL DEFAULT 0.00    COMMENT '充值总额',
    `total_refund`        DECIMAL(14,2)    NOT NULL DEFAULT 0.00    COMMENT '退款总额',
    `total_orders`        INT              NOT NULL DEFAULT 0       COMMENT '总订单数',
    `total_sessions`      INT              NOT NULL DEFAULT 0       COMMENT '上机总次数',
    `peak_concurrent`     INT              NOT NULL DEFAULT 0       COMMENT '当日最高并发',
    `avg_occupancy_rate`  DECIMAL(5,2)     NOT NULL DEFAULT 0.00    COMMENT '平均上机率（%）',
    `cash_amount`         DECIMAL(14,2)    NOT NULL DEFAULT 0.00    COMMENT '现金支付总额',
    `balance_amount`      DECIMAL(14,2)    NOT NULL DEFAULT 0.00    COMMENT '余额支付总额',
    `status`              TINYINT          NOT NULL DEFAULT 0       COMMENT '状态：0-待确认 1-已确认 2-已归档',
    `confirm_by`          BIGINT UNSIGNED  DEFAULT NULL             COMMENT '确认人ID → employee.id',
    `confirmed_at`        DATETIME         DEFAULT NULL             COMMENT '确认时间',
    -- 公共字段
    `created_at`          DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)            COMMENT '创建时间',
    `updated_at`          DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted_at`          DATETIME(3)      DEFAULT NULL             COMMENT '逻辑删除（NULL=未删除）',
    `version`             INT UNSIGNED     NOT NULL DEFAULT 1       COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_settle_date` (`settle_date`),
    UNIQUE KEY `uk_settle_no` (`settle_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='日结记录表';
```

---

### 2.6 员工与权限域

#### employee — 员工表

```sql
CREATE TABLE `employee` (
    `id`                BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `employee_no`       VARCHAR(32)      DEFAULT NULL             COMMENT '工号',
    `real_name`         VARCHAR(32)      NOT NULL                 COMMENT '姓名',
    `phone`             VARCHAR(20)      DEFAULT NULL             COMMENT '手机号',
    `password_hash`     VARCHAR(128)     NOT NULL                 COMMENT '登录密码',
    `position`          VARCHAR(32)      DEFAULT NULL             COMMENT '岗位：店长/收银员/网管/保洁',
    `employment_type`   TINYINT          NOT NULL DEFAULT 1       COMMENT '雇佣类型：1-全职 2-兼职 3-实习',
    `hire_date`         DATE             DEFAULT NULL             COMMENT '入职日期',
    `resign_date`       DATE             DEFAULT NULL             COMMENT '离职日期',
    `status`            TINYINT          NOT NULL DEFAULT 1       COMMENT '状态：1-在职 2-离职 3-停用',
    `is_active`         TINYINT          NOT NULL DEFAULT 1       COMMENT '可登录：0-否 1-是',
    `last_login_at`     DATETIME         DEFAULT NULL             COMMENT '最后登录时间',
    -- 公共字段
    `created_at`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)            COMMENT '创建时间',
    `updated_at`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted_at`        DATETIME(3)      DEFAULT NULL             COMMENT '逻辑删除（NULL=未删除）',
    `version`           INT UNSIGNED     NOT NULL DEFAULT 1       COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_employee_no` (`employee_no`),
    UNIQUE KEY `uk_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='员工表';
```

#### role — 角色表

```sql
CREATE TABLE `role` (
    `id`            BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `role_name`     VARCHAR(32)      NOT NULL                 COMMENT '角色名称',
    `role_code`     VARCHAR(32)      DEFAULT NULL             COMMENT '角色编码（唯一标识）',
    `description`   VARCHAR(255)     DEFAULT NULL             COMMENT '描述',
    `is_system`     TINYINT          NOT NULL DEFAULT 0       COMMENT '是否系统预置：0-否 1-是',
    `sort_order`    INT              NOT NULL DEFAULT 0       COMMENT '排序',
    `is_active`     TINYINT          NOT NULL DEFAULT 1       COMMENT '是否启用：0-否 1-是',
    -- 公共字段
    `created_at`    DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)            COMMENT '创建时间',
    `updated_at`    DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted_at`    DATETIME(3)      DEFAULT NULL             COMMENT '逻辑删除（NULL=未删除）',
    `version`       INT UNSIGNED     NOT NULL DEFAULT 1       COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';
```

#### permission — 权限表

```sql
CREATE TABLE `permission` (
    `id`            BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `parent_id`     BIGINT UNSIGNED  DEFAULT NULL             COMMENT '父权限ID → permission.id',
    `perm_name`     VARCHAR(64)      NOT NULL                 COMMENT '权限名称',
    `perm_code`     VARCHAR(128)     NOT NULL                 COMMENT '权限编码（如 member:create）',
    `perm_type`     TINYINT          NOT NULL DEFAULT 1       COMMENT '类型：1-菜单 2-按钮 3-数据',
    `icon`          VARCHAR(64)      DEFAULT NULL             COMMENT '菜单图标',
    `route`         VARCHAR(128)     DEFAULT NULL             COMMENT '前端路由',
    `sort_order`    INT              NOT NULL DEFAULT 0       COMMENT '排序',
    -- 公共字段
    `created_at`    DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)            COMMENT '创建时间',
    `updated_at`    DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted_at`    DATETIME(3)      DEFAULT NULL             COMMENT '逻辑删除（NULL=未删除）',
    `version`       INT UNSIGNED     NOT NULL DEFAULT 1       COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_perm_code` (`perm_code`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限表';
```

#### role_permission — 角色权限关联表

```sql
CREATE TABLE `role_permission` (
    `id`              BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `role_id`         BIGINT UNSIGNED  NOT NULL                 COMMENT '角色ID → role.id',
    `permission_id`   BIGINT UNSIGNED  NOT NULL                 COMMENT '权限ID → permission.id',
    -- 公共字段
    `created_at`      DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)            COMMENT '创建时间',
    `updated_at`      DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted_at`      DATETIME(3)      DEFAULT NULL             COMMENT '逻辑删除（NULL=未删除）',
    `version`         INT UNSIGNED     NOT NULL DEFAULT 1       COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_perm` (`role_id`, `permission_id`),
    KEY `idx_permission_id` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联表';
```

#### employee_role — 员工角色关联表

```sql
CREATE TABLE `employee_role` (
    `id`            BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `employee_id`   BIGINT UNSIGNED  NOT NULL                 COMMENT '员工ID → employee.id',
    `role_id`       BIGINT UNSIGNED  NOT NULL                 COMMENT '角色ID → role.id',
    -- 公共字段
    `created_at`    DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)            COMMENT '创建时间',
    `updated_at`    DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted_at`    DATETIME(3)      DEFAULT NULL             COMMENT '逻辑删除（NULL=未删除）',
    `version`       INT UNSIGNED     NOT NULL DEFAULT 1       COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_employee_role` (`employee_id`, `role_id`),
    KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='员工角色关联表';
```

#### attendance_record — 考勤记录表

```sql
CREATE TABLE `attendance_record` (
    `id`                    BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `employee_id`           BIGINT UNSIGNED  NOT NULL                 COMMENT '员工ID → employee.id',
    `record_date`           DATE             NOT NULL                 COMMENT '日期',
    `schedule_start`        TIME             DEFAULT NULL             COMMENT '排班开始时间',
    `schedule_end`          TIME             DEFAULT NULL             COMMENT '排班结束时间',
    `clock_in`              DATETIME         DEFAULT NULL             COMMENT '打卡上班时间',
    `clock_out`             DATETIME         DEFAULT NULL             COMMENT '打卡下班时间',
    `status`                TINYINT          NOT NULL DEFAULT 0       COMMENT '状态：0-正常 1-迟到 2-早退 3-缺勤 4-请假',
    `late_minutes`          INT              NOT NULL DEFAULT 0       COMMENT '迟到分钟数',
    `early_leave_minutes`   INT              NOT NULL DEFAULT 0       COMMENT '早退分钟数',
    `work_hours`            DECIMAL(5,2)     NOT NULL DEFAULT 0.00    COMMENT '实际工时',
    `overtime_hours`        DECIMAL(5,2)     NOT NULL DEFAULT 0.00    COMMENT '加班工时',
    `remark`                VARCHAR(255)     DEFAULT NULL             COMMENT '备注',
    -- 公共字段
    `created_at`            DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)            COMMENT '创建时间',
    `updated_at`            DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted_at`            DATETIME(3)      DEFAULT NULL             COMMENT '逻辑删除（NULL=未删除）',
    `version`               INT UNSIGNED     NOT NULL DEFAULT 1       COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_employee_date` (`employee_id`, `record_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='考勤记录表';
```

#### commission_rule — 提成规则表

```sql
CREATE TABLE `commission_rule` (
    `id`                  BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `rule_name`           VARCHAR(64)      NOT NULL                 COMMENT '规则名称',
    `rule_type`           TINYINT          NOT NULL                 COMMENT '提成类型：1-按销售额 2-按开卡数 3-按上机率 4-复合',
    `target_position`     VARCHAR(32)      DEFAULT NULL             COMMENT '适用岗位（空=全部）',
    `calculation_method`  TEXT             DEFAULT NULL             COMMENT '计算公式（JSON表达式）',
    `applicable_start`    DATE             DEFAULT NULL             COMMENT '生效日期',
    `applicable_end`      DATE             DEFAULT NULL             COMMENT '失效日期',
    `is_active`           TINYINT          NOT NULL DEFAULT 1       COMMENT '是否启用：0-否 1-是',
    -- 公共字段
    `created_at`          DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)            COMMENT '创建时间',
    `updated_at`          DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted_at`          DATETIME(3)      DEFAULT NULL             COMMENT '逻辑删除（NULL=未删除）',
    `version`             INT UNSIGNED     NOT NULL DEFAULT 1       COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提成规则表';
```

#### commission_record — 提成记录表

```sql
CREATE TABLE `commission_record` (
    `id`                  BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `employee_id`         BIGINT UNSIGNED  NOT NULL                 COMMENT '员工ID → employee.id',
    `rule_id`             BIGINT UNSIGNED  DEFAULT NULL             COMMENT '提成规则ID → commission_rule.id',
    `settle_date`         DATE             NOT NULL                 COMMENT '结算日期',
    `base_metric`         DECIMAL(14,2)    NOT NULL                 COMMENT '基准指标值',
    `commission_rate`     DECIMAL(5,4)     NOT NULL                 COMMENT '提成比例',
    `commission_amount`   DECIMAL(10,2)    NOT NULL                 COMMENT '提成金额',
    `status`              TINYINT          NOT NULL DEFAULT 0       COMMENT '状态：0-待结算 1-已结算 2-已发放',
    -- 公共字段
    `created_at`          DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)            COMMENT '创建时间',
    `updated_at`          DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted_at`          DATETIME(3)      DEFAULT NULL             COMMENT '逻辑删除（NULL=未删除）',
    `version`             INT UNSIGNED     NOT NULL DEFAULT 1       COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    KEY `idx_employee_id` (`employee_id`),
    KEY `idx_rule_id` (`rule_id`),
    KEY `idx_settle_date` (`settle_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提成记录表';
```

---

### 2.7 营销活动域

#### campaign — 活动表

```sql
CREATE TABLE `campaign` (
    `id`              BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `campaign_no`     VARCHAR(32)      DEFAULT NULL             COMMENT '活动编号',
    `campaign_name`   VARCHAR(100)     NOT NULL                 COMMENT '活动名称',
    `campaign_type`   TINYINT          NOT NULL                 COMMENT '活动类型：1-充值赠送 2-满减优惠 3-限时折扣 4-新人专享 5-生日活动 6-积分兑换',
    `start_time`      DATETIME         NOT NULL                 COMMENT '开始时间',
    `end_time`        DATETIME         NOT NULL                 COMMENT '结束时间',
    `rules`           JSON             NOT NULL                 COMMENT '活动规则（JSON）',
    `budget`          DECIMAL(14,2)    DEFAULT NULL             COMMENT '活动预算',
    `used_budget`     DECIMAL(14,2)    NOT NULL DEFAULT 0.00    COMMENT '已用预算',
    `usage_limit`     INT              DEFAULT NULL             COMMENT '使用次数限制',
    `used_count`      INT              NOT NULL DEFAULT 0       COMMENT '已使用次数',
    `member_limit`    INT              NOT NULL DEFAULT 1       COMMENT '每人限参与次数',
    `status`          TINYINT          NOT NULL DEFAULT 0       COMMENT '状态：0-草稿 1-已发布 2-已生效 3-已结束 4-已下架',
    -- 公共字段
    `created_at`      DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)            COMMENT '创建时间',
    `updated_at`      DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted_at`      DATETIME(3)      DEFAULT NULL             COMMENT '逻辑删除（NULL=未删除）',
    `version`         INT UNSIGNED     NOT NULL DEFAULT 1       COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_campaign_no` (`campaign_no`),
    KEY `idx_time` (`start_time`, `end_time`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='活动表';
```

#### coupon_template — 优惠券模板表

```sql
CREATE TABLE `coupon_template` (
    `id`                    BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `template_name`         VARCHAR(64)      NOT NULL                 COMMENT '模板名称',
    `coupon_type`           TINYINT          NOT NULL                 COMMENT '券类型：1-满减券 2-折扣券 3-现金券 4-时段券',
    `face_value`            DECIMAL(10,2)    NOT NULL                 COMMENT '面值',
    `min_consume`           DECIMAL(10,2)    NOT NULL DEFAULT 0.00    COMMENT '最低消费（满减条件）',
    `discount_rate`         DECIMAL(5,2)     DEFAULT NULL             COMMENT '折扣率（折扣券专用）',
    `valid_days`            INT              DEFAULT NULL             COMMENT '有效天数（从发放算起）',
    `valid_start`           DATE             DEFAULT NULL             COMMENT '固定生效日期',
    `valid_end`             DATE             DEFAULT NULL             COMMENT '固定失效日期',
    `applicable_products`   TEXT             DEFAULT NULL             COMMENT '适用商品ID列表（JSON）',
    `total_quantity`        INT              NOT NULL DEFAULT 0       COMMENT '发行总量（0=不限量）',
    `member_level_limit`    BIGINT UNSIGNED  DEFAULT NULL             COMMENT '会员等级限制 → member_level.id',
    `is_active`             TINYINT          NOT NULL DEFAULT 1       COMMENT '是否启用：0-否 1-是',
    -- 公共字段
    `created_at`            DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)            COMMENT '创建时间',
    `updated_at`            DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted_at`            DATETIME(3)      DEFAULT NULL             COMMENT '逻辑删除（NULL=未删除）',
    `version`               INT UNSIGNED     NOT NULL DEFAULT 1       COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    KEY `idx_member_level_limit` (`member_level_limit`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='优惠券模板表';
```

#### coupon — 优惠券实例表

```sql
CREATE TABLE `coupon` (
    `id`              BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `member_id`       BIGINT UNSIGNED  NOT NULL                 COMMENT '持有会员ID → member.id',
    `template_id`     BIGINT UNSIGNED  NOT NULL                 COMMENT '所属模板ID → coupon_template.id',
    `campaign_id`     BIGINT UNSIGNED  DEFAULT NULL             COMMENT '来源活动ID → campaign.id',
    `coupon_code`     VARCHAR(32)      DEFAULT NULL             COMMENT '券码',
    `face_value`      DECIMAL(10,2)    NOT NULL                 COMMENT '面值',
    `status`          TINYINT          NOT NULL DEFAULT 0       COMMENT '状态：0-未使用 1-已使用 2-已过期 3-已作废',
    `used_at`         DATETIME         DEFAULT NULL             COMMENT '使用时间',
    `used_order_id`   BIGINT UNSIGNED  DEFAULT NULL             COMMENT '使用订单ID → orders.id',
    `expire_at`       DATETIME         NOT NULL                 COMMENT '过期时间',
    -- 公共字段
    `created_at`      DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)            COMMENT '创建时间',
    `updated_at`      DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted_at`      DATETIME(3)      DEFAULT NULL             COMMENT '逻辑删除（NULL=未删除）',
    `version`         INT UNSIGNED     NOT NULL DEFAULT 1       COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_coupon_code` (`coupon_code`),
    KEY `idx_member_id` (`member_id`),
    KEY `idx_template_id` (`template_id`),
    KEY `idx_campaign_id` (`campaign_id`),
    KEY `idx_status` (`status`),
    KEY `idx_expire_at` (`expire_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='优惠券实例表';
```

---

### 2.8 系统与审计域

#### audit_log — 审计日志表

```sql
CREATE TABLE `audit_log` (
    `id`              BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `operator_id`     BIGINT UNSIGNED  DEFAULT NULL             COMMENT '操作人ID → employee.id',
    `operator_name`   VARCHAR(32)      DEFAULT NULL             COMMENT '操作人姓名（防止关联删除追溯）',
    `biz_type`        VARCHAR(32)      NOT NULL                 COMMENT '业务类型：member/computer/order/payment/etc',
    `biz_id`          BIGINT UNSIGNED  DEFAULT NULL             COMMENT '业务ID',
    `action`          VARCHAR(32)      NOT NULL                 COMMENT '操作动作：create/update/delete/audit/login',
    `detail`          JSON             DEFAULT NULL             COMMENT '操作详情：{field: {old: ..., new: ...}}',
    `ip_address`      VARCHAR(15)      DEFAULT NULL             COMMENT '操作IP',
    `user_agent`      VARCHAR(255)     DEFAULT NULL             COMMENT 'User-Agent',
    `request_id`      VARCHAR(64)      DEFAULT NULL             COMMENT '请求追踪ID（全链路）',
    -- 公共字段
    `created_at`      DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)            COMMENT '创建时间',
    `updated_at`      DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted_at`      DATETIME(3)      DEFAULT NULL             COMMENT '逻辑删除（NULL=未删除）',
    `version`         INT UNSIGNED     NOT NULL DEFAULT 1       COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    KEY `idx_biz_type_biz_id` (`biz_type`, `biz_id`),
    KEY `idx_operator_id` (`operator_id`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审计日志表';
```

#### system_config — 系统配置表

```sql
CREATE TABLE `system_config` (
    `id`            BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `config_key`    VARCHAR(64)      NOT NULL                 COMMENT '配置键',
    `config_value`  TEXT             DEFAULT NULL             COMMENT '配置值',
    `config_type`   TINYINT          NOT NULL DEFAULT 1       COMMENT '类型：1-系统参数 2-业务参数 3-界面参数',
    `description`   VARCHAR(255)     DEFAULT NULL             COMMENT '描述',
    `is_encrypted`  TINYINT          NOT NULL DEFAULT 0       COMMENT '是否加密：0-否 1-是',
    -- 公共字段
    `created_at`    DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)            COMMENT '创建时间',
    `updated_at`    DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted_at`    DATETIME(3)      DEFAULT NULL             COMMENT '逻辑删除（NULL=未删除）',
    `version`       INT UNSIGNED     NOT NULL DEFAULT 1       COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';
```

#### notification — 通知表

```sql
CREATE TABLE `notification` (
    `id`            BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `notify_type`   TINYINT          NOT NULL                 COMMENT '类型：1-系统公告 2-员工通知 3-会员推送',
    `title`         VARCHAR(100)     NOT NULL                 COMMENT '标题',
    `content`       TEXT             DEFAULT NULL             COMMENT '内容',
    `target_type`   TINYINT          DEFAULT NULL             COMMENT '目标类型：0-全部 1-指定角色 2-指定员工 3-指定会员',
    `target_ids`    TEXT             DEFAULT NULL             COMMENT '目标ID列表（JSON）',
    `is_read`       TINYINT          NOT NULL DEFAULT 0       COMMENT '是否已读：0-否 1-是',
    `published_at`  DATETIME         DEFAULT NULL             COMMENT '发布时间',
    `expired_at`    DATETIME         DEFAULT NULL             COMMENT '过期时间',
    -- 公共字段
    `created_at`    DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)            COMMENT '创建时间',
    `updated_at`    DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted_at`    DATETIME(3)      DEFAULT NULL             COMMENT '逻辑删除（NULL=未删除）',
    `version`       INT UNSIGNED     NOT NULL DEFAULT 1       COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    KEY `idx_notify_type` (`notify_type`),
    KEY `idx_published_at` (`published_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知表';
```

#### file_meta — 文件元数据表

```sql
CREATE TABLE `file_meta` (
    `id`              BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `original_name`   VARCHAR(255)     NOT NULL                 COMMENT '原始文件名',
    `stored_name`     VARCHAR(255)     NOT NULL                 COMMENT '存储文件名',
    `file_path`       VARCHAR(500)     NOT NULL                 COMMENT '存储路径',
    `file_size`       BIGINT UNSIGNED  NOT NULL                 COMMENT '文件大小（字节）',
    `mime_type`       VARCHAR(100)     DEFAULT NULL             COMMENT 'MIME类型',
    `file_md5`        VARCHAR(64)      DEFAULT NULL             COMMENT '文件校验值',
    `biz_type`        VARCHAR(32)      DEFAULT NULL             COMMENT '关联业务',
    `biz_id`          BIGINT UNSIGNED  DEFAULT NULL             COMMENT '关联业务ID',
    -- 公共字段
    `created_at`      DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)            COMMENT '创建时间',
    `updated_at`      DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted_at`      DATETIME(3)      DEFAULT NULL             COMMENT '逻辑删除（NULL=未删除）',
    `version`         INT UNSIGNED     NOT NULL DEFAULT 1       COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    KEY `idx_biz_type_biz_id` (`biz_type`, `biz_id`),
    KEY `idx_file_md5` (`file_md5`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件元数据表';
```

---

## 3. 外键约束

> 由于表间存在少量循环引用路径（如 `coupon.used_order_id → orders`，而 `orders` 通过 `member_id` 间接关联），建表阶段不声明外键，统一在建表完成后通过 `ALTER TABLE` 添加。
>
> **注意**：生产/Demo 环境中，若对写入性能要求较高，可选择仅在应用层维护引用完整性，不添加物理外键。以下语句按需执行。

```sql
-- ============================================================
-- 会员域外键
-- ============================================================

-- member → member_level
ALTER TABLE `member`
    ADD CONSTRAINT `fk_member_level`
    FOREIGN KEY (`level_id`) REFERENCES `member_level` (`id`)
    ON DELETE SET NULL ON UPDATE CASCADE;

-- member_points_log → member
ALTER TABLE `member_points_log`
    ADD CONSTRAINT `fk_points_log_member`
    FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
    ON DELETE RESTRICT ON UPDATE CASCADE;

-- member_recharge → member
ALTER TABLE `member_recharge`
    ADD CONSTRAINT `fk_recharge_member`
    FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
    ON DELETE RESTRICT ON UPDATE CASCADE;

-- member_recharge → employee
ALTER TABLE `member_recharge`
    ADD CONSTRAINT `fk_recharge_operator`
    FOREIGN KEY (`operator_id`) REFERENCES `employee` (`id`)
    ON DELETE SET NULL ON UPDATE CASCADE;

-- member_recharge → campaign
ALTER TABLE `member_recharge`
    ADD CONSTRAINT `fk_recharge_campaign`
    FOREIGN KEY (`campaign_id`) REFERENCES `campaign` (`id`)
    ON DELETE SET NULL ON UPDATE CASCADE;

-- ============================================================
-- 上机管理域外键
-- ============================================================

-- computer → seat_area
ALTER TABLE `computer`
    ADD CONSTRAINT `fk_computer_area`
    FOREIGN KEY (`area_id`) REFERENCES `seat_area` (`id`)
    ON DELETE SET NULL ON UPDATE CASCADE;

-- computer → tariff_plan
ALTER TABLE `computer`
    ADD CONSTRAINT `fk_computer_tariff`
    FOREIGN KEY (`tariff_plan_id`) REFERENCES `tariff_plan` (`id`)
    ON DELETE SET NULL ON UPDATE CASCADE;

-- session → computer
ALTER TABLE `session`
    ADD CONSTRAINT `fk_session_computer`
    FOREIGN KEY (`computer_id`) REFERENCES `computer` (`id`)
    ON DELETE RESTRICT ON UPDATE CASCADE;

-- session → member
ALTER TABLE `session`
    ADD CONSTRAINT `fk_session_member`
    FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
    ON DELETE SET NULL ON UPDATE CASCADE;

-- session → tariff_plan
ALTER TABLE `session`
    ADD CONSTRAINT `fk_session_tariff`
    FOREIGN KEY (`tariff_plan_id`) REFERENCES `tariff_plan` (`id`)
    ON DELETE SET NULL ON UPDATE CASCADE;

-- session → employee
ALTER TABLE `session`
    ADD CONSTRAINT `fk_session_operator`
    FOREIGN KEY (`operator_id`) REFERENCES `employee` (`id`)
    ON DELETE SET NULL ON UPDATE CASCADE;

-- session_timing → session
ALTER TABLE `session_timing`
    ADD CONSTRAINT `fk_timing_session`
    FOREIGN KEY (`session_id`) REFERENCES `session` (`id`)
    ON DELETE CASCADE ON UPDATE CASCADE;

-- reservation → member
ALTER TABLE `reservation`
    ADD CONSTRAINT `fk_reservation_member`
    FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
    ON DELETE RESTRICT ON UPDATE CASCADE;

-- reservation → computer
ALTER TABLE `reservation`
    ADD CONSTRAINT `fk_reservation_computer`
    FOREIGN KEY (`computer_id`) REFERENCES `computer` (`id`)
    ON DELETE SET NULL ON UPDATE CASCADE;

-- reservation → session
ALTER TABLE `reservation`
    ADD CONSTRAINT `fk_reservation_session`
    FOREIGN KEY (`session_id`) REFERENCES `session` (`id`)
    ON DELETE SET NULL ON UPDATE CASCADE;

-- ============================================================
-- 计费域外键
-- ============================================================

-- tariff_rate → tariff_plan
ALTER TABLE `tariff_rate`
    ADD CONSTRAINT `fk_rate_plan`
    FOREIGN KEY (`plan_id`) REFERENCES `tariff_plan` (`id`)
    ON DELETE CASCADE ON UPDATE CASCADE;

-- billing_record → session
ALTER TABLE `billing_record`
    ADD CONSTRAINT `fk_billing_session`
    FOREIGN KEY (`session_id`) REFERENCES `session` (`id`)
    ON DELETE RESTRICT ON UPDATE CASCADE;

-- billing_record → member
ALTER TABLE `billing_record`
    ADD CONSTRAINT `fk_billing_member`
    FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
    ON DELETE SET NULL ON UPDATE CASCADE;

-- billing_record → computer
ALTER TABLE `billing_record`
    ADD CONSTRAINT `fk_billing_computer`
    FOREIGN KEY (`computer_id`) REFERENCES `computer` (`id`)
    ON DELETE SET NULL ON UPDATE CASCADE;

-- billing_record → tariff_plan
ALTER TABLE `billing_record`
    ADD CONSTRAINT `fk_billing_tariff`
    FOREIGN KEY (`tariff_plan_id`) REFERENCES `tariff_plan` (`id`)
    ON DELETE SET NULL ON UPDATE CASCADE;

-- ============================================================
-- 商品与库存域外键
-- ============================================================

-- product_category 自引用
ALTER TABLE `product_category`
    ADD CONSTRAINT `fk_category_parent`
    FOREIGN KEY (`parent_id`) REFERENCES `product_category` (`id`)
    ON DELETE SET NULL ON UPDATE CASCADE;

-- product → product_category
ALTER TABLE `product`
    ADD CONSTRAINT `fk_product_category`
    FOREIGN KEY (`category_id`) REFERENCES `product_category` (`id`)
    ON DELETE SET NULL ON UPDATE CASCADE;

-- inventory → product
ALTER TABLE `inventory`
    ADD CONSTRAINT `fk_inventory_product`
    FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
    ON DELETE RESTRICT ON UPDATE CASCADE;

-- inventory_log → product
ALTER TABLE `inventory_log`
    ADD CONSTRAINT `fk_inv_log_product`
    FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
    ON DELETE RESTRICT ON UPDATE CASCADE;

-- purchase_order → supplier
ALTER TABLE `purchase_order`
    ADD CONSTRAINT `fk_purchase_supplier`
    FOREIGN KEY (`supplier_id`) REFERENCES `supplier` (`id`)
    ON DELETE SET NULL ON UPDATE CASCADE;

-- purchase_order → employee（审核人）
ALTER TABLE `purchase_order`
    ADD CONSTRAINT `fk_purchase_audit`
    FOREIGN KEY (`audit_by`) REFERENCES `employee` (`id`)
    ON DELETE SET NULL ON UPDATE CASCADE;

-- purchase_order_item → purchase_order
ALTER TABLE `purchase_order_item`
    ADD CONSTRAINT `fk_purchase_item_order`
    FOREIGN KEY (`order_id`) REFERENCES `purchase_order` (`id`)
    ON DELETE CASCADE ON UPDATE CASCADE;

-- purchase_order_item → product
ALTER TABLE `purchase_order_item`
    ADD CONSTRAINT `fk_purchase_item_product`
    FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
    ON DELETE RESTRICT ON UPDATE CASCADE;

-- ============================================================
-- 订单与收银域外键
-- ============================================================

-- orders → member
ALTER TABLE `orders`
    ADD CONSTRAINT `fk_orders_member`
    FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
    ON DELETE SET NULL ON UPDATE CASCADE;

-- orders → session
ALTER TABLE `orders`
    ADD CONSTRAINT `fk_orders_session`
    FOREIGN KEY (`session_id`) REFERENCES `session` (`id`)
    ON DELETE SET NULL ON UPDATE CASCADE;

-- orders → employee
ALTER TABLE `orders`
    ADD CONSTRAINT `fk_orders_operator`
    FOREIGN KEY (`operator_id`) REFERENCES `employee` (`id`)
    ON DELETE SET NULL ON UPDATE CASCADE;

-- order_item → orders
ALTER TABLE `order_item`
    ADD CONSTRAINT `fk_order_item_order`
    FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`)
    ON DELETE CASCADE ON UPDATE CASCADE;

-- order_item → product
ALTER TABLE `order_item`
    ADD CONSTRAINT `fk_order_item_product`
    FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
    ON DELETE SET NULL ON UPDATE CASCADE;

-- payment_record → orders
ALTER TABLE `payment_record`
    ADD CONSTRAINT `fk_payment_order`
    FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`)
    ON DELETE SET NULL ON UPDATE CASCADE;

-- payment_record → member
ALTER TABLE `payment_record`
    ADD CONSTRAINT `fk_payment_member`
    FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
    ON DELETE SET NULL ON UPDATE CASCADE;

-- payment_record → employee
ALTER TABLE `payment_record`
    ADD CONSTRAINT `fk_payment_operator`
    FOREIGN KEY (`operator_id`) REFERENCES `employee` (`id`)
    ON DELETE SET NULL ON UPDATE CASCADE;

-- refund_record → orders
ALTER TABLE `refund_record`
    ADD CONSTRAINT `fk_refund_order`
    FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`)
    ON DELETE RESTRICT ON UPDATE CASCADE;

-- refund_record → payment_record
ALTER TABLE `refund_record`
    ADD CONSTRAINT `fk_refund_payment`
    FOREIGN KEY (`payment_id`) REFERENCES `payment_record` (`id`)
    ON DELETE SET NULL ON UPDATE CASCADE;

-- refund_record → member
ALTER TABLE `refund_record`
    ADD CONSTRAINT `fk_refund_member`
    FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
    ON DELETE SET NULL ON UPDATE CASCADE;

-- refund_record → employee（审核人）
ALTER TABLE `refund_record`
    ADD CONSTRAINT `fk_refund_audit`
    FOREIGN KEY (`audit_by`) REFERENCES `employee` (`id`)
    ON DELETE SET NULL ON UPDATE CASCADE;

-- refund_record → employee（操作人）
ALTER TABLE `refund_record`
    ADD CONSTRAINT `fk_refund_operator`
    FOREIGN KEY (`operator_id`) REFERENCES `employee` (`id`)
    ON DELETE SET NULL ON UPDATE CASCADE;

-- cashier_shift → employee
ALTER TABLE `cashier_shift`
    ADD CONSTRAINT `fk_shift_employee`
    FOREIGN KEY (`employee_id`) REFERENCES `employee` (`id`)
    ON DELETE RESTRICT ON UPDATE CASCADE;

-- cashier_shift → employee（审核人）
ALTER TABLE `cashier_shift`
    ADD CONSTRAINT `fk_shift_audit`
    FOREIGN KEY (`audit_by`) REFERENCES `employee` (`id`)
    ON DELETE SET NULL ON UPDATE CASCADE;

-- daily_settlement → employee
ALTER TABLE `daily_settlement`
    ADD CONSTRAINT `fk_settlement_confirm`
    FOREIGN KEY (`confirm_by`) REFERENCES `employee` (`id`)
    ON DELETE SET NULL ON UPDATE CASCADE;

-- ============================================================
-- 员工与权限域外键
-- ============================================================

-- permission 自引用
ALTER TABLE `permission`
    ADD CONSTRAINT `fk_permission_parent`
    FOREIGN KEY (`parent_id`) REFERENCES `permission` (`id`)
    ON DELETE SET NULL ON UPDATE CASCADE;

-- role_permission → role
ALTER TABLE `role_permission`
    ADD CONSTRAINT `fk_rp_role`
    FOREIGN KEY (`role_id`) REFERENCES `role` (`id`)
    ON DELETE CASCADE ON UPDATE CASCADE;

-- role_permission → permission
ALTER TABLE `role_permission`
    ADD CONSTRAINT `fk_rp_permission`
    FOREIGN KEY (`permission_id`) REFERENCES `permission` (`id`)
    ON DELETE CASCADE ON UPDATE CASCADE;

-- employee_role → employee
ALTER TABLE `employee_role`
    ADD CONSTRAINT `fk_er_employee`
    FOREIGN KEY (`employee_id`) REFERENCES `employee` (`id`)
    ON DELETE CASCADE ON UPDATE CASCADE;

-- employee_role → role
ALTER TABLE `employee_role`
    ADD CONSTRAINT `fk_er_role`
    FOREIGN KEY (`role_id`) REFERENCES `role` (`id`)
    ON DELETE CASCADE ON UPDATE CASCADE;

-- attendance_record → employee
ALTER TABLE `attendance_record`
    ADD CONSTRAINT `fk_attendance_employee`
    FOREIGN KEY (`employee_id`) REFERENCES `employee` (`id`)
    ON DELETE RESTRICT ON UPDATE CASCADE;

-- commission_record → employee
ALTER TABLE `commission_record`
    ADD CONSTRAINT `fk_commission_employee`
    FOREIGN KEY (`employee_id`) REFERENCES `employee` (`id`)
    ON DELETE RESTRICT ON UPDATE CASCADE;

-- commission_record → commission_rule
ALTER TABLE `commission_record`
    ADD CONSTRAINT `fk_commission_rule`
    FOREIGN KEY (`rule_id`) REFERENCES `commission_rule` (`id`)
    ON DELETE SET NULL ON UPDATE CASCADE;

-- ============================================================
-- 营销活动域外键
-- ============================================================

-- coupon_template → member_level
ALTER TABLE `coupon_template`
    ADD CONSTRAINT `fk_coupon_tpl_level`
    FOREIGN KEY (`member_level_limit`) REFERENCES `member_level` (`id`)
    ON DELETE SET NULL ON UPDATE CASCADE;

-- coupon → member
ALTER TABLE `coupon`
    ADD CONSTRAINT `fk_coupon_member`
    FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
    ON DELETE RESTRICT ON UPDATE CASCADE;

-- coupon → coupon_template
ALTER TABLE `coupon`
    ADD CONSTRAINT `fk_coupon_template`
    FOREIGN KEY (`template_id`) REFERENCES `coupon_template` (`id`)
    ON DELETE RESTRICT ON UPDATE CASCADE;

-- coupon → campaign
ALTER TABLE `coupon`
    ADD CONSTRAINT `fk_coupon_campaign`
    FOREIGN KEY (`campaign_id`) REFERENCES `campaign` (`id`)
    ON DELETE SET NULL ON UPDATE CASCADE;

-- coupon → orders
ALTER TABLE `coupon`
    ADD CONSTRAINT `fk_coupon_order`
    FOREIGN KEY (`used_order_id`) REFERENCES `orders` (`id`)
    ON DELETE SET NULL ON UPDATE CASCADE;

-- ============================================================
-- 系统与审计域外键
-- ============================================================

-- audit_log → employee
ALTER TABLE `audit_log`
    ADD CONSTRAINT `fk_audit_operator`
    FOREIGN KEY (`operator_id`) REFERENCES `employee` (`id`)
    ON DELETE SET NULL ON UPDATE CASCADE;
```

---

## 4. 收尾

```sql
-- ============================================================
-- 恢复外键检查
-- ============================================================
SET FOREIGN_KEY_CHECKS = 1;
```

---

## 附录：建表总览

| 序号 | 表名 | 说明 | 所属域 |
|:---:|------|------|--------|
| 1 | member_level | 会员等级表 | 会员 |
| 2 | member | 会员主表 | 会员 |
| 3 | member_points_log | 积分流水表 | 会员 |
| 4 | member_recharge | 充值记录表 | 会员 |
| 5 | seat_area | 机位区域表 | 上机管理 |
| 6 | tariff_plan | 费率方案表 | 上机管理 |
| 7 | computer | 终端设备表 | 上机管理 |
| 8 | session | 上机会话表 | 上机管理 |
| 9 | session_timing | 上机计时明细表 | 上机管理 |
| 10 | reservation | 预约表 | 上机管理 |
| 11 | tariff_rate | 费率明细表 | 计费 |
| 12 | billing_record | 计费记录表 | 计费 |
| 13 | product_category | 商品分类表 | 商品与库存 |
| 14 | product | 商品表 | 商品与库存 |
| 15 | supplier | 供应商表 | 商品与库存 |
| 16 | inventory | 库存表 | 商品与库存 |
| 17 | inventory_log | 库存变动日志表 | 商品与库存 |
| 18 | purchase_order | 采购单 | 商品与库存 |
| 19 | purchase_order_item | 采购明细 | 商品与库存 |
| 20 | orders | 订单主表 | 订单与收银 |
| 21 | order_item | 订单明细表 | 订单与收银 |
| 22 | payment_record | 支付记录表 | 订单与收银 |
| 23 | refund_record | 退款记录表 | 订单与收银 |
| 24 | cashier_shift | 收银班次表 | 订单与收银 |
| 25 | daily_settlement | 日结记录表 | 订单与收银 |
| 26 | employee | 员工表 | 员工与权限 |
| 27 | role | 角色表 | 员工与权限 |
| 28 | permission | 权限表 | 员工与权限 |
| 29 | role_permission | 角色权限关联表 | 员工与权限 |
| 30 | employee_role | 员工角色关联表 | 员工与权限 |
| 31 | attendance_record | 考勤记录表 | 员工与权限 |
| 32 | commission_rule | 提成规则表 | 员工与权限 |
| 33 | commission_record | 提成记录表 | 员工与权限 |
| 34 | campaign | 活动表 | 营销活动 |
| 35 | coupon_template | 优惠券模板表 | 营销活动 |
| 36 | coupon | 优惠券实例表 | 营销活动 |
| 37 | audit_log | 审计日志表 | 系统与审计 |
| 38 | system_config | 系统配置表 | 系统与审计 |
| 39 | notification | 通知表 | 系统与审计 |
| 40 | file_meta | 文件元数据表 | 系统与审计 |

---

*文档版本：v2.0（Demo 单店版）*
*最后更新：2026-07-12*
*基于 database-design.md v2.0 生成*
