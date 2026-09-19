# 星络灵侍馆 — 数据库设计说明书

> 文档版本：v2.0（Demo 单店版） | 最后更新：2026-07-12 | 基于需求文档《需求文档.md》v1.0

---

## 1. 设计约定

### 1.1 命名规范

| 项 | 规范 |
|---|------|
| 表名 | `{领域}_{实体}`，全部小写，下划线分隔，如 `member_account` |
| 字段名 | 小写 + 下划线，主键统一为 `id`，外键为 `{目标表}_id` |
| 索引 | `idx_{表名}_{字段}`，唯一索引 `uk_{表名}_{字段}` |
| 字符集 | `utf8mb4`，排序规则 `utf8mb4_unicode_ci` |
| 引擎 | InnoDB（支持事务与外键） |

### 1.2 公共字段规范

所有表统一包含以下基础字段：

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint unsigned | PK, AUTO_INCREMENT | 主键 |
| created_at | datetime(3) | NOT NULL, DEFAULT CURRENT_TIMESTAMP(3) | 创建时间（毫秒精度） |
| updated_at | datetime(3) | NOT NULL, DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) | 更新时间 |
| deleted_at | datetime(3) | DEFAULT NULL | 逻辑删除（NULL = 未删除） |
| version | int unsigned | NOT NULL, DEFAULT 1 | 乐观锁版本号 |

### 1.3 全局索引策略

- 所有外键字段 `{table}_id` 自动创建索引
- 所有 `deleted_at` 字段建议创建部分索引 `WHERE deleted_at IS NULL`（InnoDB 支持）
- 时间范围查询字段（`created_at` 等）按业务需求创建索引
- 状态枚举字段（`status`）如区分度较高则加索引，区分度低（如布尔型）则不加

### 1.4 实体关系图（概览）

```
┌─────────────┐       ┌──────────────┐
│  seat_area  │──1:N──│  computer    │
└─────────────┘       └──────┬───────┘
                              │
                              │1:N
                              ▼
┌───────────┐       ┌─────────────┐       ┌──────────────┐
│ employee  │       │   session   │──1:N──│ billing_record│
└───────────┘       └──────┬──────┘       └──────────────┘
       │                    │
       │1:N                 │1:1
       ▼                    ▼
┌───────────┐       ┌─────────────┐
│ attendance│       │   member    │────1:N─── recharge
│_record    │       └──────┬──────┘
└───────────┘              │
                           │1:N
                           ▼
                    ┌─────────────┐       ┌──────────────┐
                    │   orders    │──1:N──│  order_item  │
                    └──────┬──────┘       └──────────────┘
                           │1:N
                           ▼
                    ┌─────────────┐
                    │payment_record│
                    └─────────────┘
```

---

## 2. 会员

### 2.1 member — 会员主表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | |
| member_no | varchar(32) | UK, NOT NULL | 会员编号（自动生成） |
| real_name | varchar(32) | | 真实姓名 |
| gender | tinyint | DEFAULT 0 | 性别：0-未知 1-男 2-女 |
| phone | varchar(20) | UK, NOT NULL | 手机号（登录账号） |
| id_card | varchar(18) | | 身份证号（加密） |
| id_card_hash | varchar(64) | | 身份证哈希（用于去重） |
| birthday | date | | 出生日期 |
| level_id | bigint | FK → member_level.id | 当前等级 |
| total_points | bigint | NOT NULL, DEFAULT 0 | 累计获得积分 |
| available_points | bigint | NOT NULL, DEFAULT 0 | 可用积分 |
| total_recharge | decimal(12,2) | NOT NULL, DEFAULT 0.00 | 累计充值金额 |
| balance | decimal(12,2) | NOT NULL, DEFAULT 0.00 | 账户余额 |
| total_consumption | decimal(12,2) | NOT NULL, DEFAULT 0.00 | 累计消费金额 |
| last_login_time | datetime | | 最后登录时间 |
| last_online_time | datetime | | 最后上机时间 |
| total_online_hours | int | DEFAULT 0 | 累计上机时长（分钟） |
| face_feature | varchar(255) | | 人脸特征向量（密文） |
| password_hash | varchar(128) | NOT NULL | 登录密码哈希 |
| register_source | tinyint | DEFAULT 1 | 注册来源：1-前台 2-后台导入 |
| status | tinyint | NOT NULL, DEFAULT 1 | 状态：1-正常 2-冻结 3-黑名单 |
| blacklist_reason | varchar(255) | | 黑名单原因 |
| tag | varchar(255) | | 标签（JSON 数组，用于画像） |
| ... | | | 公共字段 |

索引：`uk_phone`, `uk_member_no`, `idx_level_id`, `id_idx_card_hash`, `idx_status`
备注：`balance` 字段的所有变更必须通过流水表归档，禁止直接 UPDATE。

