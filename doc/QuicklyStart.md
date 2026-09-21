# 星络灵侍馆 — 业务事件速查手册

> 文档版本：v2.0 | 最后更新：2026-09 | 基于 database-design.md v2.0
>
> 本文档面向全体开发成员，目标是帮助快速理解系统中有哪些业务事件、每个事件的详细表调用流程（操作顺序、字段变更、事务边界与异常处理）。

---

## 阅读约定

- 每个事件格式为：**事件名称** + 场景描述 + 前置条件 + 事务边界 + 表调用流程表 + 异常处理。
- 表调用流程表中，每一步标注操作表、操作类型（SELECT / INSERT / UPDATE / DELETE）、目的与关键字段变更。
- 标注 `（条件）` 的步骤表示仅在满足特定条件时执行（如命中活动、余额支付等）。
- 所有 UPDATE 操作均隐含 `version = version + 1` 乐观锁更新，除非另有说明。
- 所有查询均隐含 `WHERE deleted_at IS NULL` 逻辑删除过滤条件。
- 状态枚举值与 `database-design.md` 严格一致。

---

## 一、会员管理（10 个事件）

### E-1.1 会员注册

新会员通过前台完成注册，系统校验身份信息、自动生成会员编号并分配初始等级。

**前置条件**：手机号未注册（`member.phone` 唯一约束校验通过）；如提供身份证号，`id_card_hash` 去重校验通过。

**事务边界**：步骤 1~4 在同一事务中提交。

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | member | SELECT | 校验手机号是否已注册 | WHERE phone = ? AND deleted_at IS NULL |
| 2 | member | SELECT | （如提供身份证）校验身份证号去重 | WHERE id_card_hash = ? AND deleted_at IS NULL |
| 3 | member_level | SELECT | 获取初始等级（level_order 最小且 is_active=1） | ORDER BY level_order ASC LIMIT 1 → 取得 level_id |
| 4 | member | INSERT | 写入会员主记录 | member_no = 自动生成（如 'M' + 时间戳 + 随机码）；phone = 手机号；real_name = 真实姓名；id_card = 加密后身份证号；id_card_hash = 哈希值；level_id = 初始等级 ID；total_points = 0；available_points = 0；total_recharge = 0.00；balance = 0.00；total_consumption = 0.00；total_online_hours = 0；password_hash = 密码哈希；register_source = 1（前台）；status = 1（正常）；version = 1 |
| 5 | audit_log | INSERT | 记录注册审计日志 | biz_type = 'member'；biz_id = 新会员 ID；action = 'create'；detail = {"phone": "xxx", "register_source": 1} |

**异常处理**：
- 手机号已存在：抛出业务异常，回滚事务，返回"该手机号已注册"。
- 身份证号重复：抛出业务异常，回滚事务，返回"该身份证已注册"。
- 乐观锁冲突：version 校验失败时重试（INSERT 场景不涉及，仅并发写入时考虑）。

---

### E-1.2 会员充值

会员通过前台进行账户充值，系统根据当前活动计算赠送金额，更新余额并记录流水。

**前置条件**：会员状态为正常（`status = 1`）；充值金额大于 0。

**事务边界**：步骤 1~6 在同一事务中提交。

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | member | SELECT | 查询会员当前余额与状态 | WHERE id = ? AND deleted_at IS NULL → 取得 balance、status、level_id |
| 2 | campaign | SELECT | 查询当前生效的充值赠送活动 | WHERE campaign_type = 1 AND status IN (1,2) AND start_time <= NOW() AND end_time >= NOW() AND deleted_at IS NULL → 取得活动 rules（含赠送比例） |
| 3 | member_recharge | INSERT | 写入充值流水记录 | member_id = 会员 ID；recharge_no = 自动生成；recharge_amount = 实充金额；bonus_amount = 赠送金额（按活动 rules 计算，无活动则为 0）；total_amount = recharge_amount + bonus_amount；balance_before = 当前余额；balance_after = balance_before + total_amount；payment_method = 支付方式；operator_id = 操作员工 ID（自助为 NULL）；campaign_id = 活动 ID（无则为 NULL）；status = 1（成功）；paid_at = NOW() |
| 4 | member | UPDATE | 更新会员余额与累计充值 | balance = balance_after；total_recharge = total_recharge + recharge_amount；version = version + 1（乐观锁） |
| 5 | campaign | UPDATE | （如有活动）累加活动已使用次数 | used_count = used_count + 1；version = version + 1 |
| 6 | member_points_log | INSERT | （如活动含赠送积分）记录积分赠送流水 | member_id = 会员 ID；points = 赠送积分值（正数）；balance_before = 当前可用积分；balance_after = balance_before + 赠送积分；biz_type = 2（充值赠送）；biz_id = member_recharge.id；expire_at = NOW() + 积分有效期 |
| 7 | member | UPDATE | （如有赠送积分）更新积分 | available_points = available_points + 赠送积分；total_points = total_points + 赠送积分；version = version + 1 |

**异常处理**：
- 会员状态异常（冻结/黑名单）：拒绝充值，回滚事务。
- 活动已过期或已达上限：跳过赠送计算，bonus_amount = 0，正常完成充值。
- 乐观锁冲突（version 不匹配）：重试整个事务，最多 3 次。
- 充值幂等：通过 recharge_no 唯一约束防止重复入账。

---

### E-1.3 充值退款

对已成功充值的记录发起退款，回滚余额、赠送金额及关联积分。

**前置条件**：目标充值记录状态为成功（`status = 1`）；退款金额不超过原充值到账总额。

**事务边界**：步骤 1~7 在同一事务中提交。

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | member_recharge | SELECT | 查询原充值记录 | WHERE id = ? AND status = 1 AND deleted_at IS NULL → 取得 member_id、total_amount、bonus_amount、balance_after |
| 2 | member | SELECT | 查询会员当前余额 | WHERE id = member_id → 取得 balance、available_points、version |
| 3 | refund_record | INSERT | 写入退款记录 | refund_no = 自动生成；order_id = NULL（充值退款无关联订单）；payment_id = 原支付记录 ID；member_id = 会员 ID；refund_amount = 退款金额（原 recharge_amount）；refund_type = 1（全额退款）/ 2（部分退款）；refund_reason = 退款原因；refund_method = 退款方式；status = 2（已完成）；operator_id = 操作员工 |
| 4 | member_recharge | UPDATE | 更新原充值记录状态 | status = 3（已退款）；version = version + 1 |
| 5 | member | UPDATE | 回滚会员余额与累计充值 | balance = balance - refund_total_amount（含赠送部分）；total_recharge = total_recharge - recharge_amount；version = version + 1 |
| 6 | member_points_log | SELECT | （如有赠送积分需回收）查询赠送积分流水 | WHERE biz_id = member_recharge.id AND biz_type = 2 → 取得赠送积分值 |
| 7 | member_points_log | INSERT | （如需回收）记录积分扣回流水 | member_id = 会员 ID；points = -赠送积分值（负数）；balance_before = 当前可用积分；balance_after = balance_before - 赠送积分；biz_type = 5（手动调整）；biz_id = refund_record.id；remark = '充值退款积分回收' |
| 8 | member | UPDATE | （如需回收）更新积分 | available_points = available_points - 赠送积分；version = version + 1 |

**异常处理**：
- 充值记录已退款：抛出业务异常，拒绝重复退款。
- 余额不足（已部分消费）：根据业务策略，允许退至负值或拒绝退款并回滚。
- 乐观锁冲突：重试整个事务。
- 退款操作失败：refund_record.status 保持 0（待审核），人工介入处理。

---

### E-1.4 会员等级变更

基于成长值自动升降级，或由管理员手动调整会员等级。

**前置条件**：会员存在且状态正常；自动升级需成长值满足目标等级阈值。

**事务边界**：步骤 1~4 在同一事务中提交。

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | member | SELECT | 查询会员当前等级与成长值 | WHERE id = ? AND deleted_at IS NULL → 取得 level_id、total_recharge（作为成长值依据） |
| 2 | member_level | SELECT | 查询目标等级信息 | 自动：WHERE min_growth <= 成长值 AND max_growth > 成长值 AND deleted_at IS NULL；手动：WHERE id = 目标 level_id → 取得 level_name、discount_rate 等 |
| 3 | member | UPDATE | 更新会员等级 | level_id = 目标等级 ID；version = version + 1 |
| 4 | audit_log | INSERT | 记录等级变更审计 | biz_type = 'member'；biz_id = 会员 ID；action = 'update'；detail = {"level_id": {"old": 原等级, "new": 新等级}, "change_type": "auto/manual"} |

**异常处理**：
- 目标等级不存在或已停用：抛出业务异常，回滚事务。
- 自动升级时成长值不满足任何等级：保持当前等级不变，记录告警日志。
- 乐观锁冲突：重试事务。

---

### E-1.5 会员账户冻结

管理员冻结会员账户，冻结后会员无法上机及消费。

**前置条件**：会员当前状态为正常（`status = 1`）；操作人具有冻结权限。

**事务边界**：步骤 1~2 在同一事务中提交。

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | member | SELECT | 查询会员当前状态 | WHERE id = ? AND deleted_at IS NULL → 取得 status |
| 2 | member | UPDATE | 冻结会员账户 | status = 2（冻结）；version = version + 1 |
| 3 | audit_log | INSERT | 记录冻结操作审计 | biz_type = 'member'；biz_id = 会员 ID；action = 'update'；detail = {"status": {"old": 1, "new": 2}, "reason": "冻结原因"} |

**异常处理**：
- 会员已处于冻结/黑名单状态：抛出业务异常，拒绝重复操作。
- 乐观锁冲突：重试事务。

---

### E-1.6 会员账户解冻

管理员解冻已冻结的会员账户，恢复正常使用。

**前置条件**：会员当前状态为冻结（`status = 2`）；操作人具有解冻权限。

**事务边界**：步骤 1~2 在同一事务中提交。

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | member | SELECT | 查询会员当前状态 | WHERE id = ? AND deleted_at IS NULL → 取得 status |
| 2 | member | UPDATE | 解冻会员账户 | status = 1（正常）；version = version + 1 |
| 3 | audit_log | INSERT | 记录解冻操作审计 | biz_type = 'member'；biz_id = 会员 ID；action = 'update'；detail = {"status": {"old": 2, "new": 1}} |

**异常处理**：
- 会员非冻结状态：抛出业务异常，拒绝操作。
- 乐观锁冲突：重试事务。

---

### E-1.7 加入黑名单

将会员标记为黑名单，若会员当前正在上机则强制下机并释放机位。

**前置条件**：会员存在；操作人具有黑名单管理权限；需填写拉黑原因。

**事务边界**：步骤 1~6 在同一事务中提交。

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | member | SELECT | 查询会员当前状态 | WHERE id = ? AND deleted_at IS NULL → 取得 status |
| 2 | session | SELECT | 查询会员是否有进行中的上机会话 | WHERE member_id = ? AND status IN (0, 1) AND deleted_at IS NULL → 取得 session.id、computer_id |
| 3 | member | UPDATE | 将会员加入黑名单 | status = 3（黑名单）；blacklist_reason = 拉黑原因；version = version + 1 |
| 4 | session | UPDATE | （如有进行中会话）强制下机 | status = 3（强制下机）；end_time = NOW()；remark = '管理员拉黑强制下机'；version = version + 1 |
| 5 | computer | UPDATE | （如有进行中会话）释放机位 | status = 0（空闲）；version = version + 1 |
| 6 | audit_log | INSERT | 记录拉黑操作审计 | biz_type = 'member'；biz_id = 会员 ID；action = 'update'；detail = {"status": {"old": 原状态, "new": 3}, "blacklist_reason": "原因", "force_offline": true/false} |

**异常处理**：
- 会员已在黑名单中：抛出业务异常，拒绝重复操作。
- 强制下机会话不存在：跳过步骤 4、5，仅更新会员状态。
- 乐观锁冲突：重试事务。

---

### E-1.8 移除黑名单

将会员从黑名单中移除，恢复正常状态。

**前置条件**：会员当前状态为黑名单（`status = 3`）；操作人具有黑名单管理权限。

**事务边界**：步骤 1~2 在同一事务中提交。

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | member | SELECT | 查询会员当前状态 | WHERE id = ? AND deleted_at IS NULL → 取得 status |
| 2 | member | UPDATE | 移除黑名单，恢复正常 | status = 1（正常）；blacklist_reason = NULL；version = version + 1 |
| 3 | audit_log | INSERT | 记录移除黑名单审计 | biz_type = 'member'；biz_id = 会员 ID；action = 'update'；detail = {"status": {"old": 3, "new": 1}, "blacklist_reason": {"old": "原原因", "new": null}} |

**异常处理**：
- 会员非黑名单状态：抛出业务异常，拒绝操作。
- 乐观锁冲突：重试事务。

---

### E-1.9 积分兑换

会员使用可用积分兑换商品或上机时长。

**前置条件**：会员状态正常；可用积分大于等于兑换所需积分；兑换商品库存充足（实物商品场景）。

**事务边界**：步骤 1~8 在同一事务中提交。

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | member | SELECT | 查询会员可用积分 | WHERE id = ? AND deleted_at IS NULL → 取得 available_points、level_id |
| 2 | member_points_log | INSERT | 记录积分兑换消耗流水 | member_id = 会员 ID；points = -兑换积分值（负数）；balance_before = 当前可用积分；balance_after = balance_before - 兑换积分；biz_type = 3（兑换消耗）；biz_id = 兑换业务 ID；remark = '积分兑换' |
| 3 | member | UPDATE | 扣减可用积分 | available_points = available_points - 兑换积分值；version = version + 1 |
| 4 | orders | INSERT | （兑换实物商品）创建订单 | order_no = 自动生成；order_type = 1（商品销售）；member_id = 会员 ID；total_amount = 商品价值；discount_amount = 商品价值（积分全额抵扣）；payable_amount = 0.00；paid_amount = 0.00；status = 1（已支付）；operator_id = 操作员工 |
| 5 | order_item | INSERT | （兑换实物商品）写入订单明细 | order_id = 订单 ID；item_type = 1（商品）；product_id = 商品 ID；product_name = 商品名称快照；unit_price = 商品单价；quantity = 数量；subtotal = 小计 |
| 6 | inventory | SELECT | （兑换实物商品）查询库存 | WHERE product_id = ? AND deleted_at IS NULL → 取得 available_quantity |
| 7 | inventory | UPDATE | （兑换实物商品）扣减库存 | quantity = quantity - 数量；available_quantity = available_quantity - 数量；version = version + 1 |
| 8 | inventory_log | INSERT | （兑换实物商品）记录库存变动 | product_id = 商品 ID；change_type = 2（销售出库）；change_quantity = -数量；balance_before = 变动前数量；balance_after = 变动后数量；ref_biz_type = 'order'；ref_biz_id = 订单 ID |
| 9 | session | UPDATE | （兑换上机时长）增加赠送时长 | free_minutes = free_minutes + 兑换分钟数；version = version + 1 |

**异常处理**：
- 可用积分不足：抛出业务异常，回滚事务。
- 商品库存不足：抛出业务异常，回滚事务。
- 乐观锁冲突：重试事务。

---

### E-1.10 积分过期处理

定时任务扫描过期积分，自动扣除未使用的过期积分。

**前置条件**：定时任务触发（每日凌晨执行）；`member_points_log` 中存在 `expire_at < NOW()` 且未被扣除的积分记录。

