# 模块设计文档

> **项目编号：** IT-26-7 Demo
> **架构模式：** 模块化单体（Modular Monolith）
> **技术栈：** Java 17 + Spring Boot 3.4.13 + MyBatis-Plus 3.5.5
> **更新日期：** 2026-07-13

---

## 1. 模块划分

系统按业务领域划分为公共层、系统层、业务层三类模块：

| 模块划分 | 包含模块 |
| :---|:---|
| 基础架构 | `starlink-common`、`starlink-module-session` |
| 会员 + 员工管理 | `starlink-system`、`starlink-module-member` |
| 计费 + 库存管理 | `starlink-module-billing`、`starlink-module-inventory`（规划中，本 Demo 未实现） |
| 收银 + 商品销售 | `starlink-module-cashier`、`starlink-module-product` |
| 报表 + 营销 + 第三方集成 | `starlink-module-report`、`starlink-module-marketing` |

> `starlink-starter` 为启动聚合模块，负责整体搭建与全局配置管理。

---

## 2. 模块总览

```
StarLinkAttendant/                          ← 父 POM（聚合 + 依赖版本管理）
├── starlink-common/                       ← 公共工具层
├── starlink-system/                       ← 系统基础层
├── starlink-module-member/                ← 业务层
├── starlink-module-session/
├── starlink-module-billing/               （规划中，未实现）
├── starlink-module-product/
├── starlink-module-cashier/
├── starlink-module-inventory/             （规划中，未实现）
├── starlink-module-report/
├── starlink-module-marketing/
└── starlink-starter/                      ← 启动入口层
```

当前代码中实际包含 **9 个子模块**，分为三层：公共层（2）、业务层（6）、启动层（1）。计费、库存两个模块仅保留设计，待后续实现。

---

## 3. 模块设计详情

### 3.1 公共层

#### starlink-common — 公共工具模块

| 属性                 | 值                                       |
| :------------------- | :--------------------------------------- |
| **artifactId** | `starlink-common`                          |
| **包路径**     | `com.starlink.common`                    |
| **职责**       | 全项目公共基础设施，所有模块均依赖此模块 |

**核心内容：**

| 分类         | 说明                                                                          |
| :----------- | :---------------------------------------------------------------------------- |
| 统一响应体   | `Result<T>` 封装，统一 API 返回格式                                         |
| 全局异常处理 | `@RestControllerAdvice` 全局异常拦截与错误码体系                            |
| 通用实体基类 | `BaseEntity`（`created_at`、`updated_at`、`deleted_at`、`version`） |
| Redis 工具   | Redis 操作封装（缓存读写、分布式锁、ZSet 排行榜等）                           |
| OSS 封装     | 阿里云 OSS 文件上传/下载/签名 URL 生成                                        |
| 工具类       | 日期处理、金额计算、分页封装等通用工具                                        |
| 常量定义     | 全局枚举与常量（订单状态、支付类型等）                                        |

**依赖：** `spring-boot-starter-web`、`spring-boot-starter-validation`、`spring-boot-starter-data-redis`、`hutool-all`

---

#### starlink-system — 系统基础模块

| 属性                 | 值                                                      |
| :------------------- | :------------------------------------------------------ |
| **artifactId** | `starlink-system`                                         |
| **包路径**     | `com.starlink.system`                                   |
| **职责**       | 全系统基础能力：RBAC 权限、认证授权、操作日志、通知公告 |
| **依赖**       | `starlink-common`                                         |

**核心内容：**

| 分类      | 说明                                                   |
| :-------- | :----------------------------------------------------- |
| 认证授权  | Spring Security 集成，JWT Token 签发与校验             |
| RBAC 权限 | 用户 → 角色 → 菜单/权限 三级模型，细粒度接口权限控制 |
| 用户管理  | 管理员/员工账号的 CRUD、密码加密、状态管控             |
| 角色管理  | 角色定义、权限分配、角色继承                           |
| 菜单管理  | 动态菜单树，支撑前端路由与按钮级权限                   |
| 操作日志  | `@OperateLog` 注解 + AOP 切面，记录关键操作审计日志  |
| 通知公告  | 系统公告发布、推送与已读状态管理                       |

