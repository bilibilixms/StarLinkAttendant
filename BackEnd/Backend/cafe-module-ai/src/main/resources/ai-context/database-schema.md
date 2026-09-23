# 数据库完整表结构（starlink_attendant）

> 星络灵侍馆（网咖/电竞馆）业务库，共 46 张表。
> 所有表统一含公共字段：`id`(BIGINT UNSIGNED PK AUTO_INCREMENT), `created_at`, `updated_at`, `deleted_at`(软删除, NULL=未删除), `version`(乐观锁)。
> 查询时务必：`WHERE deleted_at IS NULL` + `LIMIT 100`。

## member（会员主表）
- member_no 会员编号(自动生成), real_name 真实姓名, gender 性别(0-未知 1-男 2-女), phone 手机号(登录账号)
- id_card 身份证号(加密存储), id_card_hash 身份证哈希, birthday 出生日期
- level_id → member_level.id 当前等级, total_points 累计积分, available_points 可用积分, growth_value 成长值(1元=1经验)
- total_recharge 累计充值, balance 账户余额, total_consumption 累计消费
- last_login_time 最后登录, last_online_time 最后上机, total_online_hours 累计上机时长(分钟)
- face_feature 人脸特征向量(密文), password_hash 登录密码哈希
- register_source(1-前台 2-后台导入), status(1-正常 2-冻结 3-黑名单), blacklist_reason 黑名单原因, tag 标签(JSON)

## member_level（会员等级表）
- level_name 等级名称(普通/银卡/金卡/钻石), level_order 排序(越大越高), min_growth/max_growth 成长值区间
- discount_rate 商品折扣率(%,100=无折扣), hourly_discount 上机折扣率(%), recharge_bonus_rate 充值赠送比例(%)
- points_multiple 积分倍数, auto_upgrade 是否自动升级(0否1是), credit_limit 透支额度(余额可透支至负值), icon_url 图标

## member_balance_log（会员余额变动流水）
- member_id → member.id, amount 变动金额(正增负减), balance_before/balance_after 变动前后余额
- biz_type(1-消费 2-退款回充 3-充值 4-手动调整), biz_id 关联单据ID, remark

## member_growth_log（会员成长值流水）
- member_id → member.id, growth 本次获得成长值, growth_before/growth_after
- biz_type(1-收银直接消费 2-余额充值), biz_id 关联业务ID
- level_before_id/level_after_id 变动前后等级, is_upgraded 是否触发自动升级(0否1是), remark

## member_points_log（积分流水）
- member_id → member.id, points 变动积分(正增负减), balance_before/balance_after
- biz_type(1-消费获得 2-充值赠送 3-兑换消耗 4-活动奖励 5-手动调整 6-过期扣除), biz_id, remark, expire_at 过期时间

## member_recharge（充值记录）
- member_id → member.id, recharge_no 充值单号
- recharge_amount 实付金额, bonus_amount 赠送金额, total_amount 到账总额=充值+赠送
- balance_before/balance_after 充值前后余额, payment_method(1-现金 2-会员余额), payment_channel/trade_no(预留)
- operator_id → employee.id(自助为NULL), campaign_id → campaign.id 关联活动
- status(0-待支付 1-成功 2-失败 3-已退款), paid_at 支付完成时间

## coupon（优惠券实例）
- member_id 持有会员 → member.id, template_id → coupon_template.id, campaign_id 来源活动
- coupon_code 券码, face_value 面值, status(0-未使用 1-已使用 2-已过期 3-已作废)
- used_at, used_order_id → orders.id, expire_at 过期时间

## coupon_template（优惠券模板）
- template_name, coupon_type(1-满减券 2-折扣券 3-现金券 4-时段券)
- face_value 面值, min_consume 最低消费(满减), discount_rate 折扣率(折扣券)
- valid_days 有效天数 / valid_start/valid_end 固定有效期, applicable_products 适用商品ID(JSON)
- total_quantity 发行总量(0=不限), member_level_limit 等级限制, is_active 启用

## reservation（预约表）
- member_id → member.id, computer_id 指定机位(可NULL=到店分配), reservation_no 预约编号
- reservation_date 日期, start_time/end_time 起止时间, deposit_amount 保证金
- status(0-待确认 1-已确认 2-已上机 3-已取消 4-超时未到)
- cancel_reason, checked_in_at 实际到店, session_id 关联上机会话

## computer（终端设备/机位表）
- area_id → seat_area.id 所属区域, computer_no 机位编号(如A-001), computer_name, seat_label 座位标签
- device_type(1-普通PC 2-电竞PC 3-包间 4-PS5/主机), cpu/gpu/memory/screen_size 配置
- mac_address, ip_address, is_online 在线(心跳): 0否1是
- status 运营状态(0-空闲 1-使用中 2-锁定 3-维修 4-关机)
- tariff_plan_id 当前费率方案, sort_order/pos_x/pos_y 座位图, is_active 启用

