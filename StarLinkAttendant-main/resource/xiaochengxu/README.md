# 星络灵侍馆 · 微信小程序用户端

星络灵侍馆管理系统（Internet Cafe Management System）的**会员顾客端**小程序。
顾客在这里自助完成「扫码上机 → 充值 → 点单 → 领券 → 查账」，减少前台排队。

技术栈：**uni-app (Vue 3 + TypeScript + Vite)** + Pinia，一套代码同时编译 H5 与微信小程序。

---

## 一、快速开始

```bash
cd xiaochengxu
npm install

# H5 调试（浏览器打开 http://localhost:5174）
npm run dev

# 微信小程序：编译后用微信开发者工具打开 dist/dev/mp-weixin
npm run dev:mp-weixin

# 生产构建
npm run build:h5
npm run build:mp-weixin

# 类型检查
npm run type-check
```

Node 要求 `>= 18`（开发环境实测 v22.16.0）。

---

## 二、数据源：Mock 与真实后端的切换

**这是本项目最重要的一条约定。**

后端 `cafe-module-applet` 模块（`/api/applet/**`）目前**尚未实现**（见 `doc/项目需求文档.md` §7.3），
因此项目采用「前端完整实现 + Mock 数据层 + 真实接口预留」架构。

切换开关是环境变量 `VITE_USE_MOCK`：

| 文件 | 值 | 效果 |
|---|---|---|
| `.env.development` | `true` | 全部请求由 `src/mock/**` 接管 |
| 改成 `false` | `false` | 直接打真实后端 `/api/applet/**` |

**业务代码零改动**。因为 mock 层返回的就是标准 `ApiResponse`，
`src/api/request.ts` 里拆包 / 401 / 错误提示走的是同一条代码路径。

```ts
// src/api/request.ts
const task = USE_MOCK
  ? mockRequest({ url, method, data, auth, token })   // 本地 mock
  : rawRequest(options, token)                        // uni.request → 后端
```

启动时控制台会打印当前数据源，避免出现「以为在联调其实在跑 mock」。

### 接口契约

`src/api/*.ts` 里的路径**严格对齐** `doc/项目需求文档.md` §7.4 的 16 个接口，
例如 `POST /api/applet/auth/login`、`GET /api/applet/member/summary`、
`POST /api/applet/session/scan-start`、`POST /api/applet/orders` 等。

Mock 路由表在 `src/mock/routes.ts`，未实现的路径会明确返回
`{ code: 404, message: '接口未实现：METHOD /path' }`，**不会静默返回假数据**。

### Mock 数据会持久化

`src/mock/db.ts` 把状态写入 uni storage，所以充值、下单、扫码上机之后
刷新页面余额和订单还在，demo 流程是连贯的。
设置页提供「重置演示数据」入口。

---

## 三、目录结构

```
src/
├── api/                 # 接口层：每个模块一个文件，统一走 request.ts
│   ├── request.ts       # ★ 统一请求封装 + Mock/真实切换
│   └── auth / member / session / reservation / product / order /
│       recharge / coupon / points / game / community / store
├── components/          # 通用组件（见下表）
│   └── icons.ts         # ★ SVG 图标库（base64 data URI）
├── composables/
│   └── useAsyncData.ts  # 统一 loading / empty / error / success 四态
├── config/index.ts      # 常量：storage key、TabBar 配置、默认门店
├── mock/                # Mock 数据层
│   ├── seed.ts          # 种子数据（文案还原截图）
│   ├── db.ts            # 有状态 + 持久化
│   ├── routes.ts        # 路由表（对齐 §7.4 契约）
│   └── index.ts         # 分发器
├── pages/               # 页面
├── static/images/       # 本地占位图（由 scripts/gen_placeholders.py 生成）
├── stores/              # Pinia：user / cart / app
├── types/               # TypeScript 类型定义
├── utils/               # format / storage / ui / nav / system
├── uni.scss             # ★ 设计变量（uni-app 自动注入每个 style 块）
├── App.vue              # 全局样式
├── pages.json           # 路由 + 导航栏配置
└── manifest.json        # 小程序/ H5 打包配置
```