**依赖：** `starlink-common`、`spring-boot-starter-security`、`mybatis-plus-spring-boot3-starter`、`mysql-connector-j`

---

### 3.2 业务层

#### starlink-module-member — 会员模块

| 属性                 | 值                     |
| :------------------- | :--------------------- |
| **artifactId** | `starlink-module-member` |
| **包路径**     | `com.starlink.member`  |
| **需求编号**   | MBR-01 ~ MBR-07        |
| **依赖**       | `starlink-common`        |

**功能范围：** 会员注册与信息管理、会员等级体系（成长值升降级）、储值账户（充值/消费/余额）、积分规则（获取/消耗/过期）、会员卡管理（开卡/挂失/注销）、行为画像分析、黑名单管控机制。

---

#### starlink-module-session — 上机模块

| 属性                 | 值                      |
| :------------------- | :---------------------- |
| **artifactId** | `starlink-module-session` |
| **包路径**     | `com.starlink.session`  |
| **需求编号**   | COM-01 ~ COM-08         |
| **依赖**       | `starlink-common`         |

**功能范围：** 座位图可视化（实时渲染、状态标识、交互操作）、上下机状态机（状态流转引擎）、临时下机/换机/预约/强制下机等业务场景、管理员审批流程闭环。

> 本模块为系统核心，实时性要求高、并发场景多，需保障状态准确性与一致性。

---

#### starlink-module-billing — 计费模块（规划中，未实现）

| 属性                 | 值                      |
| :------------------- | :---------------------- |
| **artifactId** | `starlink-module-billing` |
| **包路径**     | `com.starlink.billing`  |
| **需求编号**   | BIL-01 ~ BIL-07         |
| **依赖**       | `starlink-common`         |

**功能范围：** 多元计费模式（阶梯定价、按时计费、包时计费）、动态调价策略、优惠减免规则、滞纳金计算、计费心跳校验（配合 XXL-Job 每 30 秒校验）、对账逻辑。

---

#### starlink-module-product — 商品模块

| 属性                 | 值                      |
| :------------------- | :---------------------- |
| **artifactId** | `starlink-module-product` |
| **包路径**     | `com.starlink.product`  |
| **需求编号**   | POS-01 ~ POS-06         |
| **依赖**       | `starlink-common`         |

**功能范围：** 商品 CRUD（分类、规格、图片）、扫码收银、散客开台、套餐组合管理、机位配送（商品送至机位）、商品退换货。

---

#### starlink-module-cashier — 收银模块

| 属性                 | 值                      |
| :------------------- | :---------------------- |
| **artifactId** | `starlink-module-cashier` |
| **包路径**     | `com.starlink.cashier`  |
| **需求编号**   | CSH-01 ~ CSH-06         |
| **依赖**       | `starlink-common`         |

**功能范围：** 收银台核心（下单、结算、找零）、交班结算（班次管理、交接对账）、日结汇总（每日营收统计）、多支付方式集成（现金/微信/支付宝/会员余额）、退款处理、发票开具。

---

#### starlink-module-inventory — 库存模块（规划中，未实现）

| 属性                 | 值                        |
| :------------------- | :------------------------ |
| **artifactId** | `starlink-module-inventory` |
| **包路径**     | `com.starlink.inventory`  |
| **需求编号**   | INV-01 ~ INV-05           |
| **依赖**       | `starlink-common`           |

**功能范围：** 商品入库/出库、库存盘点（盘盈盘亏处理）、库存预警（低于安全库存自动告警）、供应商信息管理、库存流水追溯。

---

#### starlink-module-report — 报表模块

| 属性                 | 值                     |
| :------------------- | :--------------------- |
| **artifactId** | `starlink-module-report` |
| **包路径**     | `com.starlink.report`  |
| **需求编号**   | REP-01 ~ REP-07        |
| **依赖**       | `starlink-common`        |

**功能范围：** 营业总览（今日/本周/本月概览）、营收分析（按时间段/支付方式/商品类别）、上机分析（上座率、时段分布、平均时长）、商品排行（销量/销售额排名）、会员分析（新增/活跃/留存）、财务报表、数据导出（Excel / PDF）。

