# 前后端接口规范文档

> **项目编号：** IT-26-7 Demo
> **适用端：** 管理端（StarLinkAttendant）/ 收银端（未来扩展）
> **后端框架：** Spring Boot 3.4.13 + MyBatis-Plus 3.5.5
> **前端框架：** Vue 3.5 + TypeScript + Axios
> **对应文档：** TheModules.md（后端）、TheFrontendModules.md（前端）
> **更新日期：** 2026-07-13

---

## 1. 接口总则

### 1.1 通信协议

| 项 | 规范 |
|:---|:---|
| 协议 | HTTPS（生产）/ HTTP（开发） |
| 数据格式 | `application/json`（文件上传除外） |
| 字符编码 | UTF-8 |
| 时间格式 | `yyyy-MM-dd HH:mm:ss`（如 `2026-07-13 14:30:00`） |
| 金额格式 | 整数传输，单位为 **分**（如 `10050` 表示 `¥100.50`），前端负责展示转换 |
| ID 类型 | `bigint`，前端使用 `string` 接收（防止 JS 精度丢失） |

### 1.2 基础路径

| 环境 | 基础路径 |
|:---|:---|
| 开发环境 | `http://localhost:8080/api` |
| 生产环境 | `https://{domain}/api` |

前端通过环境变量 `VITE_API_BASE_URL` 配置，不硬编码。

### 1.3 模块路径映射

每个后端模块对应统一的 API 路径前缀：

| 模块 | API 前缀 | 说明 |
|:---|:---|:---|
| 系统管理 | `/api/system/` | 用户、角色、菜单、日志、公告 |
| 会员管理 | `/api/member/` | 会员 CRUD、储值、积分、等级 |
| 上机管理 | `/api/session/` | 座位图、上下机、预约 |
| 计费管理 | `/api/billing/` | 费率、计费记录、对账 |
| 商品管理 | `/api/product/` | 商品、分类、套餐 |
| 收银管理 | `/api/cashier/` | 订单、交班、日结 |
| 库存管理 | `/api/inventory/` | 出入库、盘点、预警 |
| 经营报表 | `/api/report/` | 统计、分析、导出 |
| 营销活动 | `/api/marketing/` | 优惠券、促销、活动 |
| 文件服务 | `/api/file/` | 文件上传/下载 |
| 认证服务 | `/api/auth/` | 登录、登出、Token 刷新 |

---

## 2. 认证与鉴权

### 2.1 认证流程

采用 **JWT（JSON Web Token）** 无状态认证方案。

```
┌────────┐    POST /api/auth/login     ┌────────┐
│  前端   │ ──────────────────────────→ │  后端   │
│        │ ←────────────────────────── │        │
│        │    { accessToken, refreshToken }     │
│        │                             │        │
│        │    请求头 Authorization:      │        │
│        │    Bearer {accessToken}      │        │
│        │ ──────────────────────────→ │        │
│        │ ←────────────────────────── │        │
│        │    业务数据响应               │        │
└────────┘                             └────────┘
```

### 2.2 Token 规范

| 项 | 规范 |
|:---|:---|
| 访问令牌 | `accessToken`，有效期 **2 小时** |
| 刷新令牌 | `refreshToken`，有效期 **7 天** |
| 传递方式 | 请求头 `Authorization: Bearer {token}` |
| 存储位置 | 前端 `localStorage`（后续可迁移至 `httpOnly Cookie`） |

### 2.3 Token 刷新机制

当 `accessToken` 过期（后端返回 HTTP 401）时，前端自动使用 `refreshToken` 调用 `/api/auth/refresh` 获取新令牌，并重试原请求。刷新期间其他请求排队等待。

### 2.4 权限控制

| 层级 | 实现方式 |
|:---|:---|
| 路由级 | 后端 `@PreAuthorize` 注解，前端路由守卫拦截 |
| 按钮级 | 后端接口权限校验，前端 `v-permission` 指令控制显隐 |
| 数据级 | 后端根据当前用户角色过滤数据范围 |