**事务边界**：每个会员的过期积分处理在独立事务中提交（逐会员处理，避免长事务）。

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | member_points_log | SELECT | 扫描即将过期的积分记录 | WHERE expire_at < NOW() AND biz_type NOT IN (3, 6) AND deleted_at IS NULL → 按 member_id 分组汇总过期积分总量 |
| 2 | member | SELECT | 查询会员当前可用积分 | WHERE id = member_id → 取得 available_points |
| 3 | member_points_log | INSERT | 记录过期扣除流水 | member_id = 会员 ID；points = -过期积分值（负数）；balance_before = 当前可用积分；balance_after = balance_before - 过期积分；biz_type = 6（过期扣除）；remark = '积分过期自动扣除' |
| 4 | member | UPDATE | 扣减可用积分 | available_points = available_points - 过期积分值；version = version + 1 |

**异常处理**：
- 会员可用积分已被其他操作变更（乐观锁冲突）：重试该会员的事务。
- 可用积分不足以覆盖过期量（并发场景）：将 available_points 置为 0，记录实际扣除量，保证积分不为负。
- 无过期记录：跳过，不产生事务。

---

## 二、上机管理（12 个事件）

### E-2.1 会员上机

会员通过刷卡或前台手动认证在终端上机，系统创建会话、分配机位并开始计费。

**前置条件**：会员状态正常（`status = 1`）；目标机位状态为空闲（`computer.status = 0`）且已启用（`is_active = 1`）；会员无进行中的会话。

**事务边界**：步骤 1~6 在同一事务中提交。

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | member | SELECT | 查询会员信息与状态 | WHERE id = ? AND deleted_at IS NULL → 取得 status、level_id、balance |
| 2 | computer | SELECT | 查询目标机位状态 | WHERE id = ? AND status = 0 AND is_active = 1 AND deleted_at IS NULL → 取得 tariff_plan_id |
| 3 | tariff_plan | SELECT | 查询适用费率方案 | WHERE id = computer.tariff_plan_id AND status = 1 AND deleted_at IS NULL → 取得 plan_type |
| 4 | tariff_rate | SELECT | 查询费率明细 | WHERE plan_id = tariff_plan_id AND deleted_at IS NULL → 取得 rate_type、renewal_price（元/分钟） |
| 5 | session | INSERT | 创建上机会话记录 | computer_id = 机位 ID；member_id = 会员 ID；session_no = 自动生成；auth_method = 认证方式（1-刷卡/4-临时密码/前台手动）；tariff_plan_id = 费率方案 ID；start_time = NOW()；billed_minutes = 0；free_minutes = 0；total_amount = 0.00；discount_amount = 0.00；paid_amount = 0.00；status = 0（上机中）；pause_count = 0；pause_duration = 0；operator_id = NULL（自助上机）；version = 1 |
| 6 | computer | UPDATE | 更新机位状态为使用中 | status = 1（使用中）；version = version + 1 |
| 7 | member | UPDATE | 更新会员登录时间 | last_login_time = NOW()；last_online_time = NOW()；version = version + 1 |
| 8 | session_timing | INSERT | 写入首个计费时段 | session_id = 会话 ID；timing_type = 1（正常计费）；rate_type = 费率类型（1-按时/2-包时）；rate_price = 单价（元/分钟）；start_time = NOW()；duration_minutes = 0；amount = 0.00 |

**异常处理**：
- 会员状态异常（冻结/黑名单）：拒绝上机，回滚事务。
- 机位已被占用或已停用：拒绝上机，回滚事务。
- 会员已有进行中会话：拒绝上机，回滚事务。
- 乐观锁冲突：重试事务。

---

### E-2.2 散客上机

无会员身份的临时顾客上机，不关联会员信息，member_id 为 NULL。

**前置条件**：目标机位状态为空闲（`computer.status = 0`）且已启用。

**事务边界**：步骤 1~3 在同一事务中提交。

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | computer | SELECT | 查询目标机位状态 | WHERE id = ? AND status = 0 AND is_active = 1 AND deleted_at IS NULL → 取得 tariff_plan_id |
| 2 | session | INSERT | 创建散客上机会话 | computer_id = 机位 ID；member_id = NULL；session_no = 自动生成；auth_method = 4（临时密码）；tariff_plan_id = 费率方案 ID；start_time = NOW()；billed_minutes = 0；total_amount = 0.00；status = 0（上机中）；version = 1 |
| 3 | computer | UPDATE | 更新机位状态为使用中 | status = 1（使用中）；version = version + 1 |
| 4 | session_timing | INSERT | 写入首个计费时段 | session_id = 会话 ID；timing_type = 1（正常计费）；rate_type = 1（按时）；rate_price = 单价；start_time = NOW()；duration_minutes = 0；amount = 0.00 |

**异常处理**：
- 机位不可用：拒绝上机，回滚事务。
- 乐观锁冲突：重试事务。

---

### E-2.3 预约到店上机

已预约的会员到店，系统将预约记录转换为上机会话。

**前置条件**：预约记录状态为已确认（`reservation.status = 1`）；预约指定的机位空闲（或未指定机位时有可用机位）；会员状态正常。

**事务边界**：步骤 1~5 在同一事务中提交。

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | reservation | SELECT | 查询预约记录 | WHERE id = ? AND status = 1 AND deleted_at IS NULL → 取得 member_id、computer_id、session_id |
| 2 | member | SELECT | 校验会员状态 | WHERE id = reservation.member_id → 取得 status |
| 3 | computer | SELECT | 校验机位状态 | WHERE id = reservation.computer_id AND status = 0 → 确认可用 |
| 4 | session | INSERT | 创建上机会话 | computer_id = 预约机位 ID；member_id = 会员 ID；session_no = 自动生成；auth_method = 1（刷卡）；tariff_plan_id = 机位费率方案；start_time = NOW()；status = 0（上机中）；version = 1 |
| 5 | computer | UPDATE | 更新机位状态 | status = 1（使用中）；version = version + 1 |
| 6 | reservation | UPDATE | 更新预约状态为已上机 | status = 2（已上机）；checked_in_at = NOW()；session_id = 新会话 ID；version = version + 1 |
| 7 | session_timing | INSERT | 写入首个计费时段 | session_id = 会话 ID；timing_type = 1（正常计费）；rate_type = 费率类型；rate_price = 单价；start_time = NOW() |

**异常处理**：
- 预约状态非已确认：拒绝上机，回滚事务。
- 预约机位已被占用：尝试分配同区域其他空闲机位，或拒绝上机。
- 会员状态异常：拒绝上机，回滚事务。
- 乐观锁冲突：重试事务。

---

### E-2.4 正常下机

会员或散客正常结束上机，系统结算费用、生成订单并完成支付。

**前置条件**：会话状态为上机中（`session.status = 0`）或临时下机（`session.status = 1`）。

**事务边界**：步骤 1~12 在同一事务中提交。

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | session | SELECT | 查询会话详情 | WHERE id = ? AND status IN (0, 1) AND deleted_at IS NULL → 取得 member_id、computer_id、tariff_plan_id、start_time、billed_minutes、total_amount、free_minutes |
| 2 | session_timing | INSERT | 写入最后一个计费时段（闭合） | session_id = 会话 ID；timing_type = 1（正常计费）；start_time = 上一段 end_time 或 session.start_time；end_time = NOW()；duration_minutes = 计算时长；rate_price = 费率单价；amount = 段费用 |
| 3 | session | UPDATE | 更新会话为已下机，汇总费用 | status = 2（已下机）；end_time = NOW()；total_amount = 累计总费用；discount_amount = 优惠金额；paid_amount = total_amount - discount_amount；version = version + 1 |
| 4 | billing_record | INSERT/UPDATE | 生成/更新最终计费结算记录 | session_id = 会话 ID；member_id = 会员 ID；computer_id = 机位 ID；record_no = 自动生成；tariff_plan_id = 费率方案；maintenance_minutes = 总计费时长；unit_price = 均价；total_amount = 总费用；discount_amount = 优惠；final_amount = 实收；billing_start = session.start_time；billing_end = NOW()；is_settled = 1；settled_at = NOW() |
| 5 | orders | INSERT | 创建上机结算订单 | order_no = 自动生成；order_type = 2（上机结算）；member_id = 会员 ID（散客为 NULL）；session_id = 会话 ID；total_amount = 总费用；discount_amount = 优惠金额；payable_amount = 应付金额；paid_amount = 实付金额；status = 1（已支付）；operator_id = 操作员工；paid_at = NOW() |
| 6 | order_item | INSERT | 写入订单明细（上机时长） | order_id = 订单 ID；item_type = 2（上机时长）/ 3（包时段）；product_name = '上机时长'；unit_price = 费率单价；quantity = 计费分钟数；subtotal = 小计金额 |
| 7 | payment_record | INSERT | 写入支付记录 | payment_no = 自动生成；order_id = 订单 ID；member_id = 会员 ID；payment_method = 支付方式（1-现金 / 2-会员余额）；total_amount = 实付金额；payment_status = 1（支付成功）；paid_at = NOW()；idempotent_key = 幂等键（如 order_no + payment_method） |
| 8 | member | UPDATE | （余额支付时）扣减余额与更新统计 | balance = balance - paid_amount；total_consumption = total_consumption + paid_amount；total_online_hours = total_online_hours + 计费时长（分钟）；version = version + 1 |
| 9 | member | SELECT | （积分获取）查询会员等级积分倍数 | WHERE id = member_id → 取得 level_id |
| 10 | member_level | SELECT | 查询积分倍数 | WHERE id = level_id → 取得 points_multiple |
| 11 | member_points_log | INSERT | （会员且积分 > 0）记录积分获取 | member_id = 会员 ID；points = floor(paid_amount * 兑换比例 * points_multiple)（正数）；balance_before = 当前可用积分；balance_after = balance_before + 获得积分；biz_type = 1（消费获得）；biz_id = 订单 ID；expire_at = NOW() + 积分有效期 |
| 12 | member | UPDATE | （积分获取）更新积分 | available_points = available_points + 获得积分；total_points = total_points + 获得积分；version = version + 1 |
| 13 | computer | UPDATE | 释放机位 | status = 0（空闲）；version = version + 1 |

**异常处理**：
- 会话不存在或已下机：抛出业务异常，回滚事务。
- 余额不足（余额支付场景）：提示更换支付方式或充值，回滚事务。
- 支付幂等冲突：通过 idempotent_key 唯一约束拦截重复支付。
- 乐观锁冲突：重试事务。

---

### E-2.5 临时下机（暂停）

会员临时离开，暂停计费并锁定机位。

**前置条件**：会话状态为上机中（`session.status = 0`）。

**事务边界**：步骤 1~3 在同一事务中提交。

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | session | SELECT | 查询当前会话 | WHERE id = ? AND status = 0 AND deleted_at IS NULL → 取得 pause_count、pause_duration |
| 2 | session | UPDATE | 暂停会话 | status = 1（临时下机）；pause_count = pause_count + 1；version = version + 1 |
| 3 | session_timing | INSERT | 写入暂停时段（闭合当前计费段） | session_id = 会话 ID；timing_type = 2（临时下机）；start_time = 上一段开始或 NOW()；end_time = NOW()；duration_minutes = 暂停前最后一段时长；amount = 段费用 |
| 4 | computer | UPDATE | 锁定机位 | status = 2（锁定）；version = version + 1 |

**异常处理**：
- 会话非上机中状态：拒绝操作，回滚事务。
- 暂停次数超过系统配置上限：拒绝暂停，提示直接下机。
- 乐观锁冲突：重试事务。

---

### E-2.6 恢复上机

从临时下机状态恢复上机，继续计费。

**前置条件**：会话状态为临时下机（`session.status = 1`）。

**事务边界**：步骤 1~3 在同一事务中提交。

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | session | SELECT | 查询暂停会话 | WHERE id = ? AND status = 1 AND deleted_at IS NULL → 取得 tariff_plan_id |
| 2 | session | UPDATE | 恢复上机 | status = 0（上机中）；version = version + 1 |
| 3 | session_timing | INSERT | 写入恢复计费时段 | session_id = 会话 ID；timing_type = 1（正常计费）；rate_type = 费率类型；rate_price = 当前费率单价；start_time = NOW() |
| 4 | computer | UPDATE | 恢复机位为使用中 | status = 1（使用中）；version = version + 1 |

**异常处理**：
- 会话非临时下机状态：拒绝操作，回滚事务。
- 机位已被其他会话占用（异常场景）：抛出异常，人工介入。
- 乐观锁冲突：重试事务。

---

### E-2.7 换机操作

会员在不停止会话的情况下更换机位，计费连续。

**前置条件**：会话状态为上机中（`session.status = 0`）；目标机位空闲（`computer.status = 0`）。

**事务边界**：步骤 1~5 在同一事务中提交。

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | session | SELECT | 查询当前会话 | WHERE id = ? AND status = 0 AND deleted_at IS NULL → 取得 computer_id（旧机位）、tariff_plan_id |
| 2 | computer | SELECT | 查询目标机位状态 | WHERE id = 新机位 ID AND status = 0 AND is_active = 1 AND deleted_at IS NULL |
| 3 | session_timing | INSERT | 写入换机结算段（闭合旧机位计费） | session_id = 会话 ID；timing_type = 1（正常计费）；end_time = NOW()；duration_minutes = 本段时长；amount = 段费用 |
| 4 | billing_record | INSERT | 生成换机前的阶段性计费记录 | session_id = 会话 ID；member_id = 会员 ID；computer_id = 旧机位 ID；record_no = 自动生成；tariff_plan_id = 费率方案；maintenance_minutes = 阶段计费时长；total_amount = 阶段费用；billing_start = 上一结算点；billing_end = NOW()；is_settled = 0（待最终结算） |
| 5 | session | UPDATE | 更新会话关联机位 | computer_id = 新机位 ID；version = version + 1 |
| 6 | computer | UPDATE | 旧机位释放 | status = 0（空闲）；version = version + 1（WHERE id = 旧机位 ID） |
| 7 | computer | UPDATE | 新机位占用 | status = 1（使用中）；version = version + 1（WHERE id = 新机位 ID） |
| 8 | session_timing | INSERT | 写入新机位计费起始段 | session_id = 会话 ID；timing_type = 1（正常计费）；rate_type = 新机位费率类型；rate_price = 新机位费率单价；start_time = NOW() |

**异常处理**：
- 目标机位不可用：拒绝换机，回滚事务。
- 会话非上机中状态：拒绝操作，回滚事务。
- 乐观锁冲突：重试事务。

---

### E-2.8 强制下机

管理员强制结束会员上机会话，立即结算费用。

**前置条件**：会话存在且状态为上机中或临时下机（`session.status IN (0, 1)`）；操作人具有强制下机权限。