---

#### starlink-module-marketing — 营销模块

| 属性                 | 值                               |
| :------------------- | :------------------------------- |
| **artifactId** | `starlink-module-marketing`        |
| **包路径**     | `com.starlink.marketing`         |
| **需求编号**   | MKT-01 ~ MKT-05、INT-01 ~ INT-03 |
| **依赖**       | `starlink-common`                  |

**功能范围：** 优惠券管理（发放/核销/过期清理）、充值促销活动（充送规则配置）、限时活动（时段特价、节假日促销）、拉新裂变（邀请奖励）、第三方集成（实名认证模拟、短信服务模拟、公安审计接口模拟）。

---

### 3.3 启动层

#### starlink-starter — 启动入口模块

| 属性                 | 值                                           |
| :------------------- | :------------------------------------------- |
| **artifactId** | `starlink-starter`                             |
| **包路径**     | `com.starlink`                               |
| **职责**       | 应用启动入口，聚合所有模块，集中管理全局配置 |
| **依赖**       | `starlink-system` + 全部 8 个业务模块          |

**核心内容：**

| 分类         | 说明                                                           |
| :----------- | :------------------------------------------------------------- |
| 启动类       | `StarLinkAttendantApplication`（`@SpringBootApplication`）      |
| 全局配置     | `application.yml`（数据源、Redis、MyBatis-Plus、Jackson 等） |
| 基础设施配置 | MyBatis-Plus 配置类、Redis 序列化配置、CORS 跨域配置等         |
| 打包部署     | 唯一配置`spring-boot-maven-plugin` 的模块，输出可执行 JAR    |
| 健康检查     | `spring-boot-starter-actuator`（`/actuator/health`）       |

---

## 4. 模块依赖关系

```
                        ┌──────────────┐
                        │ starlink-starter │  ← 聚合所有模块，唯一启动入口
                        └──────┬───────┘
                               │ 依赖
          ┌────────────────────┼────────────────────┐
          │                    │                    │
    ┌─────┴─────┐    ┌────────┴────────┐    ┌──────┴──────┐
    │ starlink-system│    │  8 个业务模块    │    │  (未来扩展)  │
    └─────┬─────┘    └────────┬────────┘    └─────────────┘
          │                   │
          │         ┌─────────┴─────────┐
          └─────────┤    starlink-common    │  ← 所有模块的公共基座
                    └───────────────────┘
```

**依赖规则：**

| 层级   | 模块                      | 依赖                                       |
| :----- | :------------------------ | :----------------------------------------- |
| 基座层 | `starlink-common`           | 无内部依赖（仅第三方库）                   |
| 基础层 | `starlink-system`           | →`starlink-common`                          |
| 业务层 | `starlink-module-*`（8 个） | →`starlink-common`                          |
| 聚合层 | `starlink-starter`          | →`starlink-system` + 全部 `starlink-module-*` |

**跨模块调用：** 业务模块间通过 Spring Bean 注入直接调用（同一 JVM），不引入额外 RPC 开销。例如收银模块调用会员模块查询余额、计费模块调用上机模块获取会话时长。

---

## 5. 技术约定

| 约定项           | 规范                                                                                          |
| :--------------- | :-------------------------------------------------------------------------------------------- |
| Java 版本        | 17（LTS）                                                                                     |
| Spring Boot 版本 | 3.4.13                                                                                        |
| 包路径根         | `com.starlink`                                                                                |
| ORM              | MyBatis-Plus 3.5.5（`mybatis-plus-spring-boot3-starter`）                                   |
| 数据库           | MySQL 8.0+，字符集`utf8mb4`，引擎 InnoDB                                                    |
| 缓存             | Redis 7+（单机模式）                                                                          |
| 主键策略         | `id`（bigint unsigned，自增）                                                               |
| 公共字段         | 每张表必须包含`created_at`、`updated_at`、`deleted_at`（软删除）、`version`（乐观锁） |
| 资金安全         | 余额变更必须走流水表，禁止直接 UPDATE 主表                                                    |
| 工具库           | Hutool 5.8.25                                                                                 |
| 构建工具         | Maven 多模块                                                                                  |
