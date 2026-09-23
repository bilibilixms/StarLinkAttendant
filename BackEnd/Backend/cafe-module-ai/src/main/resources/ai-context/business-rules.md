# 星络灵侍馆（网咖/电竞馆）— 业务规则上下文

## 会员体系
- 会员等级：普通 → 银卡 → 金卡 → 钻石，按成长值区间（min_growth/max_growth）自动升降级（auto_upgrade=1）
- 成长值：1 元消费/充值 = 1 点成长值，由 member_growth_log 记录，升级时记 level_before/level_after
- 储值账户：balance 余额，通过前台充值（member_recharge），支持充值赠送活动（bonus_amount）
- 积分：消费获得、充值赠送、活动奖励；可兑换商品/抵扣消费（member_points_log，biz_type 区分）
- 等级权益：商品折扣率（discount_rate）、上机折扣（hourly_discount）、充值赠送比例（recharge_bonus_rate）、积分倍数（points_multiple）、透支额度（credit_limit，余额可透支至负值）
- 黑名单机制：status=3 黑名单会员禁止上机和充值，记录 blacklist_reason

## 上机管理
- 机位状态（computer.status）：0-空闲 1-使用中 2-锁定 3-维修 4-关机；is_online 表示心跳在线
- 上机流程：选择空闲机位 → 选择费率方案 → 开始计时（session 创建，status=0 上机中）
- 认证方式（auth_method）：1-刷卡 2-前台手动 3-临时密码
- 下机流程：结算费用（billing_record）→ 机位恢复空闲
- 临时下机：status=1，暂停计费、保留机位（pause_count/pause_duration 记录）
- 强制下机：status=3，余额不足或管理员操作时强制结算
- 预约（reservation）：会员可预约机位，需保证金，超时未到自动取消（status=4）

## 计费规则
- 费率方案（tariff_plan）：1-普通按时 2-包时 3-混合；可按区域/会员等级/星期/时段限定适用
- 费率明细（tariff_rate）：rate_type 1-首价 2-续价 3-包时价；首价含首段时间（first_minutes）+ 首段价格（first_price），续价为元/分钟（renewal_price）
- 每日封顶（max_daily_charge，NULL=不封顶）；最小计费单位（min_charge_minutes）；取整规则（round_rule 1-向上 2-向下 3-四舍五入）
- 会员上机折扣：按 member_level.hourly_discount 打折
- 计费明细（session_timing）：每段计费生成一条，timing_type 1-正常计费 2-临时下机 3-免计费时段
- 计费记录（billing_record）：上机结算生成，含均价、费用、优惠、实收、结算状态

## 商品与库存
- 商品分类（product_category）：支持多级分类树（parent_id），type 区分食品/饮料/虚拟商品/日用品/网游点卡
- 商品价格：成本价 cost_price、零售价 retail_price、会员价 member_price（is_vip_only 仅会员可购）
- 套餐（product_combo）：组合多个商品打包优惠，明细存 product_combo_item
- 库存（inventory）：quantity 当前库存、frozen_quantity 冻结（未完成订单占用）、available_quantity 可用；min_stock/max_stock 预警阈值
- 出入库日志（inventory_log）：change_type 1-采购入库 2-销售出库 3-盘盈 4-盘亏 5-损耗 6-退货入库
- 采购（purchase_order/items）：供应商供货，状态 0-待审核 1-已审核 2-已入库 3-已取消

## 收银与经营
- 订单（orders）：order_type 1-商品销售 2-上机结算 3-充值 4-套餐；会员可散客（member_id 可NULL）
- 支付方式：1-现金 2-会员余额（payment_method）；支付记录带幂等键防重复
- 退款（refund_record）：1-全额 2-部分；退款方式 1-现金退 2-退余额；需审核（status 0-待审核 1-已审核 2-已完成 3-已拒绝）
- 收银班次（cashier_shift）：开班记录备用金，结班核对现金长短款（cash_diff）
- 日结（daily_settlement）：每日汇总营收（上机/商品/充值）、订单数、上机次数、最高并发、平均上机率
- 充值：1-现金 2-会员余额，可关联活动赠金，前台操作记录 operator_id

## 活动与营销
- 活动类型（campaign.campaign_type）：1-充值赠送 2-满减 3-限时折扣 4-新人专享 5-生日 6-积分兑换
- 活动规则存 rules(JSON)，有预算/次数限制/每人限参与；状态 0-草稿 1-已发布 2-已生效 3-已结束 4-已下架
- 优惠券（coupon_template/coupon）：1-满减券 2-折扣券 3-现金券 4-时段券；实例绑定会员，有有效期与使用状态

## 员工与权限
- RBAC 权限模型：员工（employee）→ 角色（employee_role/role）→ 权限（role_permission/permission）
- 角色编码（role_code）：如 super_admin / store_manager / cashier / net_admin，按角色控制接口访问
- 考勤（attendance_record）：排班、打卡、迟到早退、请假状态，计算工时与加班
- 提成（commission_rule/record）：按销售额/开卡数/上机率等规则计算员工提成

## 其他
- 审计日志（audit_log）：关键操作记录操作人、业务类型、动作、详情、IP、请求ID，用于追溯
- 通知（notification）：系统公告/员工通知/会员推送，可指定角色/员工/会员
- 系统配置（system_config）：系统/业务/界面参数，支持加密存储