**事务边界**：步骤 1~10 在同一事务中提交。

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | session | SELECT | 查询会话详情 | WHERE id = ? AND status IN (0, 1) AND deleted_at IS NULL → 取得 member_id、computer_id、total_amount 等 |
| 2 | session_timing | INSERT | 写入最后一个计费时段 | session_id = 会话 ID；timing_type = 1（正常计费）；end_time = NOW()；duration_minutes = 段时长；amount = 段费用 |
| 3 | session | UPDATE | 强制下机，汇总费用 | status = 3（强制下机）；end_time = NOW()；total_amount = 累计总费用；remark = '管理员强制下机'；version = version + 1 |
| 4 | billing_record | INSERT/UPDATE | 生成最终计费结算记录 | session_id = 会话 ID；total_amount = 总费用；billing_end = NOW()；is_settled = 1；settled_at = NOW() |
| 5 | orders | INSERT | 创建结算订单 | order_no = 自动生成；order_type = 2（上机结算）；member_id = 会员 ID；session_id = 会话 ID；total_amount = 总费用；payable_amount = 应付；paid_amount = 实付；status = 1（已支付）；paid_at = NOW() |
| 6 | order_item | INSERT | 写入订单明细 | order_id = 订单 ID；item_type = 2（上机时长）；product_name = '上机时长'；quantity = 计费分钟数；subtotal = 小计 |
| 7 | payment_record | INSERT | 写入支付记录 | payment_no = 自动生成；order_id = 订单 ID；member_id = 会员 ID；payment_method = 支付方式；total_amount = 实付；payment_status = 1（成功）；idempotent_key = 幂等键 |
| 8 | member | UPDATE | （余额支付）扣减余额与更新统计 | balance = balance - paid_amount；total_consumption = total_consumption + paid_amount；total_online_hours = total_online_hours + 计费时长；version = version + 1 |
| 9 | computer | UPDATE | 释放机位 | status = 0（空闲）；version = version + 1 |
| 10 | audit_log | INSERT | 记录强制下机审计 | biz_type = 'session'；biz_id = 会话 ID；action = 'update'；detail = {"status": {"old": 0/1, "new": 3}, "operator": "操作员姓名"} |

**异常处理**：
- 会话不存在或已结束：拒绝操作，回滚事务。
- 余额不足：根据业务策略允许欠费或记录待收款状态。
- 乐观锁冲突：重试事务。

---

### E-2.9 创建预约

会员预约指定机位或区域的上机时段。

**前置条件**：会员状态正常；目标机位空闲或尚未被预约（在预约时段内无冲突）。

**事务边界**：步骤 1~3 在同一事务中提交。

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | member | SELECT | 校验会员状态 | WHERE id = ? AND status = 1 AND deleted_at IS NULL |
| 2 | computer | SELECT | （指定机位时）校验机位可用性与预约冲突 | WHERE id = ? AND status = 0 AND is_active = 1 AND deleted_at IS NULL；同时查询 reservation 表是否有时间段冲突的预约：WHERE computer_id = ? AND status IN (0, 1) AND reservation_date = ? AND start_time < 新结束时间 AND end_time > 新开始时间 AND deleted_at IS NULL |
| 3 | reservation | INSERT | 创建预约记录 | member_id = 会员 ID；computer_id = 指定机位 ID（NULL 表示到店分配）；reservation_no = 自动生成；reservation_date = 预约日期；start_time = 预约开始时间；end_time = 预约结束时间；deposit_amount = 保证金（系统配置值）；status = 0（待确认）；version = 1 |

**异常处理**：
- 会员状态异常：拒绝预约，回滚事务。
- 机位不可用或时段冲突：拒绝预约，提示更换机位或时段。
- 会员存在未履约的超时预约：拒绝新预约，提示先处理历史预约。

---

### E-2.10 确认预约

管理员或系统确认会员的预约请求。

**前置条件**：预约记录状态为待确认（`reservation.status = 0`）。

**事务边界**：步骤 1~2 在同一事务中提交。

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | reservation | SELECT | 查询预约记录 | WHERE id = ? AND status = 0 AND deleted_at IS NULL |
| 2 | reservation | UPDATE | 确认预约 | status = 1（已确认）；version = version + 1 |

**异常处理**：
- 预约状态非待确认：拒绝操作，回滚事务。
- 乐观锁冲突：重试事务。

---

### E-2.11 取消预约

会员或管理员取消预约，如已缴纳保证金则退还。

**前置条件**：预约记录状态为待确认或已确认（`reservation.status IN (0, 1)`）。

**事务边界**：步骤 1~4 在同一事务中提交。

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | reservation | SELECT | 查询预约记录 | WHERE id = ? AND status IN (0, 1) AND deleted_at IS NULL → 取得 member_id、deposit_amount |
| 2 | reservation | UPDATE | 取消预约 | status = 3（已取消）；cancel_reason = 取消原因；version = version + 1 |
| 3 | member | UPDATE | （如有保证金需退还）增加余额 | balance = balance + deposit_amount；version = version + 1 |
| 4 | member_recharge | INSERT | （如有保证金退还）记录退还流水 | member_id = 会员 ID；recharge_no = 自动生成；recharge_amount = deposit_amount；bonus_amount = 0；total_amount = deposit_amount；balance_before = 原余额；balance_after = 原余额 + deposit_amount；payment_method = 2（会员余额）；status = 1（成功）；remark = '预约取消退还保证金' |

**异常处理**：
- 预约状态不允许取消（已上机/已取消/超时）：拒绝操作，回滚事务。
- 乐观锁冲突：重试事务。

---

### E-2.12 预约超时处理

定时任务扫描超时未到的预约，自动标记为超时并释放预留机位。

**前置条件**：定时任务触发（每 5 分钟执行一次）；预约的开始时间已过且超过宽限期（如 30 分钟），状态仍为已确认。

**事务边界**：每条预约记录在独立事务中处理。

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | reservation | SELECT | 扫描超时预约 | WHERE status = 1 AND start_time < DATE_SUB(NOW(), INTERVAL 30 MINUTE) AND deleted_at IS NULL → 取得 computer_id |
| 2 | reservation | UPDATE | 标记为超时未到 | status = 4（超时未到）；version = version + 1 |
| 3 | computer | UPDATE | （如预约指定了机位）释放机位 | status = 0（空闲）；version = version + 1（WHERE id = reservation.computer_id AND computer_id IS NOT NULL） |

**异常处理**：
- 会员已到店但系统未识别：人工确认后手动恢复预约状态。
- 乐观锁冲突：重试事务。

---

## 三、计费管理（7 个事件）

### E-3.1 心跳计费

客户端每 30 秒上报心跳，服务端更新计时并累计费用。

**前置条件**：会话状态为上机中（`session.status = 0`）；客户端心跳正常到达。

**事务边界**：步骤 1~4 在同一事务中提交（每次心跳一个短事务）。

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | session | SELECT | 查询当前会话状态 | WHERE id = ? AND status = 0 AND deleted_at IS NULL → 取得 billed_minutes、total_amount、tariff_plan_id、computer_id |
| 2 | session_timing | SELECT/UPDATE | 查询当前未闭合的计费段，计算时长 | WHERE session_id = ? AND end_time IS NULL AND timing_type = 1 AND deleted_at IS NULL → 计算 elapsed = NOW() - start_time（分钟） |
| 3 | session_timing | UPDATE | 更新当前计费段 | end_time = NOW()（临时更新用于计算）；duration_minutes = elapsed；amount = 按费率计算的费用；随后重新打开新段或继续本段（根据实现策略，若同一费率周期内可只 UPDATE 不闭合） |
| 4 | session | UPDATE | 更新会话累计数据 | billed_minutes = billed_minutes + 新增计费分钟；total_amount = 按费率重算的累计费用；version = version + 1 |
| 5 | billing_record | INSERT | 生成周期性计费记录（每 N 分钟一条） | session_id = 会话 ID；member_id = 会员 ID；computer_id = 机位 ID；record_no = 自动生成；tariff_plan_id = 费率方案；maintenance_minutes = 本周期计费时长；unit_price = 均价；total_amount = 本周期费用；billing_start = 上周期结束时间；billing_end = NOW()；is_settled = 0（待最终结算） |
| 6 | computer | UPDATE | 更新终端在线状态 | is_online = 1；version = version + 1 |

**异常处理**：
- 会话已结束：忽略心跳，不产生数据变更。
- 心跳超时（超过 5 分钟未收到）：触发离线检测，可能标记会话异常中断。
- 费率方案变更（动态调价）：闭合当前计费段，以新费率开启新段。
- 乐观锁冲突：重试事务。

---

### E-3.2 包时段计费

会员购买包时段套餐（如包夜、包上午、包周），一次性付费使用指定时长。

**前置条件**：会员状态正常或散客；目标机位空闲；包时费率方案可用。

**事务边界**：步骤 1~8 在同一事务中提交。

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | tariff_plan | SELECT | 查询包时费率方案 | WHERE id = ? AND plan_type = 2（包时）AND status = 1 AND deleted_at IS NULL |
| 2 | tariff_rate | SELECT | 查询包时价格 | WHERE plan_id = tariff_plan_id AND rate_type = 3（包时价）→ 取得 first_price（包时总价） |
| 3 | session | INSERT | 创建包时上机会话 | computer_id = 机位 ID；member_id = 会员 ID（散客为 NULL）；session_no = 自动生成；tariff_plan_id = 包时方案 ID；start_time = NOW()；expected_minutes = 包时时长（分钟）；total_amount = 包时价格；paid_amount = 包时价格；status = 0（上机中）；version = 1 |
| 4 | computer | UPDATE | 机位占用 | status = 1（使用中）；version = version + 1 |
| 5 | session_timing | INSERT | 写入包时计费段 | session_id = 会话 ID；timing_type = 1（正常计费）；rate_type = 2（包时）；rate_price = 包时单价；start_time = NOW()；end_time = NOW() + expected_minutes；duration_minutes = expected_minutes；amount = 包时总价 |
| 6 | orders | INSERT | 创建包时订单 | order_no = 自动生成；order_type = 2（上机结算）；member_id = 会员 ID；session_id = 会话 ID；total_amount = 包时价格；payable_amount = 包时价格；paid_amount = 包时价格；status = 1（已支付）；paid_at = NOW() |
| 7 | order_item | INSERT | 写入订单明细 | order_id = 订单 ID；item_type = 3（包时段）；product_name = 方案名称；unit_price = 包时价格；quantity = 1；subtotal = 包时价格 |
| 8 | payment_record | INSERT | 写入支付记录 | payment_no = 自动生成；order_id = 订单 ID；member_id = 会员 ID；payment_method = 支付方式；total_amount = 包时价格；payment_status = 1（成功）；idempotent_key = 幂等键 |
| 9 | member | UPDATE | （余额支付）扣减余额 | balance = balance - 包时价格；total_consumption = total_consumption + 包时价格；version = version + 1 |

**异常处理**：
- 包时方案已停用：拒绝操作，回滚事务。
- 余额不足：拒绝操作，提示充值。
- 支付幂等冲突：通过 idempotent_key 拦截。
- 乐观锁冲突：重试事务。

---

### E-3.3 包时段超时转按时

包时段时间用尽后会员未下机，自动切换为按时计费。

**前置条件**：会话为包时类型（`session.tariff_plan_id` 对应 plan_type = 2）；当前时间已超过包时段结束时间；会话状态仍为上机中。

**事务边界**：步骤 1~4 在同一事务中提交。

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | session | SELECT | 查询包时会话详情 | WHERE id = ? AND status = 0 AND deleted_at IS NULL → 取得 tariff_plan_id、start_time、expected_minutes |
| 2 | tariff_plan | SELECT | 查询该区域/机位的默认按时费率方案 | WHERE plan_type = 1（普通按时）AND status = 1 AND is_default = 1 AND deleted_at IS NULL → 取得按时方案 ID |
| 3 | tariff_rate | SELECT | 查询按时费率明细 | WHERE plan_id = 按时方案 ID → 取得 renewal_price（元/分钟） |
| 4 | session_timing | INSERT | 闭合包时段，写入新的按时计费起始段 | 先 INSERT 一条 rate_type = 2（包时）的闭合段（end_time = 包时到期时间）；再 INSERT 一条 rate_type = 1（按时）的新段，start_time = 包时到期时间；rate_price = 按时单价 |
| 5 | session | UPDATE | 更新会话费率方案 | tariff_plan_id = 按时方案 ID；version = version + 1 |
| 6 | billing_record | INSERT | 记录切换点的计费数据 | session_id = 会话 ID；tariff_plan_id = 原包时方案 ID；maintenance_minutes = 包时段时长；total_amount = 包时费用；billing_start = session.start_time；billing_end = 包时到期时间；is_settled = 0 |

**异常处理**：
- 会话已下机：跳过处理。
- 无默认可用的按时方案：抛出系统异常，记录告警，保持包时状态不变。
- 乐观锁冲突：重试事务。

---

### E-3.4 动态调价

定时任务根据当前时段自动切换各机位的费率方案（如白天/夜间费率切换）。

**前置条件**：定时任务触发（如每小时整点执行）；存在适用于当前时段的新费率方案。

**事务边界**：步骤 1~3 在同一事务中提交（批量更新）。

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | tariff_plan | SELECT | 匹配当前时段生效的费率方案 | WHERE status = 1 AND deleted_at IS NULL AND effective_start <= CURRENT_TIME AND effective_end > CURRENT_TIME AND (effective_days 包含当前星期 OR effective_days 为空) ORDER BY priority DESC |
| 2 | computer | UPDATE | 批量更新机位的费率方案 | tariff_plan_id = 新方案 ID；version = version + 1（WHERE is_active = 1 AND status IN (0, 1) AND (area_id 在新方案的 applicable_areas 中 OR applicable_areas 为空)） |
| 3 | audit_log | INSERT | 记录调价审计 | biz_type = 'tariff_plan'；action = 'update'；detail = {"old_plan_id": 旧方案, "new_plan_id": 新方案 ID, "affected_computers": 影响数量} |

**异常处理**：
- 无匹配的新费率方案：保持当前方案不变，跳过本次调价。
- 部分机位正在上机中：记录切换日志，实际费率切换在下次心跳时通过 session_timing 新段实现（不中断当前计费段）。
- 批量更新部分失败：记录失败机位，人工排查。

---

### E-3.5 手动优惠/减免

收银员在结算前手动为会员提供优惠折扣或减免。

**前置条件**：会话已下机或待结算状态；操作人具有优惠权限。

**事务边界**：步骤 1~3 在同一事务中提交。

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | session | SELECT | 查询会话费用 | WHERE id = ? AND deleted_at IS NULL → 取得 total_amount、discount_amount、paid_amount |
| 2 | session | UPDATE | 更新优惠金额 | discount_amount = 原 discount_amount + 新增优惠；paid_amount = total_amount - discount_amount；remark = 优惠说明；version = version + 1 |
| 3 | billing_record | UPDATE | 同步更新计费记录 | discount_amount = 新 discount_amount；final_amount = total_amount - discount_amount；version = version + 1（WHERE session_id = ? AND is_settled = 0） |
| 4 | audit_log | INSERT | 记录优惠审计 | biz_type = 'session'；biz_id = 会话 ID；action = 'update'；detail = {"discount_amount": {"old": 原值, "new": 新值}, "reason": "优惠原因", "operator": "操作员"} |

**异常处理**：
- 优惠后实付金额为负：限制 discount_amount 不超过 total_amount，拒绝操作。
- 会话已结算（is_settled = 1）：需先冲销结算记录再操作，或拒绝优惠。
- 乐观锁冲突：重试事务。

---

### E-3.6 优惠券抵扣

会员在结算时使用优惠券抵扣部分费用。

**前置条件**：会员持有状态为未使用的优惠券（`coupon.status = 0`）；优惠券未过期（`expire_at > NOW()`）；订单金额满足优惠券最低消费条件。