---

## 3. 统一请求格式

### 3.1 请求头

| Header | 必填 | 说明 |
|:---|:---:|:---|
| `Content-Type` | 是 | `application/json`（文件上传为 `multipart/form-data`） |
| `Authorization` | 是 | `Bearer {accessToken}`（登录接口除外） |
| `X-Request-Id` | 否 | 请求追踪 ID（前端生成 UUID，用于日志关联） |

### 3.2 请求参数规范

| 请求方式 | 参数位置 | 说明 |
|:---|:---|:---|
| GET | Query String | 查询参数、筛选条件、分页参数 |
| POST | Request Body（JSON） | 新增资源 |
| PUT | Request Body（JSON） | 全量更新资源 |
| PATCH | Request Body（JSON） | 部分更新资源 |
| DELETE | Path Variable 或 Query | 删除资源 |

### 3.3 命名规范

| 项 | 规范 | 示例 |
|:---|:---|:---|
| URL 路径 | kebab-case | `/api/member/blacklist-manage` |
| 查询参数 | camelCase | `?memberName=张三&pageSize=10` |
| JSON 字段 | camelCase | `{ "memberName": "张三", "createdAt": "..." }` |
| 路径变量 | 语义化 | `/api/member/{id}` |

---

## 4. 统一响应格式

### 4.1 成功响应

所有业务接口返回统一的 JSON 结构：

```json
{
  "code": 0,
  "message": "操作成功",
  "data": { },
  "timestamp": "2026-07-13 14:30:00"
}
```

| 字段 | 类型 | 说明 |
|:---|:---|:---|
| `code` | `number` | 业务状态码，`0` 表示成功，非 `0` 表示失败 |
| `message` | `string` | 提示信息（成功或失败的描述） |
| `data` | `any` | 业务数据（成功时返回，失败时可为 `null`） |
| `timestamp` | `string` | 服务器时间戳 |

### 4.2 分页响应

分页查询接口在 `data` 中返回标准分页结构：

```json
{
  "code": 0,
  "message": "查询成功",
  "data": {
    "records": [ ],
    "total": 128,
    "page": 1,
    "size": 10,
    "pages": 13
  },
  "timestamp": "2026-07-13 14:30:00"
}
```

| 字段 | 类型 | 说明 |
|:---|:---|:---|
| `records` | `array` | 当前页数据列表 |
| `total` | `number` | 总记录数 |
| `page` | `number` | 当前页码（从 1 开始） |
| `size` | `number` | 每页条数 |
| `pages` | `number` | 总页数 |

### 4.3 分页请求参数

| 参数 | 类型 | 默认值 | 说明 |
|:---|:---|:---|:---|
| `page` | `number` | `1` | 页码（从 1 开始） |
| `size` | `number` | `10` | 每页条数（最大 100） |
| `sortField` | `string` | — | 排序字段（camelCase） |
| `sortOrder` | `string` | `desc` | 排序方向：`asc` / `desc` |

### 4.4 错误响应

```json
{
  "code": 40001,
  "message": "会员编号已存在",
  "data": null,
  "timestamp": "2026-07-13 14:30:00"
}
```

### 4.5 HTTP 状态码使用

| HTTP 状态码 | 使用场景 |
|:---|:---|
| `200 OK` | 请求成功（含业务错误，业务错误通过 `code` 区分） |
| `400 Bad Request` | 请求参数校验失败（格式错误、必填缺失） |
| `401 Unauthorized` | 未认证或 Token 过期 |
| `403 Forbidden` | 已认证但无权限 |
| `404 Not Found` | 资源不存在 |
| `500 Internal Server Error` | 服务器内部异常 |

---

## 5. 业务状态码

### 5.1 状态码设计规则

采用 **5 位数字**，首位为模块标识：