### 2.2 member_level — 会员等级表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | |
| level_name | varchar(32) | NOT NULL | 等级名称：普通/银卡/金卡/钻石 |
| level_order | tinyint | NOT NULL | 排序序号（数值越大等级越高） |
| min_growth | int | NOT NULL, DEFAULT 0 | 成长值下限 |
| max_growth | int | NOT NULL | 成长值上限 |
| discount_rate | decimal(5,2) | DEFAULT 100.00 | 折扣率（%），100=无折扣 |
| hourly_discount | decimal(5,2) | DEFAULT 100.00 | 上机折扣率（%） |
| recharge_bonus_rate | decimal(5,2) | DEFAULT 0.00 | 充值赠送比例（%） |
| points_multiple | decimal(5,2) | DEFAULT 1.00 | 积分获取倍数 |
| auto_upgrade | tinyint | DEFAULT 1 | 是否自动升级 |
| icon_url | varchar(255) | | 等级图标 |
| ... | | | 公共字段 |

### 2.3 member_points_log — 积分流水表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | |
| member_id | bigint | FK → member.id, NOT NULL | 会员 |
| points | int | NOT NULL | 变动积分（正=增加，负=消耗） |
| balance_before | int | NOT NULL | 变动前积分 |
| balance_after | int | NOT NULL | 变动后积分 |
| biz_type | tinyint | NOT NULL | 业务类型：1-消费获得 2-充值赠送 3-兑换消耗 4-活动奖励 5-手动调整 6-过期扣除 |
| biz_id | bigint | | 关联业务 ID |
| remark | varchar(255) | | 备注 |
| expire_at | datetime | | 过期时间 |
| ... | | | 公共字段 |

索引：`idx_member_id`, `idx_biz_type`, `idx_created_at`

### 2.4 member_recharge — 充值记录表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | |
| member_id | bigint | FK → member.id, NOT NULL | 会员 |
| recharge_no | varchar(32) | UK, NOT NULL | 充值单号 |
| recharge_amount | decimal(12,2) | NOT NULL | 充值金额（实付） |
| bonus_amount | decimal(12,2) | NOT NULL, DEFAULT 0.00 | 赠送金额 |
| total_amount | decimal(12,2) | NOT NULL | 到账总额 = 充值 + 赠送 |
| balance_before | decimal(12,2) | NOT NULL | 充值前余额 |
| balance_after | decimal(12,2) | NOT NULL | 充值后余额 |
| payment_method | tinyint | NOT NULL | 支付方式：1-现金 2-会员余额 |
| payment_channel | varchar(32) | | 支付通道（Demo 项目预留） |
| trade_no | varchar(64) | | 交易流水号（Demo 项目预留） |
| operator_id | bigint | FK → employee.id | 操作员工（自助为 NULL） |
| campaign_id | bigint | FK → campaign.id | 关联活动（如有） |
| status | tinyint | NOT NULL, DEFAULT 0 | 状态：0-待支付 1-成功 2-失败 3-已退款 |
| paid_at | datetime | | 支付完成时间 |
| ... | | | 公共字段 |

索引：`uk_recharge_no`, `idx_member_id`, `idx_paid_at`, `idx_operator_id`

---

## 3. 上机管理

### 3.1 seat_area — 机位区域表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | |
| area_name | varchar(32) | NOT NULL | 区域名称：如 A区/电竞区/包间 |
| area_color | varchar(7) | DEFAULT '#1890ff' | 区域标识色（十六进制） |
| sort_order | int | DEFAULT 0 | 排序 |
| is_active | tinyint | DEFAULT 1 | 是否启用 |
| ... | | | 公共字段 |

### 3.2 computer — 终端设备表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | |
| area_id | bigint | FK → seat_area.id | 所属区域 |
| computer_no | varchar(16) | UK, NOT NULL | 机位编号，如 A-001 |
| computer_name | varchar(64) | | 设备名称 |
| seat_label | varchar(32) | | 座位标签（靠窗/双人/电竞椅） |
| device_type | tinyint | DEFAULT 1 | 设备类型：1-普通 PC 2-电竞 PC 3-包间 4-PS5/主机 |
| cpu | varchar(64) | | |
| gpu | varchar(64) | | |
| memory | varchar(32) | | |
| screen_size | varchar(32) | | 屏幕尺寸 |
| mac_address | varchar(17) | | MAC 地址 |
| ip_address | varchar(15) | | IP 地址 |
| is_online | tinyint | DEFAULT 0 | 是否在线（心跳上报） |
| status | tinyint | NOT NULL, DEFAULT 0 | 运营状态：0-空闲 1-使用中 2-锁定 3-维修 4-关机 |
| tariff_plan_id | bigint | FK → tariff_plan.id | 当前费率方案 |
| sort_order | int | DEFAULT 0 | 座位图排序 |
| pos_x | int | | 座位图 X 坐标 |
| pos_y | int | | 座位图 Y 坐标 |
| is_active | tinyint | DEFAULT 1 | 是否启用 |
| ... | | | 公共字段 |

索引：`uk_computer_no`, `idx_area_id`, `idx_status`, `idx_is_online`

