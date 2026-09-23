# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**StarLinkAttendant (星络灵侍馆)** — 单店网吧综合管理系统（脱机演示版）。覆盖会员管理、上机与自动计费、商品销售、收银交班日结、营销活动、经营报表、RBAC 系统管理等业务闭环。所有支付/短信/实名/公安审计能力均为**模拟实现**，不依赖外网。

**Status:** 已实现可运行的前后端系统 + MySQL 数据库脚本，非纯设计阶段。

## Architecture（实际技术栈）

- **Frontend:** Vue 3.5 + TypeScript + Vite 8 + Element Plus 2.14 + Pinia 3 + Vue Router + Axios（单管理端工程，无独立收银端/小程序）
- **Backend:** Java 17 + Spring Boot 3.4.x（**单体模块化**，非微服务）+ MyBatis-Plus 3.5.5 + Spring Security/JWT
- **Database:** MySQL 8.0+（单库 `starlink_attendant`，40 张表，无分库分表）
- **Scheduling:** Spring `@Scheduled`（会话计费/预约超时每分钟、优惠券过期每日 01:00）
- **Build:** Maven 多模块（9 个子模块），启动模块 `starlink-starter`
- **未启用:** Redis、MQ、Elasticsearch、OSS、Docker/K8s（common 中仅有工具类预留）
- **文件存储:** 本地 `./uploads`

## Key Documents

| Document | Path | Description |
|----------|------|-------------|
| README | `README.md` | 本地环境配置与启动指南（先读） |
| Requirements | `doc/需求文档.md` | 需求文档（功能需求、实现状态、P0-P2 优先级） |
| Requirements Design | `doc/需求设计说明书.md` | 架构、数据库、接口、安全与定时任务设计 |
| Database Design | `doc/database-design.md` | 数据库设计说明（ER、命名、索引约定） |
| API Spec | `doc/Front_Back-API.md` | 前后端接口规范 |
| SQL Schema | `resource/sql/DataBase.sql` | 建库建表 DDL（可重复执行） |
| Seed Data | `resource/sql/Insert.sql` | 演示种子数据（密码 123456，BCrypt 哈希） |

## Module Structure

Maven 聚合根：`BackEnd/Backend`（父 POM `com.starlink:parent`）。

| Module | Package | Responsibility |
|--------|---------|----------------|
| starlink-common | com.starlink.common | Result/分页/全局异常/BaseEntity/CommonEnums/工具 |
| starlink-system | com.starlink.system | 认证 JWT、Security、员工/角色/菜单/权限、审计日志、公告、首页看板 |
| starlink-module-member | com.starlink.member | 会员、等级、充值、积分、黑名单 |
| starlink-module-session | com.starlink.session | 区域机位、会话状态机、按时计费引擎、预约（核心） |
| starlink-module-product | com.starlink.product | 商品、分类、套餐 |
| starlink-module-cashier | com.starlink.cashier | 订单、支付、退款、班次、日结 |
| starlink-module-report | com.starlink.report | 营收/上机/商品/会员/财务统计、导出 |
| starlink-module-marketing | com.starlink.marketing | 活动、优惠券、充值促销、模拟第三方集成 |
| starlink-starter | com.starlink | 启动入口与全局配置（CORS/Jackson/MyBatis-Plus） |

前端工程：`resource/FrontEnd/StarLinkAttendant`，按 `src/modules/<domain>/` 组织（api/views/stores/router/types）。

## Conventions

- **JDK 必须为 17**；Node.js ≥ 22.18。
- **统一响应:** `Result<T>`，成功 `code=0`；分页 `PageResult<T>`。
- **每张表公共字段:** `id`、`created_at`、`updated_at`、`deleted_at`（逻辑删除）、`version`（乐观锁）。
- **命名:** 表/字段蛇形小写、无前缀；utf8mb4 / InnoDB；金额一律 `DECIMAL`（Java 用 BigDecimal，禁 double）。
- **资金安全:** 余额变动必须写流水（member_recharge / payment_record / refund_record），业务方法加事务。
- **登录名:** 员工与会员均使用手机号；密码 BCrypt 校验。
- **分层:** Controller → Service(impl) → Mapper；复杂 SQL 写在各模块 `resources/mapper/*.xml`。
- **业务单号:** 前缀 + yyyyMMdd + 3 位序号（S 会话、OD 订单、R 充值、PY 支付、RF 退款、SH 班次、DS 日结、CPN 券等）。
- **配置:** 数据源在 `starlink-starter/src/main/resources/application-dev.yml`，新环境需改本机 MySQL 密码。

## Known Gaps（数据层已设计、业务未实现）

- 库存出入库/盘点/预警（inventory、inventory_log、purchase_order*、supplier 表已存在，无 Controller）。
- 套餐功能后端实体/接口已写（`product_combo`、`product_combo_item`），但 DDL 尚未加入 DataBase.sql。
- 包时/动态费率、考勤打卡与提成（attendance_record、commission_* 表已存在）、发票、拉新裂变。