| 范围 | 模块 | 说明 |
|:---|:---|:---|
| `0` | 全局 | 成功 |
| `10xxx` | 认证/权限 | 登录、Token、权限相关 |
| `20xxx` | 会员模块 | 会员业务错误 |
| `30xxx` | 上机模块 | 上机业务错误 |
| `40xxx` | 计费模块 | 计费业务错误 |
| `50xxx` | 商品模块 | 商品业务错误 |
| `60xxx` | 收银模块 | 收银业务错误 |
| `70xxx` | 库存模块 | 库存业务错误 |
| `80xxx` | 报表模块 | 报表业务错误 |
| `90xxx` | 营销模块 | 营销业务错误 |

### 5.2 通用错误码

| 状态码 | 说明 |
|:---|:---|
| `0` | 成功 |
| `10001` | 用户名或密码错误 |
| `10002` | Token 已过期 |
| `10003` | Token 无效 |
| `10004` | 权限不足 |
| `10005` | 账号已被禁用 |
| `10006` | 验证码错误或已过期 |

### 5.3 业务错误码示例

| 状态码 | 模块 | 说明 |
|:---|:---|:---|
| `20001` | 会员 | 会员编号已存在 |
| `20002` | 会员 | 余额不足 |
| `20003` | 会员 | 会员已被列入黑名单 |
| `30001` | 上机 | 该机位已被占用 |
| `30002` | 上机 | 该会员已有进行中的会话 |
| `30003` | 上机 | 预约时段冲突 |
| `40001` | 计费 | 费率方案不存在 |
| `40002` | 计费 | 计费规则冲突 |
| `60001` | 收银 | 订单不存在 |
| `60002` | 收银 | 订单状态不允许退款 |
| `70001` | 库存 | 库存不足 |
| `70002` | 库存 | 商品已下架 |

> 各模块在开发时按此规则扩展具体错误码，并在本文档中补充。

---

## 6. 数据传输类型规范

### 6.1 基础类型映射

| 后端 Java 类型 | JSON 类型 | 前端 TS 类型 | 说明 |
|:---|:---|:---|:---|
| `Long` | `string` | `string` | ID 类字段，防止 JS 精度丢失 |
| `Integer` / `int` | `number` | `number` | 普通整数 |
| `BigDecimal` | `number` | `number` | 金额类字段（已转为元）或 `integer`（分） |
| `Boolean` | `boolean` | `boolean` | 布尔值 |
| `String` | `string` | `string` | 字符串 |
| `LocalDateTime` | `string` | `string` | 格式：`yyyy-MM-dd HH:mm:ss` |
| `LocalDate` | `string` | `string` | 格式：`yyyy-MM-dd` |
| `List<T>` | `array` | `T[]` | 列表 |
| `Map<K, V>` | `object` | `Record<K, V>` | 键值对 |

### 6.2 金额处理规范

| 场景 | 规范 |
|:---|:---|
| 后端存储 | `bigint`，单位为 **分**（如 `10050` = ¥100.50） |
| 接口传输 | **分**（整数），前端展示时除以 100 |
| 前端展示 | 使用 `common/utils/money.ts` 中的 `formatMoney()` 函数格式化 |
| 前端计算 | 使用整数运算或 `BigDecimal.js`，避免浮点精度问题 |

```typescript
// 前端金额工具示例
function formatMoney(cents: number): string {
  return `¥${(cents / 100).toFixed(2)}`
}

function parseMoney(display: string): number {
  return Math.round(parseFloat(display) * 100)
}
```

### 6.3 枚举值规范

后端使用 `Integer` 存储枚举值，接口传输时使用 **数字码**，不使用字符串：

| 枚举 | 值 | 说明 |
|:---|:---|:---|
| 性别 `gender` | `0` 未知 / `1` 男 / `2` 女 | |
| 会员状态 `status` | `0` 正常 / `1` 冻结 / `2` 注销 | |
| 机位状态 `status` | `0` 空闲 / `1` 使用中 / `2` 锁定 / `3` 维护中 | |
| 会话状态 `status` | `0` 进行中 / `1` 已暂停 / `2` 已结束 | |
| 订单状态 `status` | `0` 待支付 / `1` 已支付 / `2` 已完成 / `3` 已取消 / `4` 已退款 | |
| 支付方式 `payType` | `1` 现金 / `2` 微信 / `3` 支付宝 / `4` 会员余额 | |
| 库存操作类型 `type` | `1` 入库 / `2` 出库 / `3` 盘盈 / `4` 盘亏 | |