### 3.3 session — 上机会话表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | |
| computer_id | bigint | FK → computer.id, NOT NULL | 终端 |
| member_id | bigint | FK → member.id | 会员（NULL 表示散客） |
| session_no | varchar(32) | UK, NOT NULL | 会话编号 |
| auth_method | tinyint | DEFAULT 1 | 认证方式：1-刷卡 2-扫码 3-人脸 4-临时密码 |
| tariff_plan_id | bigint | FK → tariff_plan.id | 适用费率 |
| start_time | datetime(3) | NOT NULL | 上机时间 |
| end_time | datetime(3) | | 下机时间 |
| expected_minutes | int | | 预计时长（预约/包时场景） |
| billed_minutes | int | DEFAULT 0 | 已计费时长（分钟） |
| free_minutes | int | DEFAULT 0 | 赠送/优惠时长 |
| total_amount | decimal(12,2) | DEFAULT 0.00 | 总费用 |
| discount_amount | decimal(12,2) | DEFAULT 0.00 | 优惠金额 |
| paid_amount | decimal(12,2) | DEFAULT 0.00 | 实付金额 |
| status | tinyint | NOT NULL, DEFAULT 0 | 状态：0-上机中 1-临时下机 2-已下机 3-强制下机 4-异常中断 |
| pause_count | int | DEFAULT 0 | 临时下机次数 |
| pause_duration | int | DEFAULT 0 | 临时下机总时长（分钟） |
| operator_id | bigint | FK → employee.id | 操作员工 |
| remark | varchar(255) | | 备注 |
| ... | | | 公共字段 |

索引：`uk_session_no`, `idx_computer_id`, `idx_member_id`, `idx_status`, `idx_start_time`

### 3.4 session_timing — 上机计时明细表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | |
| session_id | bigint | FK → session.id, NOT NULL | 所属会话 |
| timing_type | tinyint | NOT NULL | 计时类型：1-正常计费 2-临时下机 3-免计费时段 |
| rate_type | tinyint | | 费率类型：1-按时 2-包时 |
| rate_price | decimal(10,4) | | 当前费率单价（元/分钟） |
| start_time | datetime(3) | NOT NULL | 段开始时间 |
| end_time | datetime(3) | | 段结束时间 |
| duration_minutes | int | DEFAULT 0 | 段时长 |
| amount | decimal(10,2) | DEFAULT 0.00 | 段费用 |
| ... | | | 公共字段 |

索引：`idx_session_id`, `idx_timing_type`

### 3.5 reservation — 预约表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | |
| member_id | bigint | FK → member.id, NOT NULL | |
| computer_id | bigint | FK → computer.id | 指定机位（NULL 表示到店分配） |
| reservation_no | varchar(32) | UK, NOT NULL | 预约编号 |
| reservation_date | date | NOT NULL | 预约日期 |
| start_time | datetime | NOT NULL | 预约开始时间 |
| end_time | datetime | NOT NULL | 预约结束时间 |
| deposit_amount | decimal(10,2) | DEFAULT 0.00 | 预约保证金 |
| status | tinyint | NOT NULL, DEFAULT 0 | 状态：0-待确认 1-已确认 2-已上机 3-已取消 4-超时未到 |
| cancel_reason | varchar(255) | | 取消原因 |
| checked_in_at | datetime | | 实际到店时间 |
| session_id | bigint | FK → session.id | 关联上机会话 |
| ... | | | 公共字段 |

索引：`idx_reservation_date`, `idx_member_id`, `idx_status`

---

## 4. 计费

### 4.1 tariff_plan — 费率方案表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | |
| plan_name | varchar(64) | NOT NULL | 方案名称 |
| plan_type | tinyint | NOT NULL | 方案类型：1-普通按时 2-包时 3-混合 |
| is_default | tinyint | DEFAULT 0 | 是否默认方案 |
| priority | int | DEFAULT 0 | 优先级（数值高优先匹配） |
| applicable_areas | varchar(255) | | 适用区域 ID 列表（JSON） |
| applicable_levels | varchar(255) | | 适用会员等级 ID 列表（JSON） |
| effective_days | varchar(20) | | 有效星期（1-7 逗号分隔，空=全周） |
| effective_start | time | | 生效时段开始 |
| effective_end | time | | 生效时段结束 |
| status | tinyint | DEFAULT 1 | 状态：0-停用 1-启用 |
| ... | | | 公共字段 |

索引：`idx_plan_type`

### 4.2 tariff_rate — 费率明细表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | |
| plan_id | bigint | FK → tariff_plan.id, NOT NULL | 所属方案 |
| rate_name | varchar(64) | | 费率名称 |
| rate_type | tinyint | NOT NULL | 费率类型：1-首价 2-续价 3-包时价 |
| first_minutes | int | DEFAULT 0 | 首段时间（分钟，首价专用） |
| first_price | decimal(10,2) | DEFAULT 0.00 | 首段价格 |
| renewal_price | decimal(10,4) | DEFAULT 0.00 | 续价单价（元/分钟） |
| max_daily_charge | decimal(10,2) | | 每日封顶（NULL=不封顶） |
| min_charge_minutes | int | DEFAULT 1 | 最小计费单位（分钟） |
| round_rule | tinyint | DEFAULT 1 | 取整规则：1-向上 2-向下 3-四舍五入 |
| ... | | | 公共字段 |

索引：`idx_plan_id`