---

## 四、开发约定（改代码前必读）

### 1. 尺寸一律用 `rpx`

`750rpx = 屏幕宽度`，iPhone / Android 自动等比缩放。
`src/uni.scss` 已定义全部设计变量，**不需要手动 import**——uni-app 会把 `uni.scss`
自动注入到每个 `<style lang="scss">` 的最前面。

> ⚠️ 因此 style 块里**不能写 `@use` / `@import`**，否则会报
> `@use rules must be written before any other rules`。全局样式统一写在 `App.vue`。

### 2. flex 子项必须写 `min-width: 0`

这是本项目踩过的真实坑：flex 子项默认 `min-width: auto`，内容会把容器撑开，
导致**整页横向溢出**（第 5 个功能图标、第 5 个 Tab 被挤出屏幕）。
凡是 `flex: 1` 的元素，一律补 `min-width: 0`，文字再加 `@include ellipsis`。

### 3. 图标用 `AppIcon`，不要用 emoji

```vue
<AppIcon name="search" :size="40" color="#8A8A99" :stroke-width="1.7" />
```

图标库在 `src/components/icons.ts`：每个图标是一段 SVG 片段，
运行时编码成 **base64 data URI 挂到 `background-image`**。

> 为什么这么做：微信小程序 WXML **不支持内联 `<svg>` 标签**，
> 而 PNG 图标又要维护一堆二进制文件、还没法做渐变。
> data URI 方案在 H5 与微信小程序上渲染完全一致，零二进制资源。

新增图标：往 `ICONS` 里加一条 `24×24` viewBox 的路径即可，
颜色占位符写作 `{c}`（渐变写 `linear-gradient(...)` 会自动转成 SVG 渐变）。

### 4. 每个异步页面都要有三态

用 `useAsyncData` 或手动维护 `state`，配合 `StateView` 渲染：

```vue
<StateView v-if="state !== 'success'" :state="state" @retry="reload" />
```

`StateView` 支持 `loading` / `empty` / `error`，**禁止出现白屏**。

### 5. 路由跳转统一走 `src/utils/nav.ts`

```ts
navTo('/pages/xxx')      // 普通页面
switchTab('/pages/xxx')  // 5 个 Tab 页（内部用 reLaunch，保证页面栈干净）
goLogin()                // 跳登录页并带 redirect 回跳
```

### 6. 安全区适配

底部固定元素（TabBar、吸底按钮）必须叠加安全区：

```scss
padding-bottom: calc(#{$tabbar-height} + constant(safe-area-inset-bottom));
padding-bottom: calc(#{$tabbar-height} + env(safe-area-inset-bottom));
```

有 TabBar 的页面根节点加 `page-root--has-tabbar`，它会自动预留 TabBar 高度，
避免内容被遮挡。

### 7. 金额与幂等

- 后端金额单位是**元**（`decimal(12,2)`），展示统一用 `formatMoney()`。
- 下单 / 充值等资金操作必须带 `idempotentKey`（`genIdempotentKey()`），
  防止重复点击产生多笔订单（见 `doc/项目需求文档.md` §9）。

---

## 五、通用组件

| 组件 | 用途 | 关键 props |
|---|---|---|
| `AppIcon` | 图标 | `name` `size` `color` `strokeWidth` |
| `AppNavBar` | 自定义导航栏 | `title` `align` `theme` `showBack` |
| `AppStatusBar` | 状态栏占位 | `bg` |
| `AppTabBar` | 底部 5 Tab | `current` |
| `AppButton` | 按钮 | `type` `size` `block` `disabled` `loading` `pill` |
| `BannerSwiper` | 首页大 Banner 轮播 | `list` `height` `interval` |
| `SectionHeader` | 区块标题 | `title` `icon` `moreText` `showHelp` |
| `StateView` | loading / empty / error | `state` `emptyText` `retry` |
| `GameLogo` | 游戏图标（品牌色方块 + 短名） | `short` `color` `size` `active` |
| `CommunityPost` | 社区帖子卡片 | `post` `showActions` |

