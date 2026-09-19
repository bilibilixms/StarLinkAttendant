# 星络灵侍馆（StarLinkAttendant）

> 一套面向单店网吧的综合管理系统（脱机演示版）。
> 覆盖「上机自动计费 → 会员储值 → 商品收银 → 交班日结 → 经营分析」完整业务闭环。
> 全程脱机运行，不接入真实支付 / 短信 / 实名 / 公安平台，外部能力均为模拟实现。

---

## 一、功能概览

| 业务域 | 主要功能 |
| :--- | :--- |
| 上机计费（核心） | 座位图、会员/散客开机、服务端**每分钟自动计费**、临时下机、换机、强制下机、预约（超时自动释放）、费率方案 |
| 会员管理 | 会员注册、等级折扣、储值充值（充赠）、积分流水、黑名单/冻结 |
| 商品管理 | 商品分类、商品档案、上下架、套餐组合 |
| 收银管理 | 收银开单、现金/微信/支付宝/会员余额支付（模拟）、退款、开班交班（长短款）、日结归档 |
| 营销活动 | 限时活动、优惠券模板/发放/核销（每日凌晨自动过期）、充值满赠促销 |
| 经营报表 | 经营看板、营收/上机/商品排行/会员/财务分析、Excel 导出 |
| 系统管理 | JWT 登录、RBAC 角色权限、员工/菜单管理、操作审计日志、通知公告 |
| 第三方集成（模拟） | 实名认证、短信发送、公安上网审计，仅写本地日志不真实调用 |

> 库存出入库、包时费率、考勤提成等仅完成数据库表设计，属于后续迭代预留。

---

## 二、技术栈

| 层 | 技术 | 版本要求 |
| :--- | :--- | :--- |
| 后端 | Java、Spring Boot、MyBatis-Plus、Spring Security + JWT、Maven 多模块 | **JDK 17（必须）**、Maven 3.8+ |
| 数据库 | MySQL（InnoDB / utf8mb4） | **8.0+** |
| 前端 | Vue 3 + TypeScript + Vite + Element Plus + Pinia + Axios | **Node.js 22.18+** |

不需要安装 Redis，不需要外网，所有第三方服务均为模拟实现。

---

## 三、目录结构

```
StarLinkAttendant/
├── BackEnd/Backend/                      # 后端（Maven 多模块）
│   ├── starlink-common/                  # 统一响应、异常、枚举、基类
│   ├── starlink-system/                  # 登录认证、JWT、RBAC、日志、公告
│   ├── starlink-module-member/           # 会员 / 等级 / 充值 / 积分
│   ├── starlink-module-session/          # 机位、会话、自动计费、预约（核心）
│   ├── starlink-module-product/          # 商品 / 分类 / 套餐
│   ├── starlink-module-cashier/          # 订单 / 支付 / 退款 / 交班 / 日结
│   ├── starlink-module-report/           # 经营报表
│   ├── starlink-module-marketing/        # 活动 / 优惠券 / 模拟第三方
│   └── starlink-starter/                 # 启动模块（唯一 main 入口、配置文件）
├── resource/
│   ├── FrontEnd/StarLinkAttendant/       # 前端工程
│   └── sql/
│       ├── DataBase.sql                  # 建库建表脚本（40 张表，可重复执行）
│       └── Insert.sql                    # 演示种子数据（账号、近两天业务数据）
└── doc/                                  # 需求与设计文档
```

---

## 四、本地运行（三步启动）

### 环境准备

| 软件 | 版本 | 验证命令 |
| :--- | :--- | :--- |
| JDK | **17（必须是 17，高版本如 JDK 21/25 可能无法正常运行）** | `java -version` |
| Maven | 3.8+ | `mvn -v` |
| MySQL | 8.0+，服务已启动 | `mysql --version` |
| Node.js | 22.18+（Vite 8 要求） | `node -v` |

### 第 1 步：初始化数据库

打开命令行，依次执行两条脚本（把 `-p` 后密码换成你本机 MySQL 的 root 密码）：

```bash
# 1) 建库建表（脚本会自动创建 starlink_attendant 库，共 40 张表）
mysql -uroot -p < resource/sql/DataBase.sql

# 2) 导入演示数据（员工、会员、费率、机位、近两天会话/订单/充值等）
mysql -uroot -p starlink_attendant < resource/sql/Insert.sql
```

也可以用 Navicat / DataGrip 等图形工具直接运行这两个 SQL 文件（先 DataBase.sql，后 Insert.sql）。