### 4.3 billing_record — 计费记录表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | |
| session_id | bigint | FK → session.id, NOT NULL | 上机会话 |
| member_id | bigint | FK → member.id | 会员 |
| computer_id | bigint | FK → computer.id | 终端 |
| record_no | varchar(32) | UK | 计费流水号 |
| tariff_plan_id | bigint | FK → tariff_plan.id | 费率方案 |
| maintenance_minutes | int | DEFAULT 0 | 时间段内计费时长 |
| unit_price | decimal(10,4) | | 时间段内均价 |
| total_amount | decimal(10,2) | DEFAULT 0.00 | 费用 |
| discount_amount | decimal(10,2) | DEFAULT 0.00 | 优惠金额 |
| final_amount | decimal(10,2) | DEFAULT 0.00 | 实收金额 |
| billing_start | datetime(3) | NOT NULL | 计费周期开始 |
| billing_end | datetime(3) | NOT NULL | 计费周期结束 |
| is_settled | tinyint | DEFAULT 0 | 是否已结算 |
| settled_at | datetime | | 结算时间 |
| ... | | | 公共字段 |

索引：`idx_session_id`, `idx_member_id`, `idx_is_settled`, `idx_billing_end`

---

## 5. 商品与库存

### 5.1 product_category — 商品分类表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | |
| parent_id | bigint | FK → product_category.id | 父分类（NULL=一级分类） |
| category_name | varchar(32) | NOT NULL | 分类名称 |
| icon | varchar(64) | | 图标 |
| sort_order | int | DEFAULT 0 | 排序 |
| level | tinyint | DEFAULT 1 | 层级 |
| is_active | tinyint | DEFAULT 1 | 是否启用 |
| ... | | | 公共字段 |

### 5.2 product — 商品表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | |
| category_id | bigint | FK → product_category.id | 分类 |
| product_code | varchar(32) | UK | 商品编码（条码） |
| product_name | varchar(100) | NOT NULL | 商品名称 |
| product_type | tinyint | DEFAULT 1 | 类型：1-食品 2-饮料 3-虚拟商品 4-日用品 5-网游点卡 |
| unit | varchar(10) | DEFAULT '份' | 单位 |
| cost_price | decimal(10,2) | DEFAULT 0.00 | 成本价 |
| retail_price | decimal(10,2) | NOT NULL | 零售价 |
| member_price | decimal(10,2) | | 会员价 |
| image_url | varchar(255) | | 商品图片 |
| is_vip_only | tinyint | DEFAULT 0 | 仅会员购买 |
| is_active | tinyint | DEFAULT 1 | 上下架 |
| ... | | | 公共字段 |

索引：`uk_product_code`, `idx_category_id`

### 5.3 supplier — 供应商表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | |
| supplier_code | varchar(32) | UK | 供应商编码 |
| supplier_name | varchar(100) | NOT NULL | 供应商名称 |
| contact_person | varchar(32) | | 联系人 |
| contact_phone | varchar(20) | | 联系电话 |
| address | varchar(200) | | 地址 |
| credit_level | tinyint | DEFAULT 3 | 信用等级 1-5 |
| payment_terms | varchar(100) | | 结算方式 |
| is_active | tinyint | DEFAULT 1 | |
| ... | | | 公共字段 |

### 5.4 inventory — 库存表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | |
| product_id | bigint | FK → product.id, NOT NULL | 商品 |
| batch_no | varchar(64) | | 批次号 |
| quantity | int | NOT NULL, DEFAULT 0 | 当前库存量 |
| frozen_quantity | int | DEFAULT 0 | 冻结库存（未完成订单占用） |
| available_quantity | int | DEFAULT 0 | 可用库存（quantity - frozen） |
| min_stock | int | DEFAULT 0 | 最低库存预警值 |
| max_stock | int | DEFAULT 99999 | 最高库存预警值 |
| production_date | date | | 生产日期 |
| expiry_date | date | | 过期日期 |
| ... | | | 公共字段 |

索引：`uk_product_batch`(product_id, batch_no), `idx_expiry_date`

### 5.5 inventory_log — 库存变动日志表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | |
| product_id | bigint | FK → product.id, NOT NULL | |
| batch_no | varchar(64) | | |
| change_type | tinyint | NOT NULL | 变动类型：1-采购入库 2-销售出库 3-盘盈 4-盘亏 5-损耗 6-退货入库 |
| change_quantity | int | NOT NULL | 变动数量（正=入库，负=出库） |
| balance_before | int | NOT NULL | 变动前数量 |
| balance_after | int | NOT NULL | 变动后数量 |
| ref_biz_type | varchar(32) | | 关联业务类型 |
| ref_biz_id | bigint | | 关联业务 ID |
| remark | varchar(255) | | |
| ... | | | 公共字段 |

索引：`idx_product_id`, `idx_change_type`

### 5.6 purchase_order — 采购单

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | |
| supplier_id | bigint | FK → supplier.id | 供应商 |
| order_no | varchar(32) | UK | 采购单号 |
| total_amount | decimal(12,2) | DEFAULT 0.00 | 采购总金额 |
| status | tinyint | NOT NULL, DEFAULT 0 | 状态：0-待审核 1-已审核 2-已入库 3-已取消 |
| audit_by | bigint | FK → employee.id | 审核人 |
| audit_at | datetime | | 审核时间 |
| remark | varchar(255) | | |
| ... | | | 公共字段 |

