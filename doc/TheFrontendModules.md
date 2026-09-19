# 前端模块设计文档（管理端）

> **项目编号：** IT-26-7 Demo
> **应用名称：** StarLinkAttendant（星络灵侍馆 · 管理端）
> **技术栈：** Vue 3.5 + TypeScript + Vite 8 + Element Plus + Pinia + Vue Router
> **对应后端：** TheModules.md（v1.0）
> **更新日期：** 2026-07-13

---

## 1. 模块划分

前端模块与后端模块保持一一对应，各业务模块负责对应领域的前端页面开发。

| 后端模块                                           | 前端模块                                     | 页面数量（预估） |
| :------------------------------------------------- | :------------------------------------------- | :--------------: |
| `starlink-common`、`starlink-module-session`           | `common/`、`modules/session/`            |       4~6       |
| `starlink-system`、`starlink-module-member`            | `modules/system/`、`modules/member/`     |       8~10       |
| `starlink-module-billing`、`starlink-module-inventory` | `modules/billing/`、`modules/inventory/` |       6~8       |
| `starlink-module-cashier`、`starlink-module-product`   | `modules/cashier/`、`modules/product/`   |       6~8       |
| `starlink-module-report`、`starlink-module-marketing`  | `modules/report/`、`modules/marketing/`  |       8~10       |

> `layouts/`、`router/`、`App.vue`、`main.ts` 等基础骨架为全局公共设施，各模块统一使用。

---

## 2. 目录结构总览

```
StarLinkAttendant/
├── public/
│   └── favicon.ico
├── src/
│   ├── assets/                        ← 全局静态资源（样式、图片、字体）
│   │   └── styles/
│   │       ├── variables.css          ← CSS 变量（主题色、间距、圆角）
│   │       └── global.css             ← 全局重置与通用样式
│   │
│   ├── common/                        ← 公共基础设施
│   │   ├── api/
│   │   │   ├── request.ts             ← Axios 实例封装（拦截器、Token 注入、错误处理）
│   │   │   └── types.ts               ← API 通用类型（分页、响应体、请求配置）
│   │   ├── auth/
│   │   │   └── index.ts               ← Token 存取、登录态判断、权限校验工具
│   │   ├── components/                ← 全局共享 UI 组件
│   │   │   ├── Pagination.vue         ← 通用分页
│   │   │   ├── SearchForm.vue         ← 通用搜索表单
│   │   │   └── TablePro.vue           ← 增强表格（排序/筛选/导出）
│   │   ├── constants/
│   │   │   └── index.ts               ← 全局枚举与常量
│   │   ├── hooks/                     ← 全局组合式函数
│   │   │   ├── usePermission.ts       ← 按钮级权限控制
│   │   │   ├── useLoading.ts          ← 加载状态管理
│   │   │   └── usePagination.ts       ← 分页逻辑复用
│   │   ├── types/                     ← 全局 TypeScript 类型定义
│   │   │   ├── api.d.ts               ← API 响应通用类型
│   │   │   ├── entity.d.ts            ← 业务实体通用类型
│   │   │   └── global.d.ts            ← 全局声明
│   │   └── utils/
│   │       ├── date.ts                ← 日期格式化与计算
│   │       ├── money.ts               ← 金额格式化与计算（防精度丢失）
│   │       └── validate.ts            ← 表单校验规则库
│   │
│   ├── layouts/                       ← 布局组件
│   │   ├── MainLayout.vue             ← 主布局（侧边栏 + 顶栏 + 内容区）
│   │   ├── BlankLayout.vue            ← 空白布局（登录页等）
│   │   └── components/
│   │       ├── Sidebar.vue            ← 侧边导航菜单
│   │       ├── Navbar.vue             ← 顶部栏（用户信息、消息、退出）
│   │       └── Breadcrumb.vue         ← 面包屑导航
│   │
│   ├── modules/                       ← 业务模块（与后端一一对应）
│   │   ├── system/                    ← 系统管理
│   │   ├── member/                    ← 会员管理
│   │   ├── session/                   ← 上机管理
│   │   ├── billing/                   ← 计费管理
│   │   ├── product/                   ← 商品管理
│   │   ├── cashier/                   ← 收银管理
│   │   ├── inventory/                 ← 库存管理
│   │   ├── report/                    ← 经营报表
│   │   └── marketing/                 ← 营销活动
│   │
│   ├── router/                        ← 路由配置
│   │   ├── index.ts                   ← 路由入口（组合所有模块路由）
│   │   └── guards.ts                  ← 路由守卫（登录拦截、权限校验）
│   │
│   ├── stores/                        ← 全局状态（跨模块共享）
│   │   ├── user.ts                    ← 当前登录用户信息、Token
│   │   ├── permission.ts              ← 动态菜单、按钮权限
│   │   └── app.ts                     ← 应用全局配置（主题、侧边栏折叠）
│   │
│   ├── App.vue                        ← 根组件
│   └── main.ts                        ← 应用入口
│
├── index.html
├── package.json
├── vite.config.ts
├── tsconfig.json
└── eslint.config.ts
```