**事务边界**：步骤 1~4 在同一事务中提交。

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | coupon | SELECT | 查询优惠券信息 | WHERE id = ? AND member_id = ? AND status = 0 AND expire_at > NOW() AND deleted_at IS NULL → 取得 face_value、min_consume、coupon_type |
| 2 | orders | SELECT | 查询订单金额 | WHERE id = ? → 取得 total_amount，校验是否满足 min_consume |
| 3 | coupon | UPDATE | 标记优惠券已使用 | status = 1（已使用）；used_at = NOW()；used_order_id = 订单 ID；version = version + 1 |
| 4 | session | UPDATE | 更新会话优惠金额 | discount_amount = discount_amount + face_value（或按折扣率计算）；paid_amount = total_amount - discount_amount；version = version + 1 |
| 5 | orders | UPDATE | 更新订单优惠 | discount_amount = discount_amount + face_value；payable_amount = total_amount - discount_amount；version = version + 1 |
| 6 | billing_record | UPDATE | 同步更新计费记录 | discount_amount = 新值；final_amount = total_amount - discount_amount；version = version + 1 |

**异常处理**：
- 优惠券已使用/已过期/不属于该会员：拒绝使用，回滚事务。
- 订单金额不满足最低消费：拒绝使用，回滚事务。
- 优惠后金额为负：限制抵扣金额不超过 payable_amount。
- 乐观锁冲突：重试事务。

---

### E-3.7 余额不足扣费处理

上机过程中检测到会员余额不足以支付当前累计费用，系统发出通知并可能锁定机位。

**前置条件**：会话状态为上机中（`session.status = 0`）；会员余额低于预警阈值。

**事务边界**：步骤 1~3 在同一事务中提交。

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | session | SELECT | 查询会话与费用 | WHERE id = ? AND status = 0 AND deleted_at IS NULL → 取得 member_id、total_amount |
| 2 | member | SELECT | 查询会员余额 | WHERE id = member_id → 取得 balance |
| 3 | session | UPDATE | 标记余额不足 | remark = CONCAT(IFNULL(remark,''), '[余额不足预警]')；version = version + 1 |
| 4 | notification | INSERT | 发送余额不足通知 | notify_type = 3（会员推送）；title = '余额不足提醒'；content = '您的账户余额不足，请及时充值以避免断网'；target_type = 3（指定会员）；target_ids = [member_id]；published_at = NOW() |
| 5 | computer | UPDATE | （如系统配置自动锁机）锁定机位 | status = 2（锁定）；version = version + 1 |

**异常处理**：
- 会员已充值：在下次心跳检测时自动恢复（如机位被锁定则需管理员手动恢复）。
- 散客（member_id = NULL）：跳过余额检查，不发送通知。
- 通知发送失败：记录日志，不影响上机会话。

---

## 四、商品销售（6 个事件）

### E-4.1 散客商品购买

非会员顾客购买商品，使用现金支付。

**前置条件**：商品处于上架状态（`product.is_active = 1`）；库存充足（`inventory.available_quantity >= 购买数量`）。

**事务边界**：步骤 1~6 在同一事务中提交。

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | product | SELECT | 查询商品信息 | WHERE id = ? AND is_active = 1 AND deleted_at IS NULL → 取得 retail_price、product_name |
| 2 | inventory | SELECT | 查询库存 | WHERE product_id = ? AND deleted_at IS NULL → 取得 available_quantity、quantity |
| 3 | orders | INSERT | 创建订单 | order_no = 自动生成；order_type = 1（商品销售）；member_id = NULL（散客）；total_amount = 商品总价；discount_amount = 0.00；payable_amount = 商品总价；paid_amount = 商品总价；status = 1（已支付）；operator_id = 收银员；paid_at = NOW() |
| 4 | order_item | INSERT | 写入订单明细 | order_id = 订单 ID；item_type = 1（商品）；product_id = 商品 ID；product_name = 商品名称快照；unit_price = retail_price；quantity = 购买数量；subtotal = unit_price * quantity |
| 5 | payment_record | INSERT | 写入支付记录 | payment_no = 自动生成；order_id = 订单 ID；member_id = NULL；payment_method = 支付方式（1-现金）；total_amount = 实付金额；payment_status = 1（成功）；paid_at = NOW()；idempotent_key = 幂等键 |
| 6 | inventory | UPDATE | 扣减库存 | quantity = quantity - 购买数量；available_quantity = available_quantity - 购买数量；version = version + 1 |
| 7 | inventory_log | INSERT | 记录库存变动 | product_id = 商品 ID；change_type = 2（销售出库）；change_quantity = -购买数量；balance_before = 变动前数量；balance_after = 变动后数量；ref_biz_type = 'order'；ref_biz_id = 订单 ID |

**异常处理**：
- 商品已下架：拒绝购买，回滚事务。
- 库存不足：拒绝购买，回滚事务。
- 支付幂等冲突：通过 idempotent_key 拦截。
- 乐观锁冲突：重试事务。

---

### E-4.2 会员商品购买

会员购买商品，使用现金或会员余额支付，并获得消费积分。

**前置条件**：会员状态正常；商品上架且库存充足；余额支付时余额充足。

**事务边界**：步骤 1~10 在同一事务中提交。

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | member | SELECT | 查询会员信息 | WHERE id = ? AND status = 1 AND deleted_at IS NULL → 取得 balance、level_id |
| 2 | product | SELECT | 查询商品信息 | WHERE id = ? AND is_active = 1 AND deleted_at IS NULL → 取得 retail_price、member_price、product_name |
| 3 | inventory | SELECT | 查询库存 | WHERE product_id = ? AND deleted_at IS NULL → 取得 available_quantity |
| 4 | orders | INSERT | 创建订单 | order_no = 自动生成；order_type = 1（商品销售）；member_id = 会员 ID；total_amount = 商品总价（使用 member_price 如有）；discount_amount = 0.00；payable_amount = 应付；paid_amount = 实付；status = 1（已支付）；operator_id = 收银员；paid_at = NOW() |
| 5 | order_item | INSERT | 写入订单明细 | order_id = 订单 ID；item_type = 1（商品）；product_id = 商品 ID；product_name = 商品名称快照；unit_price = 实际单价；quantity = 数量；subtotal = 小计 |
| 6 | payment_record | INSERT | 写入支付记录 | payment_no = 自动生成；order_id = 订单 ID；member_id = 会员 ID；payment_method = 支付方式（1-现金/2-会员余额）；total_amount = 实付；payment_status = 1（成功）；idempotent_key = 幂等键 |
| 7 | inventory | UPDATE | 扣减库存 | quantity = quantity - 数量；available_quantity = available_quantity - 数量；version = version + 1 |
| 8 | inventory_log | INSERT | 记录库存变动 | product_id = 商品 ID；change_type = 2（销售出库）；change_quantity = -数量；balance_before = 变动前；balance_after = 变动后；ref_biz_type = 'order'；ref_biz_id = 订单 ID |
| 9 | member | UPDATE | （余额支付）扣减余额与更新消费统计 | balance = balance - paid_amount；total_consumption = total_consumption + paid_amount；version = version + 1 |
| 10 | member_level | SELECT | 查询积分倍数 | WHERE id = member.level_id → 取得 points_multiple |
| 11 | member_points_log | INSERT | 记录消费积分 | member_id = 会员 ID；points = floor(paid_amount * 兑换比例 * points_multiple)（正数）；balance_before = 当前可用积分；balance_after = balance_before + 获得积分；biz_type = 1（消费获得）；biz_id = 订单 ID；expire_at = NOW() + 积分有效期 |
| 12 | member | UPDATE | 更新积分 | available_points = available_points + 获得积分；total_points = total_points + 获得积分；version = version + 1 |

**异常处理**：
- 会员状态异常：拒绝购买，回滚事务。
- 余额不足（余额支付）：拒绝交易，提示更换支付方式或充值。
- 库存不足：拒绝购买，回滚事务。
- 支付幂等冲突：通过 idempotent_key 拦截。
- 乐观锁冲突：重试事务。

---

### E-4.3 套餐购买

会员或散客购买套餐（如零食套餐、饮料套餐），套餐包含主商品和子商品。

**前置条件**：套餐商品上架且有效；所有子商品库存充足。

**事务边界**：步骤 1~8 在同一事务中提交。

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | product | SELECT | 查询套餐商品信息 | WHERE id = ? AND is_active = 1 AND deleted_at IS NULL → 取得 retail_price、member_price、product_name |
| 2 | inventory | SELECT | 查询套餐及所有子商品库存 | WHERE product_id IN (套餐 ID, 子商品 ID 列表) AND deleted_at IS NULL → 逐一校验 available_quantity |
| 3 | orders | INSERT | 创建套餐订单 | order_no = 自动生成；order_type = 4（套餐）；member_id = 会员 ID（散客为 NULL）；total_amount = 套餐价格；payable_amount = 套餐价格；paid_amount = 套餐价格；status = 1（已支付）；paid_at = NOW() |
| 4 | order_item | INSERT | 写入套餐主明细 | order_id = 订单 ID；item_type = 4（套餐）；product_id = 套餐商品 ID；product_name = 套餐名称快照；unit_price = 套餐价格；quantity = 1；subtotal = 套餐价格 |
| 5 | order_item | INSERT | 写入子商品明细（每个子商品一条） | order_id = 订单 ID；item_type = 1（商品）；product_id = 子商品 ID；product_name = 子商品名称快照；unit_price = 子商品单价；quantity = 子商品数量；subtotal = 0（已含在套餐价中）；discount = 子商品原价 * 数量（标记套餐优惠分摊） |
| 6 | payment_record | INSERT | 写入支付记录 | payment_no = 自动生成；order_id = 订单 ID；member_id = 会员 ID；payment_method = 支付方式；total_amount = 套餐价格；payment_status = 1（成功）；idempotent_key = 幂等键 |
| 7 | inventory | UPDATE | 扣减每个子商品库存（逐条 UPDATE） | quantity = quantity - 子商品数量；available_quantity = available_quantity - 子商品数量；version = version + 1（WHERE product_id = 子商品 ID） |
| 8 | inventory_log | INSERT | 记录每个子商品的库存变动（逐条 INSERT） | product_id = 子商品 ID；change_type = 2（销售出库）；change_quantity = -子商品数量；balance_before = 变动前；balance_after = 变动后；ref_biz_type = 'order'；ref_biz_id = 订单 ID |
| 9 | member | UPDATE | （会员余额支付）扣减余额 | balance = balance - 套餐价格；total_consumption = total_consumption + 套餐价格；version = version + 1 |

**异常处理**：
- 任一子商品库存不足：拒绝购买，回滚事务。
- 套餐已下架：拒绝购买，回滚事务。
- 乐观锁冲突：重试事务。

---

### E-4.4 机位配送下单

会员通过前台向收银员点单，商品配送至机位。

**前置条件**：会员状态正常且有进行中的会话；商品上架且库存充足。

**事务边界**：步骤 1~9 在同一事务中提交。

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | member | SELECT | 查询会员信息 | WHERE id = ? AND status = 1 AND deleted_at IS NULL → 取得 balance、level_id |
| 2 | session | SELECT | 查询会员当前会话（获取机位） | WHERE member_id = ? AND status = 0 AND deleted_at IS NULL → 取得 computer_id |
| 3 | product | SELECT | 查询商品信息 | WHERE id = ? AND is_active = 1 AND deleted_at IS NULL → 取得 retail_price、member_price |
| 4 | inventory | SELECT | 查询库存 | WHERE product_id = ? AND deleted_at IS NULL → 取得 available_quantity |
| 5 | orders | INSERT | 创建配送订单 | order_no = 自动生成；order_type = 1（商品销售）；member_id = 会员 ID；session_id = 会话 ID；total_amount = 商品总价；payable_amount = 应付；paid_amount = 实付；status = 1（已支付）；paid_at = NOW() |
| 6 | order_item | INSERT | 写入订单明细 | order_id = 订单 ID；item_type = 1（商品）；product_id = 商品 ID；product_name = 快照；unit_price = 实际单价；quantity = 数量；subtotal = 小计 |
| 7 | payment_record | INSERT | 写入支付记录（余额支付） | payment_no = 自动生成；order_id = 订单 ID；member_id = 会员 ID；payment_method = 2（会员余额）；total_amount = 实付；payment_status = 1（成功）；idempotent_key = 幂等键 |
| 8 | inventory | UPDATE | 扣减库存 | quantity = quantity - 数量；available_quantity = available_quantity - 数量；version = version + 1 |
| 9 | inventory_log | INSERT | 记录库存变动 | product_id = 商品 ID；change_type = 2（销售出库）；change_quantity = -数量；balance_before = 变动前；balance_after = 变动后；ref_biz_type = 'order'；ref_biz_id = 订单 ID |
| 10 | member | UPDATE | 扣减余额与更新消费统计 | balance = balance - paid_amount；total_consumption = total_consumption + paid_amount；version = version + 1 |
| 11 | member_points_log | INSERT | 记录消费积分 | member_id = 会员 ID；points = 获得积分；balance_before = 当前积分；balance_after = 新积分；biz_type = 1（消费获得）；biz_id = 订单 ID |
| 12 | member | UPDATE | 更新积分 | available_points = available_points + 获得积分；total_points = total_points + 获得积分；version = version + 1 |

**异常处理**：
- 会员无进行中会话：拒绝下单，提示先上机。
- 余额不足：拒绝下单，提示充值。
- 库存不足：拒绝下单，回滚事务。
- 乐观锁冲突：重试事务。

---

### E-4.5 商品全额退货

会员退回已购商品，全额退款，恢复库存并回收积分。

**前置条件**：订单状态为已支付（`orders.status = 1`）；退货商品在可退期限内。

**事务边界**：步骤 1~9 在同一事务中提交。

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | orders | SELECT | 查询原订单 | WHERE id = ? AND status = 1 AND deleted_at IS NULL → 取得 member_id、total_amount、paid_amount |
| 2 | orders | UPDATE | 更新订单状态为已退款 | status = 3（已退款）；version = version + 1 |
| 3 | refund_record | INSERT | 写入退款记录 | refund_no = 自动生成；order_id = 订单 ID；payment_id = 原支付记录 ID；member_id = 会员 ID；refund_amount = paid_amount；refund_type = 1（全额退款）；refund_reason = 退货原因；status = 2（已完成）；operator_id = 操作员工 |
| 4 | payment_record | UPDATE | 更新原支付记录 | refund_amount = refund_amount + paid_amount；payment_status = 3（已退款）；version = version + 1 |
| 5 | order_item | SELECT | 查询订单明细获取商品信息 | WHERE order_id = ? AND item_type = 1 AND deleted_at IS NULL → 取得 product_id、quantity |
| 6 | inventory | UPDATE | 恢复库存（逐商品 UPDATE） | quantity = quantity + 退货数量；available_quantity = available_quantity + 退货数量；version = version + 1 |
| 7 | inventory_log | INSERT | 记录退货入库 | product_id = 商品 ID；change_type = 6（退货入库）；change_quantity = +退货数量；balance_before = 变动前；balance_after = 变动后；ref_biz_type = 'refund'；ref_biz_id = 退款记录 ID |
| 8 | member | UPDATE | （余额支付时）退还余额 | balance = balance + refund_amount；total_consumption = total_consumption - refund_amount；version = version + 1 |
| 9 | member_recharge | INSERT | （余额退还时）记录退还流水 | member_id = 会员 ID；recharge_no = 自动生成；recharge_amount = refund_amount；bonus_amount = 0；total_amount = refund_amount；balance_before = 原余额；balance_after = 原余额 + refund_amount；payment_method = 2（会员余额）；status = 1；remark = '商品退货退款' |
| 10 | member_points_log | INSERT | 回收消费获得的积分 | member_id = 会员 ID；points = -原获得积分（负数）；balance_before = 当前可用积分；balance_after = balance_before - 原积分；biz_type = 3（兑换消耗）/ 5（手动调整）；biz_id = 退款记录 ID；remark = '退货积分回收' |
| 11 | member | UPDATE | 更新积分 | available_points = available_points - 原获得积分；version = version + 1 |