### 5.7 purchase_order_item — 采购明细

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | |
| order_id | bigint | FK → purchase_order.id, NOT NULL | |
| product_id | bigint | FK → product.id, NOT NULL | |
| quantity | int | NOT NULL | 采购数量 |
| unit_price | decimal(10,2) | NOT NULL | 采购单价 |
| total_price | decimal(12,2) | NOT NULL | 小计 |
| received_quantity | int | DEFAULT 0 | 已入库数量 |
| remark | varchar(255) | | |

---

## 6. 订单与收银

### 6.1 orders — 订单主表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | |
| order_no | varchar(32) | UK, NOT NULL | 订单号 |
| order_type | tinyint | NOT NULL, DEFAULT 1 | 订单类型：1-商品销售 2-上机结算 3-充值 4-套餐 |
| member_id | bigint | FK → member.id | 会员（NULL=散客） |
| session_id | bigint | FK → session.id | 关联上机会话 |
| total_amount | decimal(12,2) | NOT NULL, DEFAULT 0.00 | 订单总额 |
| discount_amount | decimal(12,2) | DEFAULT 0.00 | 优惠金额 |
| payable_amount | decimal(12,2) | NOT NULL | 应付金额 |
| paid_amount | decimal(12,2) | DEFAULT 0.00 | 已付金额 |
| status | tinyint | NOT NULL, DEFAULT 0 | 状态：0-待支付 1-已支付 2-部分退款 3-已退款 4-已取消 |
| remark | varchar(255) | | |
| operator_id | bigint | FK → employee.id | 收银员 |
| paid_at | datetime | | 支付完成时间 |
| ... | | | 公共字段 |

索引：`uk_order_no`, `idx_member_id`, `idx_status`, `idx_paid_at`

### 6.2 order_item — 订单明细表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | |
| order_id | bigint | FK → orders.id, NOT NULL | |
| item_type | tinyint | NOT NULL, DEFAULT 1 | 明细类型：1-商品 2-上机时长 3-包时段 4-套餐 |
| product_id | bigint | FK → product.id | 商品（商品类型时关联） |
| product_name | varchar(100) | NOT NULL | 快照-商品名称 |
| unit_price | decimal(10,2) | NOT NULL | 快照-单价 |
| quantity | int | NOT NULL, DEFAULT 1 | 数量 |
| subtotal | decimal(12,2) | NOT NULL | 小计 |
| discount | decimal(10,2) | DEFAULT 0.00 | 行优惠分摊 |
| ... | | | 公共字段 |

索引：`idx_order_id`

### 6.3 payment_record — 支付记录表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | |
| payment_no | varchar(32) | UK, NOT NULL | 支付流水号 |
| order_id | bigint | FK → orders.id | 关联订单 |
| member_id | bigint | FK → member.id | 会员 |
| payment_method | tinyint | NOT NULL | 支付方式：1-现金 2-会员余额 |
| trade_no | varchar(64) | | 交易流水号（Demo 项目预留） |
| total_amount | decimal(12,2) | NOT NULL | 支付总金额 |
| refund_amount | decimal(12,2) | DEFAULT 0.00 | 已退款金额 |
| payment_status | tinyint | NOT NULL, DEFAULT 0 | 状态：0-待支付 1-支付成功 2-支付失败 3-已退款 |
| paid_at | datetime | | 支付完成时间 |
| operator_id | bigint | FK → employee.id | 操作人员 |
| idempotent_key | varchar(64) | UK | 幂等键（防重复支付） |
| ... | | | 公共字段 |

索引：`uk_idempotent_key`, `uk_trade_no`, `idx_order_id`, `idx_member_id`

### 6.4 refund_record — 退款记录表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | |
| refund_no | varchar(32) | UK, NOT NULL | 退款单号 |
| order_id | bigint | FK → orders.id, NOT NULL | 原订单 |
| payment_id | bigint | FK → payment_record.id | 原支付记录 |
| member_id | bigint | FK → member.id | |
| refund_amount | decimal(12,2) | NOT NULL | 退款金额 |
| refund_type | tinyint | NOT NULL | 退款类型：1-全额退款 2-部分退款 |
| refund_reason | varchar(255) | | 退款原因 |
| refund_method | tinyint | | 退款方式：1-原路退回 2-现金退 3-退余额 |
| status | tinyint | DEFAULT 0 | 状态：0-待审核 1-已审核 2-已完成 3-已拒绝 |
| audit_by | bigint | FK → employee.id | 审核人 |
| audit_at | datetime | | |
| operator_id | bigint | FK → employee.id | 操作人 |
| ... | | | 公共字段 |