---

## 3. 模块内部规范

每个业务模块（`modules/xxx/`）遵循统一的内部结构：

```
modules/xxx/
├── views/              ← 页面组件（每个文件对应一个路由页面）
├── components/         ← 模块私有组件（仅本模块使用）
├── api/                ← 模块 API 请求封装
│   └── index.ts        ← 调用 common/api/request.ts 发起请求
├── stores/             ← 模块私有 Pinia Store
├── router.ts           ← 模块路由定义（导出 routes 数组，由主路由合并）
└── types.ts            ← 模块私有 TypeScript 类型
```

**规范说明：**

| 规则       | 说明                                                                           |
| :--------- | :----------------------------------------------------------------------------- |
| 模块间隔离 | 各模块的`components/`、`stores/`、`types.ts` 仅本模块内部使用            |
| 跨模块通信 | 通过`stores/`（全局 Pinia）或事件总线，禁止直接引用其他模块的私有文件        |
| API 统一   | 所有 HTTP 请求必须通过`common/api/request.ts` 的 Axios 实例发出              |
| 路由注册   | 每个模块导出`routes` 数组，在 `router/index.ts` 中按模块聚合               |
| 权限控制   | 路由级权限在`router/guards.ts` 统一拦截，按钮级权限用 `usePermission` Hook |

---

## 4. 模块设计详情

### 4.1 common — 公共基础设施

| 属性               | 值                                                                  |
| :----------------- | :------------------------------------------------------------------ |
| **路径**     | `src/common/`                                                     |
| **对应后端** | `starlink-common`                                                     |
| **职责**     | 全前端公共能力：HTTP 客户端、鉴权工具、共享组件、工具函数、类型定义 |

**核心内容：**

| 分类        | 文件               | 说明                                                                       |
| :---------- | :----------------- | :------------------------------------------------------------------------- |
| HTTP 客户端 | `api/request.ts` | Axios 实例，统一处理请求/响应拦截、Token 自动注入、401 跳转登录、错误提示  |
| API 类型    | `api/types.ts`   | `ApiResponse<T>`、`PageRequest`、`PageResult<T>` 等通用类型          |
| 鉴权工具    | `auth/index.ts`  | Token 存取（localStorage）、登录态判断、权限码校验                         |
| 共享组件    | `components/`    | Pagination、SearchForm、TablePro 等高频复用组件                            |
| 组合式函数  | `hooks/`         | usePermission（按钮权限）、useLoading（加载态）、usePagination（分页逻辑） |
| 工具函数    | `utils/`         | 日期格式化、金额计算（防精度丢失）、表单校验规则库                         |
| 常量        | `constants/`     | 全局枚举（订单状态、支付类型、会员等级等）                                 |
| 类型声明    | `types/`         | 全局 TypeScript 类型定义                                                   |

---

### 4.2 layouts — 布局组件