---

## 六、素材：占位图与替换方式

### 6.1 当前状态

`src/static/images/` 下 72 张图**全部是程序生成的占位图**，
由 `scripts/gen_placeholders.py` 生成（确定性、可重复执行，不联网、无版权素材）。

```bash
python scripts/gen_placeholders.py
```

参考截图里的游戏原画 / 商品实拍 / 门店照片都有版权，不能直接使用，
所以这里用「渐变 + 几何图形」生成一套风格统一（蓝紫主色系）的替代图。

> **全部输出 JPEG，不是 PNG。**
> 原因是微信小程序主包上限 2MB，渐变类图片用 PNG 会浪费 4~5 倍体积
> （实测 69 张 PNG 占 2.5MB，导致主包 3.4MB **无法上传**；转 JPEG 后降到 800KB）。

### 6.2 替换真实素材（不用改业务代码）

保持**路径和文件名不变**，直接覆盖即可：

| 目录 | 用途 | 建议尺寸 |
|---|---|---|
| `banner/` | 首页大 Banner、328 礼包横幅 | 1500 × 580 px |
| `product/` | 商品图 | 400 × 400 px |
| `game/task{n}-{a..d}.jpg` | 游戏任务 2×2 缩略图 | 240 × 240 px |
| `community/` | 社区配图 / 视频封面 | 封面 1280 × 720，配图 800 × 900 |
| `store/` `hotel/` | 门店图、房型图 | 640 × 480 px |
| `avatar/` | 社区头像（方图即可，CSS 会裁圆） | 160 × 160 px |

**游戏 Logo 是唯一的例外**：目前由 `GameLogo.vue` 用「品牌色块 + 游戏短名」兜底渲染。
拿到官方 logo 后，把图片放进 `src/static/images/game/`，
再到 `src/mock/seed.ts` 的 `seedGames` 里把 `logo` 字段填上路径即可，页面代码无需改动：

```ts
{ id: 4, name: '英雄联盟', short: '英雄', logo: `${IMG}/game/logo-lol.png`, ... }
```

传入 `logo` 时组件渲染 `<image>`，留空时自动回退到色块，所以**渐进替换是安全的**。

### 6.3 包体积红线

微信小程序**主包上限 2MB**（整包 20MB）。
当前主包 **800KB**。新增素材时请留意：超过 2MB 会导致**无法上传**
（开发者工具预览正常，但上传报错）。

单张图片建议控制在 150KB 以内；超过时优先考虑压缩或改走 CDN（需在微信后台配置 downloadFile 合法域名）。

---

## 七、设计基准

- 主色 `#5B5BD6`（蓝紫），辅色浅蓝 / 浅紫 / 白 / 浅灰
- 页面背景 `#F1F3F9`，卡片白色，圆角 `16~32rpx`
- 5 张参考截图在 `reference/` 目录，是 UI 的唯一视觉基准

---

## 八、后端联调状态

| 项 | 状态 |
|---|---|
| 后端 `cafe-module-applet` 模块 | ❌ 不存在 |
| `/api/applet/**` 接口 | ❌ 全部未实现 |
| 现有 `/api/member/**`、`/api/session/**` 等 | ⚠️ 是管理端接口，走 employee JWT，小程序无法直接调用 |
| 统一响应结构 `{code,message,data,timestamp}` | ✅ 与后端 `ApiResponse.java` 完全一致，可直接对齐 |
| 前端接口层 | ✅ 已按 §7.4 契约写好，`VITE_USE_MOCK=false` 即可切 |

后端接口就绪后**只需**：
1. 把 `.env.development` / `.env.production` 的 `VITE_USE_MOCK` 改成 `false`；
2. 若接口域名与 H5 不同源，在 `.env` 里配置 `VITE_API_BASE_URL`（微信小程序必须填完整域名并在微信后台配置 request 合法域名）。