### 6.5 cashier_shift — 收银班次表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | |
| employee_id | bigint | FK → employee.id, NOT NULL | 收银员 |
| shift_no | varchar(32) | UK | 班次编号 |
| open_at | datetime | NOT NULL | 开班时间 |
| close_at | datetime | | 结班时间 |
| opening_balance | decimal(12,2) | DEFAULT 0.00 | 开班备用金 |
| cash_income | decimal(12,2) | DEFAULT 0.00 | 现金收入 |
| cash_expenditure | decimal(12,2) | DEFAULT 0.00 | 现金支出（退款等） |
| cash_expected | decimal(12,2) | | 预期现金余额 |
| cash_actual | decimal(12,2) | | 实际现金余额 |
| cash_diff | decimal(12,2) | | 长短款金额 |
| total_income | decimal(14,2) | DEFAULT 0.00 | 总营收（含所有支付方式） |
| order_count | int | DEFAULT 0 | 开单笔数 |
| status | tinyint | NOT NULL, DEFAULT 0 | 状态：0-进行中 1-已结班 2-已审核 |
| audit_by | bigint | FK → employee.id | 审核人 |
| ... | | | 公共字段 |

### 6.6 daily_settlement — 日结记录表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | |
| settle_date | date | UK, NOT NULL | 营业日期 |
| settle_no | varchar(32) | UK | 日结编号 |
| total_revenue | decimal(14,2) | NOT NULL | 总营收 |
| online_revenue | decimal(14,2) | DEFAULT 0.00 | 上机收入 |
| product_revenue | decimal(14,2) | DEFAULT 0.00 | 商品收入 |
| recharge_revenue | decimal(14,2) | DEFAULT 0.00 | 充值收入 |
| total_recharge | decimal(14,2) | DEFAULT 0.00 | 充值总额 |
| total_refund | decimal(14,2) | DEFAULT 0.00 | 退款总额 |
| total_orders | int | DEFAULT 0 | 总订单数 |
| total_sessions | int | DEFAULT 0 | 上机总次数 |
| peak_concurrent | int | DEFAULT 0 | 当日最高并发 |
| avg_occupancy_rate | decimal(5,2) | DEFAULT 0.00 | 平均上机率（%） |
| cash_amount | decimal(14,2) | DEFAULT 0.00 | 现金支付总额 |
| balance_amount | decimal(14,2) | DEFAULT 0.00 | 余额支付总额 |
| status | tinyint | DEFAULT 0 | 状态：0-待确认 1-已确认 2-已归档 |
| confirm_by | bigint | FK → employee.id | 确认人 |
| confirmed_at | datetime | | |
| ... | | | 公共字段 |

索引：`uk_settle_date`(settle_date)

---

## 7. 员工与权限

### 7.1 employee — 员工表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | |
| employee_no | varchar(32) | UK | 工号 |
| real_name | varchar(32) | NOT NULL | 姓名 |
| phone | varchar(20) | UK | 手机号 |
| password_hash | varchar(128) | NOT NULL | 登录密码 |
| position | varchar(32) | | 岗位：店长/收银员/网管/保洁 |
| employment_type | tinyint | DEFAULT 1 | 雇佣类型：1-全职 2-兼职 3-实习 |
| hire_date | date | | 入职日期 |
| resign_date | date | | 离职日期 |
| status | tinyint | DEFAULT 1 | 状态：1-在职 2-离职 3-停用 |
| is_active | tinyint | DEFAULT 1 | 可登录 |
| last_login_at | datetime | | 最后登录时间 |
| ... | | | 公共字段 |

索引：`uk_employee_no`

### 7.2 role — 角色表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | |
| role_name | varchar(32) | NOT NULL | 角色名称 |
| role_code | varchar(32) | UK | 角色编码（唯一标识） |
| description | varchar(255) | | |
| is_system | tinyint | DEFAULT 0 | 是否系统预置 |
| sort_order | int | DEFAULT 0 | |
| is_active | tinyint | DEFAULT 1 | |
| ... | | | 公共字段 |

### 7.3 permission — 权限表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | |
| parent_id | bigint | FK → permission.id | 父权限 |
| perm_name | varchar(64) | NOT NULL | 权限名称 |
| perm_code | varchar(128) | UK, NOT NULL | 权限编码（如 `member:create`） |
| perm_type | tinyint | DEFAULT 1 | 类型：1-菜单 2-按钮 3-数据 |
| icon | varchar(64) | | 菜单图标 |
| route | varchar(128) | | 前端路由 |
| sort_order | int | DEFAULT 0 | |
| ... | | | 公共字段 |

### 7.4 role_permission — 角色权限关联表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | |
| role_id | bigint | FK → role.id, NOT NULL | |
| permission_id | bigint | FK → permission.id, NOT NULL | |

索引：`uk_role_perm`(role_id, permission_id)

### 7.5 employee_role — 员工角色关联表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | |
| employee_id | bigint | FK → employee.id, NOT NULL | |
| role_id | bigint | FK → role.id, NOT NULL | |
| ... | | | 公共字段 |

索引：`uk_employee_role`(employee_id, role_id)

### 7.6 attendance_record — 考勤记录表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | |
| employee_id | bigint | FK → employee.id, NOT NULL | |
| record_date | date | NOT NULL | 日期 |
| schedule_start | time | | 排班开始时间 |
| schedule_end | time | | 排班结束时间 |
| clock_in | datetime | | 打卡上班时间 |
| clock_out | datetime | | 打卡下班时间 |
| status | tinyint | DEFAULT 0 | 状态：0-正常 1-迟到 2-早退 3-缺勤 4-请假 |
| late_minutes | int | DEFAULT 0 | 迟到分钟数 |
| early_leave_minutes | int | DEFAULT 0 | 早退分钟数 |
| work_hours | decimal(5,2) | DEFAULT 0.00 | 实际工时 |
| overtime_hours | decimal(5,2) | DEFAULT 0.00 | 加班工时 |
| remark | varchar(255) | | |
| ... | | | 公共字段 |