## seat_area（机位区域表）
- area_name 区域名(如A区/电竞区/包间), area_color 标识色(#1890ff), sort_order, is_active 启用

## tariff_plan（费率方案表）
- plan_name 方案名, plan_type(1-普通按时 2-包时 3-混合), is_default 默认方案, priority 优先级(高优先)
- applicable_areas 适用区域ID(JSON), applicable_levels 适用等级ID(JSON)
- effective_days 有效星期(1-7逗号分隔), effective_start/effective_end 生效时段, status(0-停用 1-启用)

## tariff_rate（费率明细表）
- plan_id → tariff_plan.id, rate_name, rate_type(1-首价 2-续价 3-包时价)
- first_minutes 首段时间(分钟), first_price 首段价格
- renewal_price 续价单价(元/分钟 DECIMAL(10,4))
- max_daily_charge 每日封顶(可NULL), min_charge_minutes 最小计费单位(分钟), round_rule 取整(1-上 2-下 3-四舍五入)

## session（上机会话表）
- computer_id → computer.id, member_id → member.id(可NULL=散客), session_no 会话编号
- auth_method(1-刷卡 2-前台手动 3-临时密码), tariff_plan_id 适用费率
- start_time 上机, end_time 下机, expected_minutes 预计时长, billed_minutes 已计费时长, free_minutes 赠送时长
- total_amount 总费用, discount_amount 优惠, paid_amount 实付
- status(0-上机中 1-临时下机 2-已下机 3-强制下机 4-异常中断)
- pause_count 临时下机次数, pause_duration 临时下机总时长(分钟), operator_id → employee.id, remark

## session_timing（上机计时明细）
- session_id → session.id, timing_type(1-正常计费 2-临时下机 3-免计费时段)
- rate_type(1-按时 2-包时), rate_price 当前费率(元/分钟), start_time/end_time, duration_minutes 段时长, amount 段费用

## billing_record（计费记录表）
- session_id → session.id, member_id → member.id, computer_id → computer.id, record_no 流水号
- tariff_plan_id 费率方案, maintenance_minutes 计费时长, unit_price 均价, total_amount 费用
- discount_amount 优惠, final_amount 实收, billing_start/billing_end 计费周期
- is_settled(0否1是), settled_at 结算时间

## product（商品表）
- category_id → product_category.id, product_code 商品编码(条码), product_name
- product_type(1-食品 2-饮料 3-虚拟商品 4-日用品 5-网游点卡), unit 单位
- cost_price 成本价, retail_price 零售价, member_price 会员价, image_url
- is_vip_only 仅会员购买, is_active 上下架(0下架 1上架)

## product_category（商品分类表）
- parent_id 父分类(可NULL=一级), category_name, icon, sort_order, level 层级, is_active 启用

## product_combo（套餐表）
- combo_name, combo_code, description
- original_price 原价(明细合计), combo_price 套餐价, image_url, is_active 上下架, sort_order

## product_combo_item（套餐明细）
- combo_id → product_combo.id, product_id → product.id
- product_name 快照名称, unit_price 快照单价, quantity 数量, subtotal 小计=单价×数量

## inventory（库存表）
- product_id → product.id, batch_no 批次号
- quantity 当前库存, frozen_quantity 冻结库存(未完成订单占用), available_quantity 可用库存=quantity-frozen
- min_stock 最低预警, max_stock 最高预警, production_date/expiry_date 生产/过期日期

## inventory_log（库存变动日志）
- product_id → product.id, batch_no, change_type(1-采购入库 2-销售出库 3-盘盈 4-盘亏 5-损耗 6-退货入库)
- change_quantity 变动数量(正入负出), balance_before/balance_after, ref_biz_type/ref_biz_id, remark

## purchase_order（采购单）
- supplier_id → supplier.id, order_no 采购单号, total_amount 总金额
- status(0-待审核 1-已审核 2-已入库 3-已取消), audit_by → employee.id, audit_at, remark

## purchase_order_item（采购明细）
- order_id → purchase_order.id, product_id → product.id
- quantity 采购数量, unit_price 单价, total_price 小计, received_quantity 已入库数量, remark

## supplier（供应商表）
- supplier_code, supplier_name, contact_person, contact_phone, address
- credit_level 信用等级(1-5), payment_terms 结算方式, is_active 启用

## orders（订单主表）
- order_no 订单号, order_type(1-商品销售 2-上机结算 3-充值 4-套餐)
- member_id → member.id(可NULL=散客), session_id 关联上机会话
- total_amount 总额, discount_amount 优惠, payable_amount 应付, paid_amount 已付
- status(0-待支付 1-已支付 2-部分退款 3-已退款 4-已取消), remark, operator_id 收银员, paid_at

## order_item（订单明细）
- order_id → orders.id, item_type(1-商品 2-上机时长 3-包时段 4-套餐)
- product_id(商品类型时关联), product_name 快照名称, unit_price 快照单价, quantity, subtotal 小计, discount 行优惠分摊

## payment_record（支付记录）
- payment_no 流水号, order_id → orders.id, member_id → member.id
- payment_method(1-现金 2-会员余额), trade_no(预留), total_amount, refund_amount 已退款
- payment_status(0-待支付 1-支付成功 2-支付失败 3-已退款), paid_at, operator_id, idempotent_key 幂等键

## refund_record（退款记录）
- refund_no 退款单号, order_id 原订单, payment_id 原支付记录, member_id
- refund_amount, refund_type(1-全额 2-部分), refund_reason, refund_method(1-现金退 2-退余额)
- status(0-待审核 1-已审核 2-已完成 3-已拒绝), audit_by, audit_at, operator_id

## cashier_shift（收银班次）
- employee_id 收银员, shift_no 班次编号, open_at 开班, close_at 结班, opening_balance 备用金
- cash_income 现金收入, cash_expenditure 现金支出(退款), cash_expected/cash_actual/cash_diff 预期/实际/长短款
- total_income 总营收, order_count 开单笔数, status(0-进行中 1-已结班 2-已审核), audit_by

## daily_settlement（日结记录）
- settle_date 营业日期, settle_no 日结编号
- total_revenue 总营收, online_revenue 上机收入, product_revenue 商品收入, recharge_revenue 充值收入
- total_recharge 充值总额, total_refund 退款总额, total_orders 总订单数, total_sessions 上机次数
- peak_concurrent 最高并发, avg_occupancy_rate 平均上机率(%), cash_amount/balance_amount 现金/余额支付
- status(0-待确认 1-已确认 2-已归档), confirm_by, confirmed_at

## employee（员工表）
- employee_no 工号, real_name 姓名, phone, password_hash 登录密码
- position 岗位(店长/收银员/网管/保洁), employment_type(1-全职 2-兼职 3-实习)
- hire_date 入职, resign_date 离职, status(1-在职 2-离职 3-停用), is_active 可登录, last_login_at

## employee_role（员工角色关联）
- employee_id → employee.id, role_id → role.id

## role（角色表）
- role_name 角色名, role_code 角色编码(唯一), description, is_system 系统预置, sort_order, is_active 启用

## permission（权限表）
- parent_id 父权限, perm_name, perm_code 权限编码(如member:create), perm_type(1-菜单 2-按钮 3-数据), icon, route, sort_order

## role_permission（角色权限关联）
- role_id → role.id, permission_id → permission.id

## attendance_record（考勤记录）
- employee_id → employee.id, record_date 日期, schedule_start/schedule_end 排班时间
- clock_in/clock_out 打卡时间, status(0-正常 1-迟到 2-早退 3-缺勤 4-请假)
- late_minutes 迟到分钟, early_leave_minutes 早退分钟, work_hours 实际工时, overtime_hours 加班工时, remark

## commission_rule（提成规则）
- rule_name, rule_type(1-按销售额 2-按开卡数 3-按上机率 4-复合), target_position 适用岗位(空=全部)
- calculation_method 计算公式(JSON), applicable_start/end 生效期, is_active 启用

## commission_record（提成记录）
- employee_id → employee.id, rule_id → commission_rule.id, settle_date 结算日期
- base_metric 基准指标值, commission_rate 提成比例, commission_amount 提成金额
- status(0-待结算 1-已结算 2-已发放)

## campaign（活动表）
- campaign_no, campaign_name, campaign_type(1-充值赠送 2-满减 3-限时折扣 4-新人专享 5-生日 6-积分兑换)
- start_time/end_time, rules 活动规则(JSON), budget 预算, used_budget 已用预算
- usage_limit 次数限制, used_count 已用次数, member_limit 每人限参与
- status(0-草稿 1-已发布 2-已生效 3-已结束 4-已下架)

## notification（通知表）
- notify_type(1-系统公告 2-员工通知 3-会员推送), title, content
- target_type(0-全部 1-指定角色 2-指定员工 3-指定会员), target_ids(JSON)
- is_read 已读, published_at 发布时间, expired_at 过期时间

## audit_log（审计日志）
- operator_id → employee.id, operator_name 操作人姓名(防删除追溯)
- biz_type 业务类型(member/computer/order/payment), biz_id, action(create/update/delete/audit/login)
- detail 操作详情(JSON), ip_address, user_agent, request_id 请求追踪ID

## file_meta（文件元数据）
- original_name 原始名, stored_name 存储名, file_path, file_size 字节, mime_type, file_md5
- biz_type 关联业务, biz_id 关联业务ID

## system_config（系统配置）
- config_key 配置键, config_value 配置值, config_type(1-系统参数 2-业务参数 3-界面参数), description, is_encrypted 加密

## ai_chat_session（AI 助手会话表）
- session_no 对外会话号(唯一), user_type(user=用户端 admin=管理端), user_id 归属用户ID, user_name 归属用户名
- title 会话标题(默认首条提问前20字), message_count 消息条数, updated_at 更新时间

## ai_chat_message（AI 助手消息表）
- session_id → ai_chat_session.id, role(user=提问 assistant=回答), content 消息内容
- thinking_json AI思考步骤(JSON), cost_ms 耗时(毫秒), model 模型名