**异常处理**：
- 订单已退款：拒绝重复退款，回滚事务。
- 退货商品已损坏不可再售：库存恢复至 quantity 但不增加 available_quantity（标记为损耗），或根据业务策略处理。
- 积分已部分使用导致回收后为负：将 available_points 置为 0，记录实际回收量。
- 乐观锁冲突：重试事务。

---

### E-4.6 商品部分退货

会员退回订单中的部分商品，按比例退款。

**前置条件**：订单状态为已支付（`orders.status = 1`）；退货商品在可退期限内。

**事务边界**：步骤 1~10 在同一事务中提交。

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | orders | SELECT | 查询原订单 | WHERE id = ? AND status IN (1, 2) AND deleted_at IS NULL → 取得 member_id、total_amount、paid_amount、status |
| 2 | order_item | SELECT | 查询退货商品明细 | WHERE order_id = ? AND product_id = ? AND deleted_at IS NULL → 取得 quantity、unit_price、subtotal |
| 3 | orders | UPDATE | 更新订单状态与金额 | status = 2（部分退款）（若全部退完则 = 3）；paid_amount = paid_amount - 退款金额；version = version + 1 |
| 4 | refund_record | INSERT | 写入退款记录 | refund_no = 自动生成；order_id = 订单 ID；payment_id = 原支付记录 ID；member_id = 会员 ID；refund_amount = 退款金额；refund_type = 2（部分退款）；refund_reason = 退货原因；status = 2（已完成） |
| 5 | payment_record | UPDATE | 更新原支付记录退款金额 | refund_amount = refund_amount + 退款金额；version = version + 1（如全额退完则 payment_status = 3） |
| 6 | inventory | UPDATE | 恢复退货商品库存 | quantity = quantity + 退货数量；available_quantity = available_quantity + 退货数量；version = version + 1 |
| 7 | inventory_log | INSERT | 记录退货入库 | product_id = 商品 ID；change_type = 6（退货入库）；change_quantity = +退货数量；balance_before = 变动前；balance_after = 变动后；ref_biz_type = 'refund'；ref_biz_id = 退款记录 ID |
| 8 | member | UPDATE | （余额支付时）退还余额 | balance = balance + 退款金额；total_consumption = total_consumption - 退款金额；version = version + 1 |
| 9 | member_points_log | INSERT | （按比例）回收积分 | member_id = 会员 ID；points = -按比例回收积分（负数）；balance_before = 当前可用积分；balance_after = balance_before - 回收积分；biz_type = 5（手动调整）；biz_id = 退款记录 ID；remark = '部分退货积分回收' |
| 10 | member | UPDATE | 更新积分 | available_points = available_points - 回收积分；version = version + 1 |

**异常处理**：
- 退货金额超过已付金额：拒绝操作，回滚事务。
- 退货数量超过购买数量：拒绝操作，回滚事务。
- 乐观锁冲突：重试事务。

---

## 五、库存管理（7 个事件）

### E-5.1 创建采购单

管理员创建采购单，记录采购商品明细。

**前置条件**：供应商存在且有效（`supplier.is_active = 1`）；采购商品存在。

**事务边界**：步骤 1~3 在同一事务中提交。

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | supplier | SELECT | 校验供应商有效性 | WHERE id = ? AND is_active = 1 AND deleted_at IS NULL |
| 2 | product | SELECT | 校验采购商品存在（逐条） | WHERE id IN (商品 ID 列表) AND deleted_at IS NULL |
| 3 | purchase_order | INSERT | 创建采购单 | supplier_id = 供应商 ID；order_no = 自动生成；total_amount = SUM(各明细 quantity * unit_price)；status = 0（待审核）；remark = 备注；version = 1 |
| 4 | purchase_order_item | INSERT | 写入采购明细（每条商品一行） | order_id = 采购单 ID；product_id = 商品 ID；quantity = 采购数量；unit_price = 采购单价；total_price = quantity * unit_price；received_quantity = 0 |

**异常处理**：
- 供应商不存在或已停用：拒绝创建，回滚事务。
- 商品不存在：拒绝创建，回滚事务。
- 采购数量或单价为负/零：校验失败，拒绝创建。

---

### E-5.2 采购单审核

管理员审核采购单，审核通过后方可执行入库。

**前置条件**：采购单状态为待审核（`purchase_order.status = 0`）；审核人具有审核权限。

**事务边界**：步骤 1~2 在同一事务中提交。

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | purchase_order | SELECT | 查询采购单 | WHERE id = ? AND status = 0 AND deleted_at IS NULL |
| 2 | purchase_order | UPDATE | 审核通过 | status = 1（已审核）；audit_by = 审核人 ID；audit_at = NOW()；version = version + 1 |

**异常处理**：
- 采购单状态非待审核：拒绝操作，回滚事务。
- 乐观锁冲突：重试事务。

---

### E-5.3 采购入库

采购商品到货，执行入库操作，更新库存。

**前置条件**：采购单状态为已审核（`purchase_order.status = 1`）；实际到货数量与采购数量一致（或允许部分入库）。

**事务边界**：步骤 1~5 在同一事务中提交。

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | purchase_order | SELECT | 查询采购单及明细 | WHERE id = ? AND status = 1 AND deleted_at IS NULL → 取得明细列表 |
| 2 | purchase_order | UPDATE | 更新采购单状态 | status = 2（已入库）；version = version + 1 |
| 3 | purchase_order_item | UPDATE | 更新各明细已入库数量（逐条） | received_quantity = quantity（全额入库）或实际到货数量；version = version + 1 |
| 4 | inventory | SELECT | 查询各商品当前库存 | WHERE product_id IN (商品 ID 列表) AND deleted_at IS NULL |
| 5 | inventory | UPDATE | 增加库存（逐商品 UPDATE） | quantity = quantity + 入库数量；available_quantity = available_quantity + 入库数量；version = version + 1 |
| 6 | inventory_log | INSERT | 记录采购入库（逐商品 INSERT） | product_id = 商品 ID；batch_no = 批次号；change_type = 1（采购入库）；change_quantity = +入库数量；balance_before = 变动前数量；balance_after = 变动后数量；ref_biz_type = 'purchase_order'；ref_biz_id = 采购单 ID；remark = '采购入库' |

**异常处理**：
- 采购单状态非已审核：拒绝入库，回滚事务。
- 部分商品入库数量不符：记录实际数量，采购单保持已入库状态，差异人工处理。
- 乐观锁冲突：重试事务。

---

### E-5.4 取消采购单

管理员取消未入库的采购单。

**前置条件**：采购单状态为待审核或已审核（`purchase_order.status IN (0, 1)`），尚未入库。

**事务边界**：步骤 1~2 在同一事务中提交。

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | purchase_order | SELECT | 查询采购单 | WHERE id = ? AND status IN (0, 1) AND deleted_at IS NULL |
| 2 | purchase_order | UPDATE | 取消采购单 | status = 3（已取消）；version = version + 1 |

**异常处理**：
- 采购单已入库或已取消：拒绝操作，回滚事务。
- 乐观锁冲突：重试事务。

---

### E-5.5 库存盘点

管理员执行库存盘点，根据实际盘点结果处理盘盈或盘亏。

**前置条件**：操作人具有库存管理权限；盘点数据已录入。

**事务边界**：每个商品的盘点调整在独立事务中提交。

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | inventory | SELECT | 查询商品当前库存 | WHERE product_id = ? AND deleted_at IS NULL → 取得 quantity、available_quantity |
| 2 | inventory | UPDATE | 根据盘点结果调整库存 | quantity = 实际盘点数量；available_quantity = 实际盘点数量 - frozen_quantity；version = version + 1 |
| 3 | inventory_log | INSERT | 记录盘盈 | product_id = 商品 ID；change_type = 3（盘盈）；change_quantity = 盘盈数量（正数）；balance_before = 原数量；balance_after = 新数量；remark = '库存盘点调整' |
| 4 | inventory_log | INSERT | 记录盘亏（如实际数量 < 系统数量） | product_id = 商品 ID；change_type = 4（盘亏）；change_quantity = 盘亏数量（负数）；balance_before = 原数量；balance_after = 新数量；remark = '库存盘点调整' |

**异常处理**：
- 商品不存在：跳过该条目，记录异常日志。
- 盘点数量与系统数量一致：不产生变更记录。
- 乐观锁冲突：重试事务。

---

### E-5.6 损耗登记

登记商品损耗（如过期、损坏），减少库存。

**前置条件**：商品存在；当前可用库存大于等于损耗数量。

**事务边界**：步骤 1~3 在同一事务中提交。

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | inventory | SELECT | 查询当前库存 | WHERE product_id = ? AND deleted_at IS NULL → 取得 quantity、available_quantity |
| 2 | inventory | UPDATE | 扣减库存 | quantity = quantity - 损耗数量；available_quantity = available_quantity - 损耗数量；version = version + 1 |
| 3 | inventory_log | INSERT | 记录损耗 | product_id = 商品 ID；change_type = 5（损耗）；change_quantity = -损耗数量；balance_before = 变动前数量；balance_after = 变动后数量；remark = 损耗原因 |

**异常处理**：
- 库存不足：拒绝登记，回滚事务。
- 乐观锁冲突：重试事务。

---

### E-5.7 库存预警处理

定时任务扫描库存，对低于最低库存或高于最高库存的商品发送预警通知。

**前置条件**：定时任务触发（如每小时执行一次）。

**事务边界**：每条预警通知在独立事务中提交（批量扫描，逐条通知）。

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | inventory | SELECT | 扫描库存预警商品 | WHERE deleted_at IS NULL AND (available_quantity <= min_stock OR available_quantity >= max_stock) → 取得 product_id、quantity、available_quantity、min_stock、max_stock |
| 2 | product | SELECT | 查询商品名称 | WHERE id IN (预警商品 ID 列表) AND deleted_at IS NULL → 取得 product_name |
| 3 | notification | INSERT | 写入库存预警通知 | notify_type = 2（员工通知）；title = '库存预警'；content = 包含商品名称、当前库存、预警阈值；target_type = 0（全部）/ 1（指定角色，如店长）；published_at = NOW() |

**异常处理**：
- 无预警商品：不产生通知，静默完成。
- 同一商品短时间内重复预警：通过去重逻辑（如检查最近一条同类型通知）避免重复推送。
- 通知写入失败：记录日志，不影响其他预警处理。

---

## 六、收银管理（7 个事件）

### E-6.1 开班

收银员上岗前执行开班操作，系统记录班次信息与备用金，作为当班收银的起始快照。

**前置条件**：
- 员工已登录系统，角色为收银员或店长
- 该员工当前无"进行中"状态的班次（同一时间仅允许一个活跃班次）

**事务边界**：步骤 1~3 在同一事务中

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | employee | SELECT | 验证员工身份与在职状态 | WHERE id = #{employee_id} AND status = 1 AND is_active = 1 AND deleted_at IS NULL |
| 2 | cashier_shift | SELECT | 检查是否存在未结班记录，防止重复开班 | WHERE employee_id = #{employee_id} AND status = 0 AND deleted_at IS NULL |
| 3 | cashier_shift | INSERT | 创建班次记录，记录开班时间与备用金 | employee_id = #{employee_id}, shift_no = 系统生成班次编号, open_at = CURRENT_TIMESTAMP, opening_balance = #{输入备用金}, status = 0（进行中）, version = 1 |

**异常处理**：
- 员工不存在或已离职/停用：抛出业务异常，事务不启动
- 存在未结班记录：提示"请先完成上一班次结班"，拒绝开班
- 备用金为负数：前端校验拦截，不进入事务

---

### E-6.2 交班结班

收银员结束当班，系统汇总本班次现金收支，收银员录入实际清点金额，系统计算长短款差异。

**前置条件**：
- 当前存在"进行中"（status = 0）的班次记录
- 收银员已清点现金并录入实际金额

**事务边界**：步骤 1~4 在同一事务中

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | cashier_shift | SELECT | 查询当前进行中的班次记录（含乐观锁版本号） | WHERE employee_id = #{employee_id} AND status = 0 AND deleted_at IS NULL |
| 2 | payment_record | SELECT | 汇总本班次内现金支付收入（payment_method = 3 且 payment_status = 1） | SELECT COALESCE(SUM(total_amount), 0) FROM payment_record WHERE operator_id = #{employee_id} AND payment_method = 3 AND payment_status = 1 AND paid_at >= #{open_at} AND paid_at <= CURRENT_TIMESTAMP AND deleted_at IS NULL → 结果记为 cash_income |
| 3 | refund_record | SELECT | 汇总本班次内现金退款金额（refund_method = 2 且 status = 2） | SELECT COALESCE(SUM(refund_amount), 0) FROM refund_record WHERE operator_id = #{employee_id} AND refund_method = 2 AND status = 2 AND created_at >= #{open_at} AND deleted_at IS NULL → 结果记为 cash_expenditure |
| 4 | cashier_shift | UPDATE | 更新班次为已结班，写入汇总数据 | status = 1（已结班）, close_at = CURRENT_TIMESTAMP, cash_income = #{cash_income}, cash_expenditure = #{cash_expenditure}, cash_expected = opening_balance + cash_income - cash_expenditure, cash_actual = #{收银员录入实际金额}, cash_diff = cash_actual - cash_expected, total_income = 本班次所有已支付订单总额, order_count = 本班次订单数, version = version + 1（乐观锁校验） |

**异常处理**：
- 无进行中班次：提示"当前无活跃班次"，拒绝结班
- 乐观锁冲突（version 不匹配）：提示"数据已被修改，请刷新后重试"，事务回滚
- 本班次无订单数据：允许结班，cash_income / total_income / order_count 均为 0

---

### E-6.3 结班审核

店长或主管审核收银员提交的结班数据，确认长短款差异。

**前置条件**：
- 目标班次状态为"已结班"（status = 1）
- 操作人具有审核权限（角色为店长或主管）

**事务边界**：步骤 1~2 在同一事务中

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | cashier_shift | SELECT | 查询目标班次记录，确认状态为已结班 | WHERE id = #{shift_id} AND status = 1 AND deleted_at IS NULL |
| 2 | cashier_shift | UPDATE | 将班次标记为已审核，记录审核人 | status = 2（已审核）, audit_by = #{当前操作员工 ID}, version = version + 1 |

**异常处理**：
- 班次状态非"已结班"：提示"班次状态不正确，无法审核"，事务回滚
- 操作人无审核权限：在业务层拦截，不进入事务
- 乐观锁冲突：提示刷新重试，事务回滚

---

### E-6.4 日结

营业日结束后，系统（定时任务或手动触发）自动汇总当日所有班次与业务数据，生成日结报告。