索引：`uk_employee_date`(employee_id, record_date)

### 7.7 commission_rule — 提成规则表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | |
| rule_name | varchar(64) | NOT NULL | 规则名称 |
| rule_type | tinyint | NOT NULL | 提成类型：1-按销售额 2-按开卡数 3-按上机率 4-复合 |
| target_position | varchar(32) | | 适用岗位（空=全部） |
| calculation_method | text | | 计算公式（JSON 表达式） |
| applicable_start | date | | 生效日期 |
| applicable_end | date | | 失效日期 |
| is_active | tinyint | DEFAULT 1 | |
| ... | | | 公共字段 |

### 7.8 commission_record — 提成记录表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | |
| employee_id | bigint | FK → employee.id, NOT NULL | |
| rule_id | bigint | FK → commission_rule.id | |
| settle_date | date | NOT NULL | 结算日期 |
| base_metric | decimal(14,2) | NOT NULL | 基准指标值 |
| commission_rate | decimal(5,4) | NOT NULL | 提成比例 |
| commission_amount | decimal(10,2) | NOT NULL | 提成金额 |
| status | tinyint | DEFAULT 0 | 状态：0-待结算 1-已结算 2-已发放 |
| ... | | | 公共字段 |

---

## 8. 营销活动

### 8.1 campaign — 活动表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | |
| campaign_no | varchar(32) | UK | 活动编号 |
| campaign_name | varchar(100) | NOT NULL | 活动名称 |
| campaign_type | tinyint | NOT NULL | 活动类型：1-充值赠送 2-满减优惠 3-限时折扣 4-新人专享 5-生日活动 6-积分兑换 |
| start_time | datetime | NOT NULL | 开始时间 |
| end_time | datetime | NOT NULL | 结束时间 |
| rules | json | NOT NULL | 活动规则（JSON） |
| budget | decimal(14,2) | | 活动预算 |
| used_budget | decimal(14,2) | DEFAULT 0.00 | 已用预算 |
| usage_limit | int | | 使用次数限制 |
| used_count | int | DEFAULT 0 | 已使用次数 |
| member_limit | int | DEFAULT 1 | 每人限参与次数 |
| status | tinyint | DEFAULT 0 | 状态：0-草稿 1-已发布 2-已生效 3-已结束 4-已下架 |
| ... | | | 公共字段 |

索引：`idx_time`(start_time, end_time), `idx_status`

### 8.2 coupon_template — 优惠券模板表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | |
| template_name | varchar(64) | NOT NULL | 模板名称 |
| coupon_type | tinyint | NOT NULL | 券类型：1-满减券 2-折扣券 3-现金券 4-时段券 |
| face_value | decimal(10,2) | NOT NULL | 面值 |
| min_consume | decimal(10,2) | DEFAULT 0.00 | 最低消费（满减条件） |
| discount_rate | decimal(5,2) | | 折扣率（折扣券专用） |
| valid_days | int | | 有效天数（从发放算起） |
| valid_start | date | | 固定生效日期 |
| valid_end | date | | 固定失效日期 |
| applicable_products | text | | 适用商品 ID 列表（JSON） |
| total_quantity | int | DEFAULT 0 | 发行总量（0=不限量） |
| member_level_limit | bigint | FK → member_level.id | 会员等级限制 |
| is_active | tinyint | DEFAULT 1 | |
| ... | | | 公共字段 |

### 8.3 coupon — 优惠券实例表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | |
| member_id | bigint | FK → member.id, NOT NULL | 持有会员 |
| template_id | bigint | FK → coupon_template.id, NOT NULL | 所属模板 |
| campaign_id | bigint | FK → campaign.id | 来源活动 |
| coupon_code | varchar(32) | UK | 券码 |
| face_value | decimal(10,2) | NOT NULL | 面值 |
| status | tinyint | NOT NULL, DEFAULT 0 | 状态：0-未使用 1-已使用 2-已过期 3-已作废 |
| used_at | datetime | | 使用时间 |
| used_order_id | bigint | FK → orders.id | 使用订单 |
| expire_at | datetime | NOT NULL | 过期时间 |
| ... | | | 公共字段 |

索引：`idx_member_id`, `idx_status`, `idx_expire_at`

---

## 9. 系统与审计

### 9.1 audit_log — 审计日志表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | |
| operator_id | bigint | FK → employee.id | 操作人 |
| operator_name | varchar(32) | | 操作人姓名（防止关联删除追溯） |
| biz_type | varchar(32) | NOT NULL | 业务类型：member/computer/order/payment/etc |
| biz_id | bigint | | 业务 ID |
| action | varchar(32) | NOT NULL | 操作动作：create/update/delete/audit/login |
| detail | json | | 操作详情：{field: {old: ..., new: ...}} |
| ip_address | varchar(15) | | 操作 IP |
| user_agent | varchar(255) | | |
| request_id | varchar(64) | | 请求追踪 ID（全链路） |
| ... | | | 公共字段 |