| 属性             | 值                             |
| :--------------- | :----------------------------- |
| **路径**   | `src/layouts/`               |
| **职责**   | 应用整体布局骨架，所有页面共享 |

**布局说明：**

| 布局                | 使用场景                                                |
| :------------------ | :------------------------------------------------------ |
| `MainLayout.vue`  | 登录后的所有业务页面（侧边栏 + 顶栏 + 面包屑 + 内容区） |
| `BlankLayout.vue` | 登录页、注册页、404 页等无框架页面                      |

---

### 4.3 system — 系统管理模块

| 属性               | 值                              |
| :----------------- | :------------------------------ |
| **路径**     | `src/modules/system/`         |
| **对应后端** | `starlink-system`                 |
| **需求编号** | EMP-01 ~ EMP-06（员工管理部分） |

**页面清单：**

| 页面     | 路由                | 说明                                |
| :------- | :------------------ | :---------------------------------- |
| 登录页   | `/login`          | 管理员/员工登录（使用 BlankLayout） |
| 用户管理 | `/system/users`   | 账号 CRUD、状态启停、密码重置       |
| 角色管理 | `/system/roles`   | 角色定义、权限分配（菜单树勾选）    |
| 菜单管理 | `/system/menus`   | 菜单树维护（图标、路径、排序）      |
| 操作日志 | `/system/logs`    | 操作审计日志查询                    |
| 通知公告 | `/system/notices` | 公告发布、推送记录、已读统计        |

---

### 4.4 member — 会员管理模块

| 属性               | 值                      |
| :----------------- | :---------------------- |
| **路径**     | `src/modules/member/` |
| **对应后端** | `starlink-module-member`  |
| **需求编号** | MBR-01 ~ MBR-07         |

**页面清单：**

| 页面       | 路由                   | 说明                             |
| :--------- | :--------------------- | :------------------------------- |
| 会员列表   | `/member/list`       | 会员搜索、筛选、分页列表         |
| 会员详情   | `/member/detail/:id` | 基础信息、余额、积分、消费记录   |
| 会员注册   | `/member/register`   | 新增会员（个人信息 + 开卡）      |
| 充值管理   | `/member/recharge`   | 储值充值、充值记录查询           |
| 等级规则   | `/member/levels`     | 等级配置（成长值阈值、权益设置） |
| 积分管理   | `/member/points`     | 积分规则配置、积分流水查询       |
| 黑名单管理 | `/member/blacklist`  | 黑名单列表、加入/移出操作        |

---

### 4.5 session — 上机管理模块

| 属性               | 值                       |
| :----------------- | :----------------------- |
| **路径**     | `src/modules/session/` |
| **对应后端** | `starlink-module-session`  |
| **需求编号** | COM-01 ~ COM-08          |

> 系统核心模块，实时性要求最高。

**页面清单：**

| 页面     | 路由                      | 说明                                           |
| :------- | :------------------------ | :--------------------------------------------- |
| 座位图   | `/session/seat-map`     | 实时座位图可视化（机位状态着色、点击交互操作） |
| 上机管理 | `/session/start`        | 开台/上机操作（选会员、选机位、确认上机）      |
| 会话监控 | `/session/monitor`      | 当前所有上机会话列表（时长实时刷新、远程下机） |
| 预约管理 | `/session/reservations` | 预约列表、预约状态管理、超时自动取消           |
| 换机操作 | `/session/transfer`     | 在会话中选择目标机位进行换机                   |

**特殊说明：** 座位图组件需通过 WebSocket 或轮询实现机位状态实时刷新（建议 5~10 秒轮询），使用 Redis Hash 缓存机位状态。

---

### 4.6 billing — 计费管理模块

| 属性               | 值                       |
| :----------------- | :----------------------- |
| **路径**     | `src/modules/billing/` |
| **对应后端** | `starlink-module-billing`  |
| **需求编号** | BIL-01 ~ BIL-07          |

**页面清单：**