**前置条件**：
- 当日所有班次均已审核（status = 2）
- 当日日期尚无日结记录（或需重新生成）

**事务边界**：步骤 1~6 在同一事务中

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | daily_settlement | SELECT | 检查当日是否已存在日结记录 | WHERE settle_date = #{营业日期} AND deleted_at IS NULL |
| 2 | cashier_shift | SELECT | 汇总当日所有已审核班次的营收数据 | SELECT SUM(total_income) AS total_income, SUM(order_count) AS order_count, SUM(cash_income) AS cash_income, SUM(cash_expenditure) AS cash_expenditure FROM cashier_shift WHERE DATE(open_at) = #{营业日期} AND status = 2 AND deleted_at IS NULL |
| 3 | orders | SELECT | 按订单类型汇总当日收入 | SELECT order_type, SUM(payable_amount) AS type_revenue, COUNT(*) AS type_count FROM orders WHERE DATE(paid_at) = #{营业日期} AND status IN (1, 2) AND deleted_at IS NULL GROUP BY order_type → 拆分为 online_revenue（order_type=2）、product_revenue（order_type=1）、recharge_revenue（order_type=3） |
| 4 | payment_record | SELECT | 按支付方式汇总当日支付金额 | SELECT payment_method, SUM(total_amount) AS method_amount FROM payment_record WHERE DATE(paid_at) = #{营业日期} AND payment_status = 1 AND deleted_at IS NULL GROUP BY payment_method → 拆分为 cash_amount（method=1）、balance_amount（method=2） |
| 5 | session | SELECT | 统计当日上机次数、最高并发、平均上机率 | SELECT COUNT(*) AS total_sessions, MAX(concurrent_count) AS peak_concurrent, AVG(occupancy_rate) AS avg_occupancy_rate FROM session WHERE DATE(start_time) = #{营业日期} AND deleted_at IS NULL；同时 SELECT SUM(total_recharge) AS total_recharge FROM member_recharge WHERE DATE(paid_at) = #{营业日期} AND status = 1 AND deleted_at IS NULL；SELECT SUM(refund_amount) AS total_refund FROM refund_record WHERE DATE(created_at) = #{营业日期} AND status = 2 AND deleted_at IS NULL |
| 6 | daily_settlement | INSERT | 写入日结记录 | settle_date = #{营业日期}, settle_no = 系统生成日结编号, total_revenue = online_revenue + product_revenue + recharge_revenue, online_revenue = #{步骤 3 结果}, product_revenue = #{步骤 3 结果}, recharge_revenue = #{步骤 3 结果}, total_recharge = #{步骤 5 充值总额}, total_refund = #{步骤 5 退款总额}, total_orders = #{步骤 2 订单总数}, total_sessions = #{步骤 5 上机次数}, peak_concurrent = #{步骤 5 最高并发}, avg_occupancy_rate = #{步骤 5 平均上机率}, cash_amount = #{步骤 4 结果}, balance_amount = #{步骤 4 结果}, status = 0（待确认）, version = 1 |

**异常处理**：
- 当日已有日结记录且状态为"已确认"：拒绝重新生成，提示"当日已确认日结，不可重复操作"
- 当日无已审核班次：允许生成，所有金额字段为 0，在 remark 中标注"当日无已审核班次"
- 聚合查询返回 NULL：使用 COALESCE 兜底为 0

---

### E-6.5 日结确认

店长或主管确认当日日结数据，确认后日结记录不可修改。

**前置条件**：
- 目标日结记录状态为"待确认"（status = 0）
- 操作人具有日结确认权限

**事务边界**：步骤 1~2 在同一事务中

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | daily_settlement | SELECT | 查询目标日结记录，确认状态为待确认 | WHERE id = #{settlement_id} AND status = 0 AND deleted_at IS NULL |
| 2 | daily_settlement | UPDATE | 标记日结为已确认，记录确认人与时间 | status = 1（已确认）, confirm_by = #{当前操作员工 ID}, confirmed_at = CURRENT_TIMESTAMP, version = version + 1 |

**异常处理**：
- 日结状态非"待确认"：提示"日结状态不正确"，事务回滚
- 乐观锁冲突：提示刷新重试，事务回滚
- 已确认的日结记录不可再修改（业务层硬限制）

---

### E-6.6 退款审批

管理员审核退款申请，决定批准或拒绝。

**前置条件**：
- 退款记录状态为"待审核"（status = 0）
- 操作人具有退款审批权限

**事务边界**：步骤 1~2 在同一事务中

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | refund_record | SELECT | 查询退款申请详情，确认状态为待审核 | WHERE id = #{refund_id} AND status = 0 AND deleted_at IS NULL |
| 2 | refund_record | UPDATE | 写入审核结果 | **审批通过**：status = 1（已审核）, audit_by = #{当前操作员工 ID}, audit_at = CURRENT_TIMESTAMP, version = version + 1；**审批拒绝**：status = 3（已拒绝）, audit_by = #{当前操作员工 ID}, audit_at = CURRENT_TIMESTAMP, version = version + 1 |

**异常处理**：
- 退款记录不存在或状态非"待审核"：提示"退款单状态不正确"，事务回滚
- 原订单不存在或已被退款：提示"原订单异常"，事务回滚
- 退款金额超过原订单已付金额：业务层校验拦截
- 乐观锁冲突：提示刷新重试

---

### E-6.7 退款执行

执行已审核通过的退款，资金按原路退回。涉及余额变动时通过流水表记录。

**前置条件**：
- 退款记录状态为"已审核"（status = 1）
- 原支付记录存在且已支付成功

**事务边界**：步骤 1~7 在同一事务中

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | refund_record | SELECT | 查询退款详情，确认状态为已审核 | WHERE id = #{refund_id} AND status = 1 AND deleted_at IS NULL |
| 2 | payment_record | SELECT | 查询原支付记录，确认支付成功且退款金额未超限 | WHERE id = #{payment_id} AND payment_status = 1 AND deleted_at IS NULL；校验 refund_amount + #{本次退款金额} <= total_amount |
| 3 | payment_record | UPDATE | 更新原支付记录的已退款金额；若全额退款则更新状态 | refund_amount = refund_amount + #{本次退款金额}；若 refund_amount = total_amount 则额外更新 payment_status = 3（已退款）, version = version + 1 |
| 4 | refund_record | UPDATE | 标记退款已完成 | status = 2（已完成）, version = version + 1 |
| 5 | orders | UPDATE | 更新原订单状态（全额退款→已退款，部分退款→部分退款） | 若原订单全额退款：status = 3（已退款）；若部分退款：status = 2（部分退款）, version = version + 1 |
| 6 | member | SELECT | 若退款方式为"退余额"（refund_method = 3），查询会员当前余额与版本号 | WHERE id = #{member_id} AND deleted_at IS NULL |
| 7 | member | UPDATE | 若退款方式为"退余额"，恢复会员余额（通过流水表同步记录） | balance = balance + #{退款金额}, version = version + 1 |
| 8 | member_recharge | INSERT | 若退款方式为"退余额"，写入余额变动流水（负数表示退回） | member_id = #{member_id}, recharge_no = 系统生成退款流水编号, recharge_amount = -#{退款金额}, bonus_amount = 0.00, total_amount = -#{退款金额}, balance_before = #{步骤 6 查询的当前余额}, balance_after = #{步骤 7 更新后的余额}, payment_method = 原支付方式, status = 4（退款冲正）, remark = '退款单号: #{refund_no}' |

**异常处理**：
- 退款状态非"已审核"：提示"退款单状态不正确"，事务回滚
- 原支付记录不存在或状态非"支付成功"：提示"原支付记录异常"，事务回滚
- 退款金额超过原支付可退金额：提示"退款金额超限"，事务回滚
- 退余额场景下会员不存在：提示"会员信息异常"，事务回滚
- 乐观锁冲突（payment_record / member 表）：提示数据已被修改，全部回滚
- 退款操作失败：捕获异常，保持 refund_record 为"已审核"状态，支持人工重试

---

## 七、员工管理（8 个事件）

### E-7.1 员工入职

管理员在系统中创建新员工账号，分配初始信息。

**前置条件**：
- 操作人具有员工管理权限
- 员工手机号、工号在系统中唯一