索引：`idx_biz_type_biz_id`(biz_type, biz_id), `idx_operator_id`, `idx_created_at`

### 9.2 system_config — 系统配置表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | |
| config_key | varchar(64) | UK, NOT NULL | 配置键 |
| config_value | text | | 配置值 |
| config_type | tinyint | DEFAULT 1 | 类型：1-系统参数 2-业务参数 3-界面参数 |
| description | varchar(255) | | |
| is_encrypted | tinyint | DEFAULT 0 | |
| ... | | | 公共字段 |

### 9.3 notification — 通知表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | |
| notify_type | tinyint | NOT NULL | 类型：1-系统公告 2-员工通知 3-会员推送 |
| title | varchar(100) | NOT NULL | |
| content | text | | |
| target_type | tinyint | | 目标类型：0-全部 1-指定角色 2-指定员工 3-指定会员 |
| target_ids | text | | 目标 ID 列表（JSON） |
| is_read | tinyint | DEFAULT 0 | 是否已读（按人维度存储在关联表） |
| published_at | datetime | | 发布时间 |
| expired_at | datetime | | 过期时间 |
| ... | | | 公共字段 |

### 9.4 file_meta — 文件元数据表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | |
| original_name | varchar(255) | NOT NULL | 原始文件名 |
| stored_name | varchar(255) | NOT NULL | 存储文件名 |
| file_path | varchar(500) | NOT NULL | 存储路径 |
| file_size | bigint | NOT NULL | 文件大小（字节） |
| mime_type | varchar(100) | | |
| file_md5 | varchar(64) | | 文件校验值 |
| biz_type | varchar(32) | | 关联业务 |
| biz_id | bigint | | 关联业务 ID |
| ... | | | 公共字段 |

---

## 10. 核心表关联总览

```
member (1) ──< (N) session ──< (N) billing_record
member (1) ──< (N) member_recharge
member (1) ──< (N) member_points_log
member (1) ──< (N) orders
member (1) ──< (N) reservation
member (1) ──< (N) coupon

computer (1) ──< (N) session
seat_area (1) ──< (N) computer
tariff_plan (1) ──< (N) tariff_rate
tariff_plan (1) ──< (N) computer
tariff_plan (1) ──< (N) billing_record

orders (1) ──< (N) order_item
orders (1) ──< (N) payment_record
orders (1) ──< (N) refund_record

product_category (1) ──< (N) product
product (1) ──< (N) inventory
product (1) ──< (N) inventory_log
product (1) ──< (N) purchase_order_item

supplier (1) ──< (N) purchase_order
purchase_order (1) ──< (N) purchase_order_item

employee (1) ──< (N) attendance_record
employee (1) ──< (N) cashier_shift
employee (N) >──< (N) role ──< (N) permission
employee (1) ──< (N) commission_record

campaign (1) ──< (N) coupon_template ──< (N) coupon
```

---

## 11. 数据库设计要点

### 11.1 资金安全

- 所有涉及余额`balance`、积分`available_points`的变更**必须**通过流水表（`member_recharge`、`member_points_log`、`payment_record`）记录，禁止直接 UPDATE 主表余额字段。
- 支付流水表（`payment_record`）通过 `idempotent_key` 唯一约束保证幂等，防止重复扣款。
- 日结表（`daily_settlement`）每日归档，数据不可修改，用于 Demo 项目内部对账。

### 11.2 计费准实时

- `session_timing` 明细表记录每个计费段，客户端心跳 + 服务端定时任务双重写保障。
- `billing_record` 按周期生成结算记录，标记 `is_settled` 防止重复结算。

### 11.3 逻辑删除与查询

- 统一使用 `deleted_at` 逻辑删除，查询时条件 `WHERE deleted_at IS NULL`。
- 需要物理删除的场景仅限：`inventory_log`、`audit_log`、`session_timing` 等纯流水表按月/按季度归档后清理。

### 11.4 分表策略

Demo 单店项目数据量有限，暂不需要分表策略。如后续扩展，可按月对 session、billing_record、payment_record 等大表进行归档。

---

## 12. 索引使用建议

| 场景 | 索引策略 |
|------|----------|
| 手机号登录 | member(phone) UNIQUE |
| 会员历史订单 | orders(member_id, created_at) |
| 当前上机中 | session(status) WHERE status IN (0,1) |
| 日结查询 | daily_settlement(settle_date) UNIQUE |
| 机位状态 | computer(status) |
| 对账查询 | payment_record(paid_at, payment_status) |
| 审计追溯 | audit_log(biz_type, biz_id, created_at) |

---

*文档版本：v2.0（Demo 单店版）*
*最后更新：2026-07-12*
*变更说明：基于 v1.1，适配 Demo 单店版需求。简化支付方式（仅现金+会员余额），移除微信/支付宝/银联/小程序等外部渠道，移除分表策略，日结表去除微信/支付宝金额字段。*
*对应需求文档：《需求文档.md》v1.0*