前端定义对应的常量对象：

```typescript
// common/constants/index.ts
export const MachineStatus = {
  IDLE: 0,
  IN_USE: 1,
  LOCKED: 2,
  MAINTENANCE: 3,
} as const

export const MachineStatusLabel: Record<number, string> = {
  [MachineStatus.IDLE]: '空闲',
  [MachineStatus.IN_USE]: '使用中',
  [MachineStatus.LOCKED]: '锁定',
  [MachineStatus.MAINTENANCE]: '维护中',
}
```

### 6.4 软删除字段

所有实体包含 `deletedAt` 字段（`datetime`），`null` 表示未删除，有值表示已删除时间。

| 场景 | 规范 |
|:---|:---|
| 列表查询 | 默认只返回未删除记录（后端自动过滤） |
| 删除操作 | 使用 `DELETE /api/xxx/{id}`，后端执行软删除 |
| 恢复操作 | 使用 `PATCH /api/xxx/{id}/restore`（如需要） |

---

## 7. 参数校验规范

### 7.1 后端校验

使用 `jakarta.validation` 注解进行参数校验：

| 注解 | 说明 | 示例 |
|:---|:---|:---|
| `@NotNull` | 不能为 null | 必填字段 |
| `@NotBlank` | 不能为 null 且去空后长度 > 0 | 字符串必填 |
| `@Size(min, max)` | 长度范围 | 用户名 2~32 位 |
| `@Min` / `@Max` | 数值范围 | 金额 ≥ 0 |
| `@Pattern` | 正则匹配 | 手机号格式 |
| `@Email` | 邮箱格式 | |

### 7.2 校验失败响应

```json
{
  "code": 400,
  "message": "参数校验失败",
  "data": {
    "errors": [
      { "field": "memberName", "message": "会员名称不能为空" },
      { "field": "phone", "message": "手机号格式不正确" }
    ]
  },
  "timestamp": "2026-07-13 14:30:00"
}
```

### 7.3 前端校验

表单提交前，前端使用 Element Plus 的表单校验 + `common/utils/validate.ts` 中的规则库进行前置校验，减少无效请求。

---

## 8. 文件上传与下载

### 8.1 文件上传

| 项 | 规范 |
|:---|:---|
| 接口 | `POST /api/file/upload` |
| Content-Type | `multipart/form-data` |
| 参数 | `file`（文件）、`type`（业务类型：`avatar`/`product`/`export`） |
| 大小限制 | 单文件最大 10MB |
| 支持格式 | 图片：`jpg`、`jpeg`、`png`、`gif`；文档：`xlsx`、`pdf` |

**上传响应：**

```json
{
  "code": 0,
  "message": "上传成功",
  "data": {
    "fileId": "1234567890",
    "fileName": "product_001.jpg",
    "fileUrl": "https://oss.example.com/xxx/product_001.jpg",
    "fileSize": 102400
  }
}
```

### 8.2 文件下载 / 导出

| 项 | 规范 |
|:---|:---|
| 接口 | `GET /api/file/download/{fileId}` 或各模块导出接口 |
| 响应类型 | `application/octet-stream` 或 `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` |
| 文件名 | 通过响应头 `Content-Disposition: attachment; filename=xxx.xlsx` 传递 |

---

## 9. 实时数据通信

### 9.1 座位图实时状态

上机模块的座位图需要实时展示机位状态变化，采用 **短轮询** 方案：

| 项 | 规范 |
|:---|:---|
| 接口 | `GET /api/session/seat-status` |
| 轮询间隔 | 5 秒 |
| 响应数据 | 所有机位的当前状态列表（机位 ID + 状态码） |
| 前端处理 | 对比前后数据差异，仅更新变化的机位（增量渲染） |