### 第 2 步：修改后端数据库密码（必改）

打开文件：

```
BackEnd/Backend/starlink-starter/src/main/resources/application-dev.yml
```

把数据源账号密码改成本机 MySQL 的账号密码（默认用户名 `root`）：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/starlink_attendant?useUnicode=true&characterEncoding=UTF-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root
    password: 你的MySQL密码
```

> 如果 MySQL 不在本机或端口不是 3306，同步修改 `url` 中的地址和端口。

### 第 3 步：启动后端

确保 `JAVA_HOME` 指向 **JDK 17**（这是最常见的启动失败原因），然后：

```bash
cd BackEnd/Backend
mvn -pl starlink-starter spring-boot:run
```

启动成功后：

- 后端地址：http://localhost:8080
- 健康检查：http://localhost:8080/actuator/health 返回 `UP`
- 看到控制台输出会话计费定时任务日志（每分钟扫描进行中会话）即正常

也可以直接用 IDEA 打开 `BackEnd/Backend`（Maven 工程），确认 Project SDK = 17，运行启动类：
`starlink-starter/src/main/java/com/starlink/StarLinkAttendantApplication.java`

### 第 4 步：启动前端

```bash
cd resource/FrontEnd/StarLinkAttendant
npm install
npm run dev
```

浏览器打开终端提示的地址（默认 http://localhost:5173/ ）。

> 如果后端不在 8080 端口或部署在其他机器，修改前端文件
> `resource/FrontEnd/StarLinkAttendant/.env` 中的 `VITE_API_BASE_URL` 后重启前端。

---

## 五、演示账号

种子数据内置员工账号与会员账号，**密码统一为 `123456`**，登录名一律使用**手机号**：

| 角色 | 手机号 | 姓名 | 权限说明 |
| :--- | :--- | :--- | :--- |
| 店长 | 13800000001 | 张伟 | 全部功能、日结确认、角色授权 |
| 副店长 | 13800000002 | 李娜 | 运营管理、会员处置、营销配置 |
| 收银员 | 13800000003 | 王磊 | 开卡充值、上下机、收银、退款、交班 |
| 收银员 | 13800000004 | 赵静 | 同上 |
| 网管 | 13800000005 | 刘洋 | 机位维护、换机、强制下机 |

会员测试账号：手机号 `13811001001` ～ `13811001010`，密码同为 `123456`。

建议演示路径：店长账号登录 → 首页看板（今日营收/在线人数/机位使用率）→ 上机管理查看进行中会话（金额每分钟自动增长）→ 收银台开单支付/退款 → 交班/日结 → 经营报表。

---

## 六、常见问题

**1. 后端启动报版本不兼容 / 编译失败？**
JDK 版本不对。本项目必须使用 **JDK 17**。命令行启动先设置 `JAVA_HOME` 指向 JDK17 目录（IDEA 中在 Project Structure 把 SDK 和 Language Level 都改为 17）。

**2. 启动报 `Access denied for user 'root'` 或连不上数据库？**
`application-dev.yml` 中的数据库密码/地址与本机不一致，按「第 2 步」修改。

**3. 前端页面能打开但登录没反应 / 提示网络错误？**
后端没启动或端口不是 8080。确认 http://localhost:8080/actuator/health 可访问；如改过后端端口，同步改前端 `.env` 的 `VITE_API_BASE_URL`。

**4. 端口被占用？**
- 8080 被占：关闭占用进程，或修改 `application.yml` 中 `server.port`（同时改前端 `.env`）。
- 5173 被占：Vite 会自动顺延到 5174 等端口，以终端输出为准。

**5. 想重置演示数据？**
重新按顺序执行 `DataBase.sql`、`Insert.sql` 即可（脚本会先清空再重建）。

**6. 进行中会话的数据为什么一直在变？**
这是正常现象：后端计费定时任务每分钟对进行中会话自动计费并更新余额，用于演示实时计费效果。重置数据后重启后端即可恢复初始状态。

---

## 七、补充说明

- 系统为**脱机演示项目**：支付、短信、实名认证、公安审计均为模拟实现，不会发生真实交易或对外请求。
- 金额使用 `DECIMAL` 存储（单位：元）；业务表统一带逻辑删除（`deleted_at`）与乐观锁（`version`）。
- 需求与设计细节见 `doc/` 目录：《需求文档.md》《需求设计说明书.md》《database-design.md》《Front_Back-API.md》。

---

*初始版本 · 2026-09*