**事务边界**：步骤 1~3 在同一事务中

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | employee | SELECT | 校验工号与手机号唯一性 | WHERE (employee_no = #{工号} OR phone = #{手机号}) AND deleted_at IS NULL |
| 2 | employee | INSERT | 创建员工账号 | employee_no = #{工号}, real_name = #{姓名}, phone = #{手机号}, password_hash = #{密码加密哈希}, position = #{岗位}, employment_type = #{雇佣类型}, hire_date = #{入职日期}, status = 1（在职）, is_active = 1, version = 1 |
| 3 | audit_log | INSERT | 记录入职操作审计日志 | operator_id = #{操作人 ID}, operator_name = #{操作人姓名}, biz_type = 'employee', biz_id = #{新插入员工 ID}, action = 'create', detail = {"real_name": {"new": "#{姓名}"}, "position": {"new": "#{岗位}"}}, ip_address = #{操作 IP} |

**异常处理**：
- 工号或手机号重复：提示"工号/手机号已存在"，事务回滚
- 必填字段缺失：前端/业务层校验拦截

---

### E-7.2 员工离职

标记员工为离职状态，禁止登录，清除所有角色关联。

**前置条件**：
- 目标员工当前状态为"在职"（status = 1）
- 操作人具有员工管理权限

**事务边界**：步骤 1~4 在同一事务中

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | employee | SELECT | 查询目标员工，确认状态为在职 | WHERE id = #{employee_id} AND status = 1 AND deleted_at IS NULL |
| 2 | employee | UPDATE | 标记离职，禁止登录 | status = 2（离职）, resign_date = #{离职日期}, is_active = 0, version = version + 1 |
| 3 | employee_role | SELECT | 查询该员工所有角色关联记录 | WHERE employee_id = #{employee_id} AND deleted_at IS NULL |
| 4 | employee_role | UPDATE | 逻辑删除所有角色关联（清除权限） | deleted_at = CURRENT_TIMESTAMP（对步骤 3 查出的所有记录执行逻辑删除） |

**异常处理**：
- 员工状态非"在职"：提示"员工状态不正确，无法执行离职操作"，事务回滚
- 员工存在进行中的班次：提示"请先完成当前班次结班"，拒绝离职
- 乐观锁冲突：提示刷新重试，事务回滚

---

### E-7.3 员工打卡上班

员工到岗后打卡签到，系统记录上班时间。若当日已有记录则更新，否则新建。

**前置条件**：
- 员工状态为"在职"且 is_active = 1
- 当日未打卡或需要补卡

**事务边界**：步骤 1~4 在同一事务中

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | employee | SELECT | 验证员工身份与排班信息 | WHERE id = #{employee_id} AND status = 1 AND is_active = 1 AND deleted_at IS NULL |
| 2 | attendance_record | SELECT | 查询当日是否已有考勤记录 | WHERE employee_id = #{employee_id} AND record_date = #{当日日期} AND deleted_at IS NULL |
| 3 | attendance_record | INSERT | 若当日无记录，新建考勤记录 | employee_id = #{employee_id}, record_date = #{当日日期}, schedule_start = #{排班开始时间}, schedule_end = #{排班结束时间}, clock_in = CURRENT_TIMESTAMP, status = 0（正常）, version = 1 |
| 4 | attendance_record | UPDATE | 若当日已有记录（补卡场景），更新打卡时间 | clock_in = #{补卡时间}, version = version + 1 |

**异常处理**：
- 员工不存在或已离职/停用：提示"员工状态异常"，拒绝打卡
- 当日已有正常打卡记录且非补卡场景：提示"今日已打卡"，拒绝重复操作
- 乐观锁冲突（补卡场景）：提示刷新重试

---

### E-7.4 员工打卡下班

员工下班打卡，系统计算实际工时。

**前置条件**：
- 当日存在考勤记录且已有上班打卡（clock_in 不为空）
- 当日尚未打卡下班

**事务边界**：步骤 1~3 在同一事务中

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | attendance_record | SELECT | 查询当日考勤记录，确认已有上班打卡 | WHERE employee_id = #{employee_id} AND record_date = #{当日日期} AND clock_in IS NOT NULL AND clock_out IS NULL AND deleted_at IS NULL |
| 2 | attendance_record | UPDATE | 记录下班时间，计算工时 | clock_out = CURRENT_TIMESTAMP, work_hours = TIMESTAMPDIFF(MINUTE, clock_in, CURRENT_TIMESTAMP) / 60.00, version = version + 1 |
| 3 | attendance_record | UPDATE | 根据排班判断迟到/早退并更新状态（联动触发 E-7.5 逻辑） | 若 clock_in 时间 > schedule_start：status = 1（迟到）, late_minutes = TIMESTAMPDIFF(MINUTE, schedule_start, clock_in)；若 clock_out 时间 < schedule_end：status = 2（早退）, early_leave_minutes = TIMESTAMPDIFF(MINUTE, clock_out, schedule_end)；若同时迟到且早退，以迟到为主状态, version = version + 1 |

**异常处理**：
- 当日无考勤记录或无上班打卡：提示"请先打卡上班"，拒绝操作
- 当日已打卡下班：提示"今日已打卡下班"，拒绝重复操作
- 乐观锁冲突：提示刷新重试

---

### E-7.5 迟到/早退处理

系统自动或人工标记迟到/早退，记录偏差分钟数。此逻辑已在 E-7.4 步骤 3 中联动执行，本事件也支持独立的手动修正场景。

**前置条件**：
- 当日存在考勤记录
- 手动修正场景下操作人具有考勤管理权限

**事务边界**：步骤 1~2 在同一事务中

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | attendance_record | SELECT | 查询目标考勤记录 | WHERE id = #{record_id} AND deleted_at IS NULL |
| 2 | attendance_record | UPDATE | 更新考勤状态与偏差分钟数 | **迟到**：status = 1, late_minutes = #{计算迟到分钟数}；**早退**：status = 2, early_leave_minutes = #{计算早退分钟数}；**缺勤**：status = 3；**请假**：status = 4, remark = #{备注说明}, version = version + 1 |

**异常处理**：
- 考勤记录不存在：提示"记录不存在"，事务回滚
- 手动修正时操作人无权限：业务层拦截
- 乐观锁冲突：提示刷新重试

---

### E-7.6 角色权限分配

管理员为员工分配角色，或为角色分配权限。支持新增与移除两种操作。

**前置条件**：
- 操作人具有角色/权限管理权限
- 目标员工、角色、权限均存在且有效

**事务边界**：

**子流程 A：员工角色分配** — 步骤 A1~A3 在同一事务中

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| A1 | employee | SELECT | 验证员工存在且在职 | WHERE id = #{employee_id} AND status = 1 AND deleted_at IS NULL |
| A2 | role | SELECT | 验证角色存在且启用 | WHERE id = #{role_id} AND is_active = 1 AND deleted_at IS NULL |
| A3 | employee_role | INSERT | 建立员工-角色关联 | employee_id = #{employee_id}, role_id = #{role_id}, version = 1 |

**子流程 B：移除员工角色** — 步骤 B1~B2 在同一事务中

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| B1 | employee_role | SELECT | 查询目标关联记录 | WHERE employee_id = #{employee_id} AND role_id = #{role_id} AND deleted_at IS NULL |
| B2 | employee_role | UPDATE | 逻辑删除该关联 | deleted_at = CURRENT_TIMESTAMP |

**子流程 C：角色权限分配** — 步骤 C1~C3 在同一事务中

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| C1 | role | SELECT | 验证角色存在 | WHERE id = #{role_id} AND deleted_at IS NULL |
| C2 | permission | SELECT | 验证权限存在 | WHERE id = #{permission_id} AND deleted_at IS NULL |
| C3 | role_permission | INSERT | 建立角色-权限关联 | role_id = #{role_id}, permission_id = #{permission_id} |

**子流程 D：移除角色权限** — 步骤 D1~D2 在同一事务中

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| D1 | role_permission | SELECT | 查询目标关联记录 | WHERE role_id = #{role_id} AND permission_id = #{permission_id} |
| D2 | role_permission | DELETE | 物理删除该关联（关联表无公共字段，直接物理删除） | 删除 role_id = #{role_id} AND permission_id = #{permission_id} 的记录 |

**异常处理**：
- 员工/角色/权限不存在或已失效：提示对应资源异常，事务回滚
- 重复分配（唯一约束冲突）：提示"已存在该关联"，事务回滚
- 系统预置角色（is_system = 1）不允许删除关联：业务层拦截

---

### E-7.7 提成结算

定时任务（通常为月度/周度）根据提成规则自动计算员工提成金额。

**前置条件**：
- 提成规则处于启用状态（is_active = 1）且在有效期内
- 结算周期内有可统计的业务数据

**事务边界**：每个员工的提成记录独立成事务（步骤 1~5 循环执行）

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | commission_rule | SELECT | 查询所有启用的提成规则 | WHERE is_active = 1 AND applicable_start <= #{结算日期} AND (applicable_end IS NULL OR applicable_end >= #{结算日期}) AND deleted_at IS NULL |
| 2 | employee | SELECT | 查询适用岗位的员工列表 | WHERE status = 1 AND (target_position = '' OR position = #{rule.target_position}) AND deleted_at IS NULL |
| 3 | orders | SELECT | 按员工统计结算周期内的业务指标（销售额、开卡数等） | SELECT operator_id, SUM(payable_amount) AS sales_amount, COUNT(*) AS order_count FROM orders WHERE paid_at >= #{周期开始} AND paid_at <= #{周期结束} AND status IN (1, 2) AND deleted_at IS NULL GROUP BY operator_id |
| 4 | commission_record | SELECT | 检查是否已存在同周期同员工的提成记录（防重复计算） | WHERE employee_id = #{employee_id} AND rule_id = #{rule_id} AND settle_date = #{结算日期} AND deleted_at IS NULL |
| 5 | commission_record | INSERT | 写入提成记录 | employee_id = #{employee_id}, rule_id = #{rule_id}, settle_date = #{结算日期}, base_metric = #{根据 calculation_method 计算的基准指标值}, commission_rate = #{提成比例（从 calculation_method JSON 解析）}, commission_amount = base_metric * commission_rate, status = 0（待结算）, version = 1 |

**异常处理**：
- 无匹配规则：跳过本轮计算，不写入记录
- 员工在结算周期内无业务数据：base_metric = 0，commission_amount = 0，仍写入记录以便追溯
- 已存在同周期记录：跳过该员工，防止重复计算
- calculation_method JSON 解析失败：记录错误日志，跳过该规则

---

### E-7.8 提成发放

财务或管理员将提成记录标记为已发放。

**前置条件**：
- 提成记录状态为"待结算"（status = 0）或"已结算"（status = 1）
- 操作人具有提成发放权限

**事务边界**：步骤 1~2 在同一事务中

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | commission_record | SELECT | 查询目标提成记录，确认状态为待结算或已结算 | WHERE id = #{record_id} AND status IN (0, 1) AND deleted_at IS NULL |
| 2 | commission_record | UPDATE | 标记为已发放 | status = 2（已发放）, version = version + 1 |

**异常处理**：
- 提成记录状态不正确：提示"状态不正确，无法发放"，事务回滚
- 乐观锁冲突：提示刷新重试
- 批量发放时部分失败：单条记录独立事务，失败记录单独回滚，不影响其他记录

---

## 八、营销活动（6 个事件）

### E-8.1 创建活动

管理员创建营销活动，配置活动类型、时间、规则与预算。

**前置条件**：
- 操作人具有营销活动管理权限
- 活动名称、编号唯一

**事务边界**：步骤 1~2 在同一事务中

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | campaign | SELECT | 校验活动编号唯一性 | WHERE campaign_no = #{活动编号} AND deleted_at IS NULL |
| 2 | campaign | INSERT | 创建营销活动 | campaign_no = 系统生成活动编号, campaign_name = #{活动名称}, campaign_type = #{活动类型 1-6}, start_time = #{开始时间}, end_time = #{结束时间}, rules = #{活动规则 JSON}, budget = #{活动预算}, usage_limit = #{使用次数限制}, member_limit = #{每人限参与次数 默认 1}, used_budget = 0.00, used_count = 0, status = 0（草稿）, version = 1 |

**异常处理**：
- 活动编号重复：重新生成后重试
- 结束时间早于开始时间：业务层校验拦截
- 必填字段缺失：前端/业务层校验拦截

---

### E-8.2 发布活动

管理员将草稿状态的活动发布上线。

**前置条件**：
- 活动状态为"草稿"（status = 0）
- 活动时间、规则配置完整

**事务边界**：步骤 1~2 在同一事务中

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | campaign | SELECT | 查询活动详情，确认状态为草稿且配置完整 | WHERE id = #{campaign_id} AND status = 0 AND deleted_at IS NULL；校验 start_time、end_time、rules 不为空 |
| 2 | campaign | UPDATE | 发布活动 | status = 1（已发布）, version = version + 1 |

**异常处理**：
- 活动状态非"草稿"：提示"活动状态不正确"，事务回滚
- 配置不完整（缺少时间/规则）：提示"请先完善活动配置"，拒绝发布
- 乐观锁冲突：提示刷新重试

---

### E-8.3 结束活动

活动到期自动结束，或管理员手动下架。

**前置条件**：
- 活动状态为"已发布"（status = 1）或"已生效"（status = 2）
- 手动下架场景需操作人具有活动管理权限

**事务边界**：步骤 1~2 在同一事务中

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | campaign | SELECT | 查询活动记录，确认状态允许结束 | WHERE id = #{campaign_id} AND status IN (1, 2) AND deleted_at IS NULL |
| 2 | campaign | UPDATE | 结束活动 | **到期自动结束**：status = 3（已结束）, version = version + 1；**手动下架**：status = 4（已下架）, version = version + 1 |

**异常处理**：
- 活动状态非"已发布"/"已生效"：提示"活动状态不正确"，事务回滚
- 乐观锁冲突：提示刷新重试
- 定时任务触发时活动已结束：幂等处理，跳过

---

### E-8.4 创建优惠券模板

管理员创建优惠券模板，定义券类型、面值、使用条件与发行量。

**前置条件**：
- 操作人具有优惠券管理权限
- 模板名称合法

**事务边界**：步骤 1 在同一事务中

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | coupon_template | INSERT | 创建优惠券模板 | template_name = #{模板名称}, coupon_type = #{券类型 1-满减 2-折扣 3-现金 4-时段}, face_value = #{面值}, min_consume = #{最低消费 默认 0.00}, discount_rate = #{折扣率 折扣券专用}, valid_days = #{有效天数（从发放算起，与 valid_start/valid_end 互斥）}, valid_start = #{固定生效日期}, valid_end = #{固定失效日期}, applicable_products = #{适用商品 ID 列表 JSON}, total_quantity = #{发行总量 0=不限量}, member_level_limit = #{会员等级限制 可为 NULL}, is_active = 1, version = 1 |

**异常处理**：
- valid_days 与 valid_start/valid_end 同时填写：业务层校验拦截，二者互斥
- 满减券未设置 min_consume：业务层校验拦截
- 折扣券未设置 discount_rate：业务层校验拦截
- 发行总量为负数：前端校验拦截

---

### E-8.5 发放优惠券

管理员批量向会员发放优惠券实例，可关联活动或独立发放。

**前置条件**：
- 优惠券模板存在且启用（is_active = 1）
- 关联活动场景下活动状态为"已发布"或"已生效"
- 模板剩余可发数量充足（total_quantity > 已发放数量 或 total_quantity = 0 不限量）
- 目标会员列表有效

**事务边界**：步骤 1~5 在同一事务中（批量发放整体为同一事务）

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | coupon_template | SELECT | 查询模板信息，校验发行量限制 | WHERE id = #{template_id} AND is_active = 1 AND deleted_at IS NULL；校验 total_quantity = 0（不限量）或 total_quantity > 已发放数量（SELECT COUNT(*) FROM coupon WHERE template_id = #{template_id} AND deleted_at IS NULL） |
| 2 | campaign | SELECT | 若关联活动，查询活动状态与参与限制 | WHERE id = #{campaign_id} AND status IN (1, 2) AND deleted_at IS NULL；若 usage_limit 不为 NULL，校验 used_count < usage_limit |
| 3 | coupon | SELECT | 校验每个目标会员的参与次数限制 | WHERE member_id = #{member_id} AND campaign_id = #{campaign_id} AND deleted_at IS NULL；校验 COUNT(*) < campaign.member_limit |
| 4 | coupon | INSERT | 批量生成优惠券实例（每个会员一条记录） | member_id = #{目标会员 ID}, template_id = #{模板 ID}, campaign_id = #{活动 ID 可为 NULL}, coupon_code = 系统生成唯一券码, face_value = #{模板面值}, status = 0（未使用）, expire_at = 根据模板配置计算（NOW() + valid_days 或 valid_end）, version = 1 |
| 5 | campaign | UPDATE | 若关联活动，更新已使用次数 | used_count = used_count + #{本次发放数量}, version = version + 1 |

**异常处理**：
- 模板不存在或未启用：提示"优惠券模板不可用"，事务回滚
- 模板剩余数量不足：提示"优惠券库存不足"，事务回滚
- 活动不存在或状态不正确：提示"活动不可用"，事务回滚
- 会员已达到参与次数上限：跳过该会员或整体回滚（根据业务配置）
- 券码生成冲突（唯一约束）：重新生成后重试
- 乐观锁冲突（campaign 表）：提示刷新重试

---

### E-8.6 优惠券过期处理

定时任务扫描过期优惠券，批量更新状态为已过期。

**前置条件**：
- 定时任务按设定频率执行（建议每日凌晨）
- 存在 expire_at < 当前时间且 status = 0（未使用）的优惠券

**事务边界**：按批次提交事务，每批 500~1000 条

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | coupon | SELECT | 查询已过期的未使用优惠券（分页） | WHERE expire_at < CURRENT_TIMESTAMP AND status = 0 AND deleted_at IS NULL LIMIT #{批次大小} |
| 2 | coupon | UPDATE | 批量标记为已过期 | status = 2（已过期）（对步骤 1 查出的所有记录执行批量更新） |

**异常处理**：
- 无过期优惠券：任务正常结束，无数据变更
- 批次更新部分失败：回滚当前批次，记录错误日志，继续处理下一批次
- 定时任务重复触发：通过 status 条件天然幂等，已过期记录不会被重复处理

---

## 九、系统管理（5 个事件）

### E-9.1 员工登录

员工通过管理后台或收银端登录系统，系统验证身份并加载权限树。

**前置条件**：
- 员工账号存在且 is_active = 1
- 密码验证通过

**事务边界**：步骤 1~2 在同一事务中（更新登录时间），步骤 3~5 为只读查询

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | employee | SELECT | 根据手机号/工号查询员工，验证密码与状态 | WHERE (phone = #{手机号} OR employee_no = #{工号}) AND is_active = 1 AND status IN (1) AND deleted_at IS NULL；比对 password_hash |
| 2 | employee | UPDATE | 更新最后登录时间 | last_login_at = CURRENT_TIMESTAMP, version = version + 1 |
| 3 | employee_role | SELECT | 加载员工关联的角色列表 | WHERE employee_id = #{employee_id} AND deleted_at IS NULL |
| 4 | role_permission | SELECT | 加载所有角色关联的权限 ID | WHERE role_id IN (#{角色 ID 列表}) |
| 5 | permission | SELECT | 查询权限详情，构建权限树 | WHERE id IN (#{权限 ID 列表}) AND deleted_at IS NULL ORDER BY sort_order |

**异常处理**：
- 账号不存在或已停用：提示"账号不存在或已被禁用"
- 密码错误：提示"密码错误"，连续错误次数达阈值可锁定账号（通过 audit_log 记录失败次数）
- 员工状态为离职：提示"账号已失效"
- 乐观锁冲突（步骤 2）：允许静默失败，登录时间更新非关键路径

---

### E-9.2 会员登录

会员通过前台登录，系统验证手机号并加载等级信息。

**前置条件**：
- 会员账号存在且状态为正常（status = 1）
- 手机号验证码校验通过或密码验证通过

**事务边界**：步骤 1~2 在同一事务中，步骤 3 为只读查询

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | member | SELECT | 根据手机号查询会员，验证状态 | WHERE phone = #{手机号} AND status = 1 AND deleted_at IS NULL |
| 2 | member | UPDATE | 更新最后登录时间 | last_login_time = CURRENT_TIMESTAMP, version = version + 1 |
| 3 | member_level | SELECT | 加载会员等级信息（折扣率、积分倍数等） | WHERE id = #{member.level_id} AND deleted_at IS NULL |

**异常处理**：
- 会员不存在：引导注册新账号
- 会员状态为冻结（status = 2）：提示"账号已被冻结，请联系前台"
- 会员状态为黑名单（status = 3）：提示"账号已被限制使用"
- 乐观锁冲突（步骤 2）：允许静默失败

---

### E-9.3 系统配置修改

管理员修改系统运行参数（如计费规则、通知模板、营业设置等）。

**前置条件**：
- 操作人具有系统配置管理权限
- 配置项存在且可修改

**事务边界**：步骤 1~3 在同一事务中

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | system_config | SELECT | 查询当前配置值与版本号 | WHERE config_key = #{配置键} AND deleted_at IS NULL |
| 2 | audit_log | INSERT | 记录配置变更审计日志 | operator_id = #{操作人 ID}, operator_name = #{操作人姓名}, biz_type = 'system_config', biz_id = #{配置记录 ID}, action = 'update', detail = {"config_value": {"old": "#{旧值}", "new": "#{新值}"}}, ip_address = #{操作 IP} |
| 3 | system_config | UPDATE | 更新配置值 | config_value = #{新配置值}, version = version + 1 |

**异常处理**：
- 配置项不存在：提示"配置项不存在"，事务回滚
- 配置值格式校验失败（如数值型配置传入非数值）：业务层校验拦截
- 乐观锁冲突：提示"配置已被其他管理员修改，请刷新后重试"，事务回滚
- 加密配置项（is_encrypted = 1）：写入前加密，读取后解密，审计日志中不记录明文

---

### E-9.4 公告发布

管理员发布系统公告、员工通知或会员推送。

**前置条件**：
- 操作人具有公告发布权限
- 标题、内容不为空

**事务边界**：步骤 1 在同一事务中

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | notification | INSERT | 创建通知记录 | notify_type = #{通知类型 1-系统公告 2-员工通知 3-会员推送}, title = #{标题}, content = #{内容}, target_type = #{目标类型 0-全部 1-指定角色 2-指定员工 3-指定会员}, target_ids = #{目标 ID 列表 JSON 当 target_type != 0 时必填}, published_at = CURRENT_TIMESTAMP, expired_at = #{过期时间 可为 NULL 表示永不过期}, is_read = 0, version = 1 |

**异常处理**：
- 标题或内容为空：业务层校验拦截
- target_type 为指定类型但 target_ids 为空：业务层校验拦截
- 过期时间早于当前时间：业务层校验拦截

---

### E-9.5 文件上传

上传商品图片、会员头像、报表文件等，系统存储文件并记录元数据。

**前置条件**：
- 文件格式与大小在允许范围内
- 存储路径可写

**事务边界**：步骤 1~2 在同一事务中

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | file_meta | SELECT | 根据文件 MD5 检查是否已存在相同文件（去重） | WHERE file_md5 = #{文件 MD5} AND deleted_at IS NULL |
| 2 | file_meta | INSERT | 记录文件元数据（若文件已存在则复用，仅新增关联） | original_name = #{原始文件名}, stored_name = #{存储文件名（UUID 或时间戳生成）}, file_path = #{存储路径}, file_size = #{文件大小（字节）}, mime_type = #{MIME 类型}, file_md5 = #{文件 MD5 校验值}, biz_type = #{关联业务类型 如 product_image / member_avatar / report}, biz_id = #{关联业务 ID 可为 NULL}, version = 1 |

**异常处理**：
- 文件格式不在白名单内：拒绝上传，提示"不支持的文件格式"
- 文件大小超过限制：拒绝上传，提示"文件过大"
- 存储空间不足：捕获 IO 异常，提示"存储空间不足"，事务回滚
- 文件 MD5 重复（秒传）：跳过文件存储，仅新增 file_meta 记录关联业务信息
- 存储路径不可写：捕获 IO 异常，提示"存储服务异常"，事务回滚

---

## 十、第三方集成（2 个模拟事件）

### E-10.1 公安审计上报

定时任务按公安机关要求周期性地汇总上机记录，生成审计上报数据。**Demo 环境下，数据仅写入本地审计表（audit_log），不实际对接外部公安审计平台。**

**前置条件**：
- 定时任务按设定频率执行（如每日凌晨）
- 存在待上报的上机会话记录

**事务边界**：步骤 1~2 为只读查询，无写操作（上报数据生成文件后通过外部接口传输）

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | session | SELECT | 查询待上报的上机会话记录（上次上报后的新增记录） | WHERE start_time > #{上次上报截止时间} AND deleted_at IS NULL ORDER BY start_time ASC |
| 2 | member | SELECT | 根据会话中的 member_id 批量查询会员身份信息 | WHERE id IN (#{步骤 1 会话记录中的 member_id 列表}) AND deleted_at IS NULL；提取 real_name、id_card、phone 等身份字段 |

**上报数据组装**（非数据库操作）：
- 将会话记录与会员身份信息关联组装
- 按公安审计平台要求的格式生成上报文件（XML/JSON）
- Demo 环境下，上报数据写入本地文件或直接丢弃，不实际传输
- 记录本次上报的截止时间点到系统配置（system_config），用于下次增量查询

**异常处理**：
- 无待上报记录：任务正常结束，不生成上报文件
- 会员身份信息缺失（如散客 session.member_id 为 NULL）：按散客规则处理，使用临时身份信息
- 上报接口调用失败：记录错误日志，保留待上报数据，下次任务重试
- 网络超时：支持断点续传，通过记录的截止时间点避免重复上报

---

### E-10.2 短信发送

发送验证码、消费通知等，**Demo 环境下仅在系统内记录，不实际调用短信服务商接口。**

**前置条件**：
- 短信服务商接口可用
- 目标手机号合法有效
- 发送频率未超过限制（验证码 60 秒间隔等）

**事务边界**：步骤 1~2 在同一事务中

| 步骤 | 操作表 | 操作类型 | 目的 | 关键字段变更 |
|------|--------|----------|------|-------------|
| 1 | notification | SELECT | 发送频率校验：查询该手机号最近的验证码发送记录 | WHERE target_ids LIKE '%#{手机号}%' AND notify_type = 3 AND created_at > #{当前时间 - 60秒} AND deleted_at IS NULL LIMIT 1 → 若存在则拒绝发送 |
| 2 | notification | INSERT | 记录短信发送日志 | notify_type = 3（会员推送）, title = #{短信类型标题 如"验证码"/"消费通知"}, content = #{短信内容}, target_type = 3（指定会员）, target_ids = #{目标手机号 JSON}, published_at = CURRENT_TIMESTAMP, expired_at = #{验证码过期时间 仅验证码类型设置}, is_read = 0, version = 1 |

**短信发送**（步骤 2 之后，异步执行）：
- Demo 环境下，短信内容仅记录在 notification 表中，不实际发送
- 更新 notification 记录发送结果（Demo 环境统一标记为"已发送"）

**异常处理**：
- 发送频率超限：提示"验证码发送过于频繁，请稍后再试"，不写入记录
- 手机号格式非法：业务层校验拦截
- 短信接口调用失败：notification 记录仍保留（标记发送状态），支持定时任务重发
- 短信内容包含敏感信息：业务层脱敏处理后再发送
- 批量发送部分失败：每条短信独立处理，失败记录单独标记，不影响其他发送

---

## 事件总览表

| 编号 | 事件名称 | 所属模块 | 触发方式 | 优先级 |
|------|----------|----------|----------|--------|
| E-1.1 | 会员注册 | 会员管理 | 前台 | P0 |
| E-1.2 | 会员充值 | 会员管理 | 前台 | P0 |
| E-1.3 | 充值退款 | 会员管理 | 前台 | P0 |
| E-1.4 | 会员等级变更 | 会员管理 | 系统自动/手动 | P1 |
| E-1.5 | 会员账户冻结 | 会员管理 | 手动 | P1 |
| E-1.6 | 会员账户解冻 | 会员管理 | 手动 | P1 |
| E-1.7 | 加入黑名单 | 会员管理 | 手动 | P0 |
| E-1.8 | 移除黑名单 | 会员管理 | 手动 | P1 |
| E-1.9 | 积分兑换 | 会员管理 | 前台 | P1 |
| E-1.10 | 积分过期处理 | 会员管理 | 定时任务 | P1 |
| E-2.1 | 会员上机 | 上机管理 | 刷卡/前台手动 | P0 |
| E-2.2 | 散客上机 | 上机管理 | 临时密码 | P0 |
| E-2.3 | 预约到店上机 | 上机管理 | 到店触发 | P0 |
| E-2.4 | 正常下机 | 上机管理 | 会员主动 | P0 |
| E-2.5 | 临时下机 | 上机管理 | 会员主动 | P0 |
| E-2.6 | 恢复上机 | 上机管理 | 会员主动 | P0 |
| E-2.7 | 换机操作 | 上机管理 | 会员/管理员 | P1 |
| E-2.8 | 强制下机 | 上机管理 | 管理员 | P0 |
| E-2.9 | 创建预约 | 上机管理 | 前台 | P1 |
| E-2.10 | 确认预约 | 上机管理 | 管理员 | P1 |
| E-2.11 | 取消预约 | 上机管理 | 会员/管理员 | P1 |
| E-2.12 | 预约超时处理 | 上机管理 | 定时任务 | P1 |
| E-3.1 | 心跳计费 | 计费管理 | 客户端心跳 | P0 |
| E-3.2 | 包时段计费 | 计费管理 | 会员选择 | P0 |
| E-3.3 | 包时段超时转按时 | 计费管理 | 系统自动 | P0 |
| E-3.4 | 动态调价 | 计费管理 | 定时任务 | P1 |
| E-3.5 | 手动优惠/减免 | 计费管理 | 收银员 | P0 |
| E-3.6 | 优惠券抵扣 | 计费管理 | 结算时 | P1 |
| E-3.7 | 余额不足扣费处理 | 计费管理 | 系统自动 | P0 |
| E-4.1 | 散客商品购买 | 商品销售 | 收银台 | P0 |
| E-4.2 | 会员商品购买 | 商品销售 | 收银台 | P0 |
| E-4.3 | 套餐购买 | 商品销售 | 收银台 | P1 |
| E-4.4 | 机位配送下单 | 商品销售 | 前台 | P2 |
| E-4.5 | 商品全额退货 | 商品销售 | 收银台 | P0 |
| E-4.6 | 商品部分退货 | 商品销售 | 收银台 | P1 |
| E-5.1 | 创建采购单 | 库存管理 | 管理员 | P1 |
| E-5.2 | 采购单审核 | 库存管理 | 主管 | P1 |
| E-5.3 | 采购入库 | 库存管理 | 管理员 | P1 |
| E-5.4 | 取消采购单 | 库存管理 | 管理员 | P1 |
| E-5.5 | 库存盘点 | 库存管理 | 管理员 | P1 |
| E-5.6 | 损耗登记 | 库存管理 | 管理员 | P1 |
| E-5.7 | 库存预警处理 | 库存管理 | 定时任务 | P1 |
| E-6.1 | 开班 | 收银管理 | 收银员 | P0 |
| E-6.2 | 交班结班 | 收银管理 | 收银员 | P0 |
| E-6.3 | 结班审核 | 收银管理 | 主管 | P0 |
| E-6.4 | 日结 | 收银管理 | 定时任务/手动 | P0 |
| E-6.5 | 日结确认 | 收银管理 | 主管 | P0 |
| E-6.6 | 退款审批 | 收银管理 | 管理员 | P0 |
| E-6.7 | 退款执行 | 收银管理 | 系统/手动 | P0 |
| E-7.1 | 员工入职 | 员工管理 | 管理员 | P1 |
| E-7.2 | 员工离职 | 员工管理 | 管理员 | P1 |
| E-7.3 | 员工打卡上班 | 员工管理 | 员工 | P1 |
| E-7.4 | 员工打卡下班 | 员工管理 | 员工 | P1 |
| E-7.5 | 迟到/早退处理 | 员工管理 | 系统自动 | P1 |
| E-7.6 | 角色权限分配 | 员工管理 | 管理员 | P1 |
| E-7.7 | 提成结算 | 员工管理 | 定时任务 | P2 |
| E-7.8 | 提成发放 | 员工管理 | 管理员 | P2 |
| E-8.1 | 创建活动 | 营销活动 | 管理员 | P2 |
| E-8.2 | 发布活动 | 营销活动 | 管理员 | P2 |
| E-8.3 | 结束活动 | 营销活动 | 系统/管理员 | P2 |
| E-8.4 | 创建优惠券模板 | 营销活动 | 管理员 | P2 |
| E-8.5 | 发放优惠券 | 营销活动 | 系统/手动 | P2 |
| E-8.6 | 优惠券过期处理 | 营销活动 | 定时任务 | P2 |
| E-9.1 | 员工登录 | 系统管理 | 员工 | P0 |
| E-9.2 | 会员登录 | 系统管理 | 会员 | P0 |
| E-9.3 | 系统配置修改 | 系统管理 | 管理员 | P1 |
| E-9.4 | 公告发布 | 系统管理 | 管理员 | P1 |
| E-9.5 | 文件上传 | 系统管理 | 任意用户 | P1 |
| E-10.1 | 公安审计上报（模拟） | 第三方集成 | 定时任务 | P0 |
| E-10.2 | 短信发送（模拟） | 第三方集成 | 系统触发 | P1 |

---

## 核心业务链路（事件串联视角）

以下展示几条最常见的业务链路中事件的串联顺序，帮助理解事件之间的依赖关系。

### 链路 A：会员完整生命周期

```
E-1.1 会员注册
  → E-1.2 会员充值
    → E-2.1 会员上机
      → E-3.1 心跳计费（循环）
        → E-4.2 会员商品购买（上机中购买）
          → E-2.4 正常下机（自动结算）
            → E-1.4 会员等级变更（消费累计触发）
```

### 链路 B：预约 → 上机 → 下机

```
E-2.9 创建预约
  → E-2.10 确认预约
    → E-2.3 预约到店上机
      → E-3.1 心跳计费（循环）
        → E-2.4 正常下机
```

### 链路 C：商品采购 → 入库 → 销售 → 退货

```
E-5.1 创建采购单
  → E-5.2 采购单审核
    → E-5.3 采购入库
      → E-4.1 散客商品购买 / E-4.2 会员商品购买
        → E-4.5 商品全额退货 / E-4.6 商品部分退货
```

### 链路 D：每日收银流程

```
E-6.1 开班
  → [营业期间：各收银/上机/充值事件持续发生]
    → E-6.2 交班结班
      → E-6.3 结班审核
        → E-6.4 日结
          → E-6.5 日结确认
```

### 链路 E：充值活动驱动

```
E-8.1 创建活动（充值赠送）
  → E-8.2 发布活动
    → E-1.2 会员充值（命中活动，触发赠送）
      → E-8.5 发放优惠券（活动附带发券）
        → E-3.6 优惠券抵扣（下次结算使用）
          → E-8.3 结束活动
```

### 链路 F：异常处理

```
E-2.1 会员上机
  → E-3.7 余额不足扣费处理
    → E-2.8 强制下机（欠费不补）
      → E-1.7 加入黑名单（违规行为）
```

---

## 数据库表总览（39 张表）

| 领域 | 表名 | 说明 |
|------|------|------|
| 会员 | member | 会员主表 |
| 会员 | member_level | 会员等级表 |
| 会员 | member_points_log | 积分流水表 |
| 会员 | member_recharge | 充值记录表 |
| 上机 | seat_area | 机位区域表 |
| 上机 | computer | 终端设备表 |
| 上机 | session | 上机会话表 |
| 上机 | session_timing | 上机计时明细表 |
| 上机 | reservation | 预约表 |
| 计费 | tariff_plan | 费率方案表 |
| 计费 | tariff_rate | 费率明细表 |
| 计费 | billing_record | 计费记录表 |
| 商品 | product_category | 商品分类表 |
| 商品 | product | 商品表 |
| 商品 | supplier | 供应商表 |
| 商品 | inventory | 库存表 |
| 商品 | inventory_log | 库存变动日志表 |
| 商品 | purchase_order | 采购单 |
| 商品 | purchase_order_item | 采购明细 |
| 订单 | orders | 订单主表 |
| 订单 | order_item | 订单明细表 |
| 订单 | payment_record | 支付记录表 |
| 订单 | refund_record | 退款记录表 |
| 收银 | cashier_shift | 收银班次表 |
| 收银 | daily_settlement | 日结记录表 |
| 员工 | employee | 员工表 |
| 员工 | role | 角色表 |
| 员工 | permission | 权限表 |
| 员工 | role_permission | 角色权限关联表 |
| 员工 | employee_role | 员工角色关联表 |
| 员工 | attendance_record | 考勤记录表 |
| 员工 | commission_rule | 提成规则表 |
| 员工 | commission_record | 提成记录表 |
| 营销 | campaign | 活动表 |
| 营销 | coupon_template | 优惠券模板表 |
| 营销 | coupon | 优惠券实例表 |
| 系统 | audit_log | 审计日志表 |
| 系统 | system_config | 系统配置表 |
| 系统 | notification | 通知表 |
| 系统 | file_meta | 文件元数据表 |

---

> 文档版本：v2.0（Demo 单店版） — 所有 70 个事件的详细表调用流程已补充完成。