```json
{
  "code": 0,
  "message": "查询成功",
  "data": {
    "seats": [
      { "id": "1", "status": 0 },
      { "id": "2", "status": 1, "memberName": "张三", "startTime": "2026-07-13 14:00:00" },
      { "id": "3", "status": 2 }
    ],
    "serverTime": "2026-07-13 14:30:00"
  }
}
```

> 如后续需要更高实时性，可升级为 WebSocket 方案（`ws://host/ws/session`）。

---

## 10. 模块接口清单

以下为各模块的核心接口路径概览。具体请求/响应参数在各模块开发时补充。

### 10.1 认证服务（`/api/auth/`）

| 方法 | 路径 | 说明 |
|:---|:---|:---|
| POST | `/api/auth/login` | 管理员登录 |
| POST | `/api/auth/logout` | 退出登录 |
| POST | `/api/auth/refresh` | 刷新 Token |
| GET | `/api/auth/info` | 获取当前登录用户信息 |

### 10.2 系统管理（`/api/system/`）

| 方法 | 路径 | 说明 |
|:---|:---|:---|
| GET | `/api/system/users` | 用户列表（分页） |
| POST | `/api/system/users` | 新增用户 |
| PUT | `/api/system/users/{id}` | 编辑用户 |
| DELETE | `/api/system/users/{id}` | 删除用户（软删除） |
| PATCH | `/api/system/users/{id}/status` | 启用/禁用用户 |
| PATCH | `/api/system/users/{id}/reset-password` | 重置密码 |
| GET | `/api/system/roles` | 角色列表 |
| POST | `/api/system/roles` | 新增角色 |
| PUT | `/api/system/roles/{id}` | 编辑角色 |
| DELETE | `/api/system/roles/{id}` | 删除角色 |
| PUT | `/api/system/roles/{id}/permissions` | 分配角色权限 |
| GET | `/api/system/menus` | 菜单树 |
| POST | `/api/system/menus` | 新增菜单 |
| PUT | `/api/system/menus/{id}` | 编辑菜单 |
| DELETE | `/api/system/menus/{id}` | 删除菜单 |
| GET | `/api/system/logs` | 操作日志列表（分页） |
| GET | `/api/system/notices` | 公告列表（分页） |
| POST | `/api/system/notices` | 发布公告 |
| PUT | `/api/system/notices/{id}` | 编辑公告 |
| DELETE | `/api/system/notices/{id}` | 删除公告 |

### 10.3 会员管理（`/api/member/`）

| 方法 | 路径 | 说明 |
|:---|:---|:---|
| GET | `/api/member/list` | 会员列表（分页 + 筛选） |
| GET | `/api/member/{id}` | 会员详情 |
| POST | `/api/member/register` | 会员注册 |
| PUT | `/api/member/{id}` | 编辑会员信息 |
| DELETE | `/api/member/{id}` | 注销会员 |
| POST | `/api/member/recharge` | 储值充值 |
| GET | `/api/member/{id}/recharge-records` | 充值记录 |
| GET | `/api/member/{id}/points-records` | 积分流水 |
| GET | `/api/member/levels` | 等级规则列表 |
| POST | `/api/member/levels` | 新增等级规则 |
| PUT | `/api/member/levels/{id}` | 编辑等级规则 |
| GET | `/api/member/blacklist` | 黑名单列表 |
| POST | `/api/member/blacklist` | 加入黑名单 |
| DELETE | `/api/member/blacklist/{id}` | 移出黑名单 |

### 10.4 上机管理（`/api/session/`）