| 页面     | 路由                        | 说明                                         |
| :------- | :-------------------------- | :------------------------------------------- |
| 费率方案 | `/billing/rates`          | 费率方案 CRUD（阶梯定价、按时/包时规则配置） |
| 计费记录 | `/billing/records`        | 上机会话的计费明细查询                       |
| 动态调价 | `/billing/adjustment`     | 时段调价规则配置（节假日、高峰时段）         |
| 优惠规则 | `/billing/discounts`      | 优惠减免规则配置                             |
| 对账管理 | `/billing/reconciliation` | 计费数据与收银数据核对                       |

---

### 4.7 product — 商品管理模块

| 属性               | 值                       |
| :----------------- | :----------------------- |
| **路径**     | `src/modules/product/` |
| **对应后端** | `starlink-module-product`  |
| **需求编号** | POS-01 ~ POS-06          |

**页面清单：**

| 页面     | 路由                    | 说明                                          |
| :------- | :---------------------- | :-------------------------------------------- |
| 商品列表 | `/product/list`       | 商品搜索、分类筛选、分页列表                  |
| 商品编辑 | `/product/edit/:id?`  | 新增/编辑商品（名称、分类、规格、价格、图片） |
| 商品分类 | `/product/categories` | 分类树维护                                    |
| 套餐管理 | `/product/combos`     | 套餐组合配置（套餐内容、定价）                |

---

### 4.8 cashier — 收银管理模块

| 属性               | 值                       |
| :----------------- | :----------------------- |
| **路径**     | `src/modules/cashier/` |
| **对应后端** | `starlink-module-cashier`  |
| **需求编号** | CSH-01 ~ CSH-06          |

**页面清单：**

| 页面     | 路由                    | 说明                                          |
| :------- | :---------------------- | :-------------------------------------------- |
| 收银台   | `/cashier/pos`        | 核心收银页面（扫码/手输商品、会员识别、结算） |
| 订单列表 | `/cashier/orders`     | 历史订单查询（筛选、详情、退款）              |
| 交班管理 | `/cashier/shift`      | 交班操作、班次记录                            |
| 日结汇总 | `/cashier/settlement` | 每日营收统计、支付方式汇总、日结报表          |
| 退款管理 | `/cashier/refunds`    | 退款申请、审批、退款记录                      |

---

### 4.9 inventory — 库存管理模块

| 属性               | 值                         |
| :----------------- | :------------------------- |
| **路径**     | `src/modules/inventory/` |
| **对应后端** | `starlink-module-inventory`  |
| **需求编号** | INV-01 ~ INV-05            |

**页面清单：**

| 页面       | 路由                     | 说明                                 |
| :--------- | :----------------------- | :----------------------------------- |
| 库存总览   | `/inventory/overview`  | 当前库存列表（库存量、预警状态标识） |
| 入库管理   | `/inventory/inbound`   | 入库单创建、入库记录查询             |
| 出库管理   | `/inventory/outbound`  | 出库单创建、出库记录查询             |
| 库存盘点   | `/inventory/stocktake` | 盘点单创建、盘盈盘亏处理             |
| 库存预警   | `/inventory/alerts`    | 低于安全库存的商品列表、预警记录     |
| 供应商管理 | `/inventory/suppliers` | 供应商信息 CRUD                      |

---

### 4.10 report — 经营报表模块

| 属性               | 值                      |
| :----------------- | :---------------------- |
| **路径**     | `src/modules/report/` |
| **对应后端** | `starlink-module-report`  |
| **需求编号** | REP-01 ~ REP-07         |

**页面清单：**

| 页面     | 路由                  | 说明                                     |
| :------- | :-------------------- | :--------------------------------------- |
| 营业总览 | `/report/dashboard` | 今日/本周/本月核心指标卡片 + 趋势图      |
| 营收分析 | `/report/revenue`   | 按时间段/支付方式/商品类别的营收分析图表 |
| 上机分析 | `/report/session`   | 上座率、时段分布、平均上机时长           |
| 商品排行 | `/report/products`  | 销量/销售额排行榜                        |
| 会员分析 | `/report/members`   | 新增/活跃/留存分析、消费分布             |
| 财务报表 | `/report/finance`   | 收入/支出汇总、利润分析                  |
| 数据导出 | `/report/export`    | 选择报表类型与时间范围，导出 Excel/PDF   |