| 方法 | 路径 | 说明 |
|:---|:---|:---|
| GET | `/api/session/seat-map` | 获取座位图配置（机位列表 + 分区） |
| GET | `/api/session/seat-status` | 获取所有机位实时状态（轮询接口） |
| POST | `/api/session/start` | 上机（开台） |
| POST | `/api/session/end` | 下机（结账） |
| POST | `/api/session/pause` | 暂停上机 |
| POST | `/api/session/resume` | 恢复上机 |
| POST | `/api/session/transfer` | 换机 |
| POST | `/api/session/force-end` | 强制下机（管理员操作） |
| GET | `/api/session/active` | 当前活跃会话列表 |
| GET | `/api/session/{id}` | 会话详情 |
| GET | `/api/session/reservations` | 预约列表 |
| POST | `/api/session/reservations` | 创建预约 |
| DELETE | `/api/session/reservations/{id}` | 取消预约 |

### 10.5 计费管理（`/api/billing/`）

| 方法 | 路径 | 说明 |
|:---|:---|:---|
| GET | `/api/billing/rates` | 费率方案列表 |
| POST | `/api/billing/rates` | 新增费率方案 |
| PUT | `/api/billing/rates/{id}` | 编辑费率方案 |
| DELETE | `/api/billing/rates/{id}` | 删除费率方案 |
| GET | `/api/billing/records` | 计费记录列表（分页） |
| GET | `/api/billing/records/{id}` | 计费记录详情 |
| GET | `/api/billing/adjustment` | 调价规则列表 |
| POST | `/api/billing/adjustment` | 新增调价规则 |
| PUT | `/api/billing/adjustment/{id}` | 编辑调价规则 |
| GET | `/api/billing/discounts` | 优惠规则列表 |
| POST | `/api/billing/discounts` | 新增优惠规则 |
| GET | `/api/billing/reconciliation` | 对账数据 |

### 10.6 商品管理（`/api/product/`）

| 方法 | 路径 | 说明 |
|:---|:---|:---|
| GET | `/api/product/list` | 商品列表（分页 + 筛选） |
| GET | `/api/product/{id}` | 商品详情 |
| POST | `/api/product` | 新增商品 |
| PUT | `/api/product/{id}` | 编辑商品 |
| DELETE | `/api/product/{id}` | 删除商品 |
| GET | `/api/product/categories` | 分类树 |
| POST | `/api/product/categories` | 新增分类 |
| PUT | `/api/product/categories/{id}` | 编辑分类 |
| DELETE | `/api/product/categories/{id}` | 删除分类 |
| GET | `/api/product/combos` | 套餐列表 |
| POST | `/api/product/combos` | 新增套餐 |
| PUT | `/api/product/combos/{id}` | 编辑套餐 |
| DELETE | `/api/product/combos/{id}` | 删除套餐 |

### 10.7 收银管理（`/api/cashier/`）

| 方法 | 路径 | 说明 |
|:---|:---|:---|
| POST | `/api/cashier/orders` | 创建订单 |
| GET | `/api/cashier/orders` | 订单列表（分页 + 筛选） |
| GET | `/api/cashier/orders/{id}` | 订单详情 |
| POST | `/api/cashier/orders/{id}/pay` | 订单支付 |
| POST | `/api/cashier/orders/{id}/refund` | 订单退款 |
| POST | `/api/cashier/shift/start` | 开始班次 |
| POST | `/api/cashier/shift/end` | 结束班次（交班） |
| GET | `/api/cashier/shift/records` | 班次记录 |
| GET | `/api/cashier/settlement/daily` | 日结汇总 |
| GET | `/api/cashier/settlement/export` | 导出日结报表 |

### 10.8 库存管理（`/api/inventory/`）

| 方法 | 路径 | 说明 |
|:---|:---|:---|
| GET | `/api/inventory/overview` | 库存总览（分页） |
| GET | `/api/inventory/overview/{productId}` | 单商品库存详情 |
| POST | `/api/inventory/inbound` | 创建入库单 |
| GET | `/api/inventory/inbound` | 入库记录 |
| POST | `/api/inventory/outbound` | 创建出库单 |
| GET | `/api/inventory/outbound` | 出库记录 |
| POST | `/api/inventory/stocktake` | 创建盘点单 |
| PUT | `/api/inventory/stocktake/{id}/submit` | 提交盘点结果 |
| GET | `/api/inventory/alerts` | 库存预警列表 |
| GET | `/api/inventory/suppliers` | 供应商列表 |
| POST | `/api/inventory/suppliers` | 新增供应商 |
| PUT | `/api/inventory/suppliers/{id}` | 编辑供应商 |
| DELETE | `/api/inventory/suppliers/{id}` | 删除供应商 |