---

### 4.11 marketing — 营销活动模块

| 属性               | 值                               |
| :----------------- | :------------------------------- |
| **路径**     | `src/modules/marketing/`       |
| **对应后端** | `starlink-module-marketing`        |
| **需求编号** | MKT-01 ~ MKT-05、INT-01 ~ INT-03 |

**页面清单：**

| 页面       | 路由                          | 说明                                       |
| :--------- | :---------------------------- | :----------------------------------------- |
| 优惠券管理 | `/marketing/coupons`        | 优惠券模板创建、发放记录、核销统计         |
| 充值促销   | `/marketing/recharge-promo` | 充值活动配置（充 100 送 20 等规则）        |
| 限时活动   | `/marketing/campaigns`      | 活动创建（时段特价、节假日促销）、活动日历 |
| 拉新裂变   | `/marketing/referral`       | 邀请奖励规则配置、邀请记录统计             |
| 第三方集成 | `/marketing/integrations`   | 实名认证/短信/公安审计接口的模拟配置与日志 |

---

## 5. 前后端模块对应关系

| 前端模块               | 后端模块                  | API 基础路径        | 说明                         |
| :--------------------- | :------------------------ | :------------------ | :--------------------------- |
| `common/`            | `starlink-common`           | —                  | 前端公共能力，不直接对应 API |
| `modules/system/`    | `starlink-system`           | `/api/system/`    | 用户/角色/菜单/日志/公告     |
| `modules/member/`    | `starlink-module-member`    | `/api/member/`    | 会员 CRUD、储值、积分、等级  |
| `modules/session/`   | `starlink-module-session`   | `/api/session/`   | 座位图、上下机、预约         |
| `modules/billing/`   | `starlink-module-billing`   | `/api/billing/`   | 费率、计费记录、对账         |
| `modules/product/`   | `starlink-module-product`   | `/api/product/`   | 商品 CRUD、分类、套餐        |
| `modules/cashier/`   | `starlink-module-cashier`   | `/api/cashier/`   | 收银、订单、交班、日结       |
| `modules/inventory/` | `starlink-module-inventory` | `/api/inventory/` | 出入库、盘点、预警           |
| `modules/report/`    | `starlink-module-report`    | `/api/report/`    | 各类统计报表                 |
| `modules/marketing/` | `starlink-module-marketing` | `/api/marketing/` | 优惠券、促销、活动           |

---

## 6. 全局状态管理（Pinia）

### 6.1 全局 Store（`src/stores/`）

跨模块共享的状态，所有模块均可访问。

| Store                  | 文件                     | 职责                                     |
| :--------------------- | :----------------------- | :--------------------------------------- |
| `useUserStore`       | `stores/user.ts`       | 当前登录用户信息、Token、头像            |
| `usePermissionStore` | `stores/permission.ts` | 动态路由菜单、按钮权限码列表             |
| `useAppStore`        | `stores/app.ts`        | 应用配置（侧边栏折叠状态、主题色、语言） |

### 6.2 模块 Store（`src/modules/xxx/stores/`）

各模块私有，仅本模块使用。例如：

| 模块    | Store               | 职责                                                  |
| :------ | :------------------ | :---------------------------------------------------- |
| session | `useSessionStore` | 当前选中的机位、座位图实时状态缓存                    |
| cashier | `useCartStore`    | 购物车商品列表、当前结算金额                          |
| member  | `useMemberStore`  | 当前选中的会员信息（供收银等模块通过全局 Store 共享） |

---

## 7. 路由设计

### 7.1 路由组织策略

每个业务模块在 `modules/xxx/router.ts` 中导出自己的路由配置：