### 10.9 经营报表（`/api/report/`）

| 方法 | 路径 | 说明 |
|:---|:---|:---|
| GET | `/api/report/dashboard` | 营业总览（今日/本周/本月核心指标） |
| GET | `/api/report/revenue` | 营收分析（按时间段/支付方式/类别） |
| GET | `/api/report/session` | 上机分析（上座率、时段分布） |
| GET | `/api/report/products` | 商品排行（销量/销售额） |
| GET | `/api/report/members` | 会员分析（新增/活跃/留存） |
| GET | `/api/report/finance` | 财务报表（收入/支出/利润） |
| GET | `/api/report/export` | 数据导出（参数：`type` + `dateRange`） |

### 10.10 营销活动（`/api/marketing/`）

| 方法 | 路径 | 说明 |
|:---|:---|:---|
| GET | `/api/marketing/coupons` | 优惠券模板列表 |
| POST | `/api/marketing/coupons` | 创建优惠券模板 |
| PUT | `/api/marketing/coupons/{id}` | 编辑优惠券 |
| POST | `/api/marketing/coupons/{id}/issue` | 发放优惠券 |
| GET | `/api/marketing/coupons/records` | 核销记录 |
| GET | `/api/marketing/recharge-promo` | 充值活动列表 |
| POST | `/api/marketing/recharge-promo` | 创建充值活动 |
| PUT | `/api/marketing/recharge-promo/{id}` | 编辑充值活动 |
| GET | `/api/marketing/campaigns` | 限时活动列表 |
| POST | `/api/marketing/campaigns` | 创建活动 |
| PUT | `/api/marketing/campaigns/{id}` | 编辑活动 |
| GET | `/api/marketing/referral` | 拉新记录统计 |
| POST | `/api/marketing/referral/rules` | 配置邀请奖励规则 |

---

## 11. 跨域与安全性

### 11.1 CORS 配置

开发环境后端允许前端开发服务器跨域访问：

| 项 | 配置 |
|:---|:---|
| 允许的源 | `http://localhost:5173`（Vite 默认端口） |
| 允许的方法 | `GET`、`POST`、`PUT`、`PATCH`、`DELETE`、`OPTIONS` |
| 允许的头 | `Content-Type`、`Authorization`、`X-Request-Id` |
| 暴露的头 | `Content-Disposition`（文件下载） |
| 凭证 | 允许（`allowCredentials: true`） |

### 11.2 安全规范

| 项 | 规范 |
|:---|:---|
| 密码存储 | BCrypt 加密，禁止明文存储 |
| 敏感字段 | 接口响应中密码字段永远不返回 |
| SQL 注入 | MyBatis-Plus 参数化查询，禁止拼接 SQL |
| XSS 防护 | 前端输入转义，后端 `@SafeHtml` 校验 |
| 接口限流 | 登录接口限流 5 次/分钟（Redis 计数器） |
| 操作审计 | 关键操作（充值、退款、删除）记录操作日志 |

---

## 12. 约定与纪律

| 约定项 | 规范 |
|:---|:---|
| 接口文档维护 | 本文档为总纲，各模块在开发时补充具体接口的请求/响应参数 |
| 前后端同步 | 接口变更必须同步更新本文档，先改文档再改代码 |
| Mock 数据 | 前端开发初期可使用 Mock 数据，格式须与本文档规范一致 |
| 版本管理 | 接口大版本升级时通过 URL 前缀区分（如 `/api/v2/`），当前为 v1（省略） |
| 联调流程 | 后端先部署到开发环境 → 前端切换 `VITE_API_BASE_URL` → 联调验证 |