```typescript
// modules/member/router.ts
export default [
  {
    path: '/member',
    component: () => import('@/layouts/MainLayout.vue'),
    meta: { title: '会员管理', icon: 'User' },
    children: [
      { path: 'list', name: 'MemberList', component: () => import('./views/MemberList.vue') },
      { path: 'detail/:id', name: 'MemberDetail', component: () => import('./views/MemberDetail.vue') },
    ],
  },
]
```

主路由在 `router/index.ts` 中聚合所有模块路由：

```typescript
// router/index.ts
import systemRoutes from '@/modules/system/router'
import memberRoutes from '@/modules/member/router'
import sessionRoutes from '@/modules/session/router'
// ... 其他模块

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: () => import('@/layouts/BlankLayout.vue'), children: [...] },
    ...systemRoutes,
    ...memberRoutes,
    ...sessionRoutes,
    // ... 其他模块路由
  ],
})
```

### 7.2 路由守卫

在 `router/guards.ts` 中统一处理：

| 守卫     | 说明                                                          |
| :------- | :------------------------------------------------------------ |
| 登录拦截 | 未登录用户访问业务页面 → 跳转`/login`                      |
| 权限校验 | 根据`usePermissionStore` 中的权限码判断是否有权访问当前路由 |
| 页面标题 | 动态设置`document.title` 为当前页面标题                     |

---

## 8. API 通信规范

### 8.1 请求封装

所有 API 请求通过 `common/api/request.ts` 的 Axios 实例发出：

```typescript
// common/api/request.ts
const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,  // 环境变量配置
  timeout: 15000,
})

// 请求拦截：注入 Token
request.interceptors.request.use((config) => {
  const token = getToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// 响应拦截：统一错误处理
request.interceptors.response.use(
  (response) => response.data,
  (error) => {
    if (error.response?.status === 401) router.push('/login')
    ElMessage.error(error.response?.data?.message || '请求失败')
    return Promise.reject(error)
  }
)
```

### 8.2 模块 API 示例

```typescript
// modules/member/api/index.ts
import request from '@/common/api/request'
import type { PageResult, PageRequest } from '@/common/api/types'
import type { Member, MemberQuery } from '../types'

/** 分页查询会员列表 */
export function getMemberList(params: PageRequest & MemberQuery) {
  return request.get<any, PageResult<Member>>('/api/member/list', { params })
}

/** 获取会员详情 */
export function getMemberDetail(id: number) {
  return request.get<any, Member>(`/api/member/${id}`)
}
```

### 8.3 统一响应格式

后端返回统一结构，前端在 `request.ts` 中解包：

```typescript
// 后端响应结构
interface ApiResponse<T> {
  code: number      // 业务状态码（0 = 成功）
  message: string   // 提示信息
  data: T           // 业务数据
}

// 分页响应
interface PageResult<T> {
  records: T[]      // 当前页数据
  total: number     // 总记录数
  page: number      // 当前页码
  size: number      // 每页条数
}
```

---

## 9. 技术约定

| 约定项       | 规范                                                                                           |
| :----------- | :--------------------------------------------------------------------------------------------- |
| 组件命名     | PascalCase（`MemberList.vue`）                                                               |
| 文件命名     | 组件 PascalCase，其他 kebab-case（`use-pagination.ts`）                                      |
| 路由路径     | kebab-case（`/member/detail/:id`）                                                           |
| Store 命名   | `use{Module}Store`（`useMemberStore`）                                                     |
| API 函数命名 | camelCase，动词开头（`getMemberList`、`createMember`、`updateMember`、`deleteMember`） |
| 样式方案     | scoped CSS / CSS Modules，全局样式仅在`assets/styles/` 中定义                                |
| 类型优先     | 所有 API 入参、出参、组件 Props 必须有 TypeScript 类型定义                                     |
| 环境变量     | 通过`.env` 文件管理（`VITE_API_BASE_URL` 等），不硬编码                                    |
