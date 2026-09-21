# JWT 登出黑名单方案

## Context

当前项目的 JWT 是完全无状态的——`JwtTokenUtil` 生成 token 后不存任何地方，`JwtAuthenticationFilter` 只校验签名+过期就放行。后果是：用户点"退出登录"时，前端只是删了本地 token，后端不知道，被截获的 token 在过期前还能继续用。

`AuthService.logout()` 当前是空实现（[L78-L80](file:///e:/StarLinkAttendant/BackEnd/Backend/starlink-system/src/main/java/com/starlink/system/service/AuthService.java#L78-L80)），`AuthController` 的 `/api/auth/logout` 接口已存在但不做事。本方案补齐这块：登出时把 token 标识写进 Redis 黑名单（带 TTL），过滤器多查一步 Redis，实现 token 主动失效。

Redis 已接入（前一轮已完成 `spring-boot-starter-data-redis` 引入 + `application-dev.yml` 配置），本方案直接使用。

## 实施步骤

### 1. 新建 RedisService 工具类

路径：`e:/StarLinkAttendant/BackEnd/Backend/starlink-common/src/main/java/com/starlink/common/util/RedisService.java`

放在现有 `util/` 目录下（与 DateUtils、MoneyUtils 同级）。

封装常用操作：
- `void set(key, value, ttl)` — 带 TTL 写入
- `void set(key, value)` — 不带 TTL
- `String get(key)` — 读取
- `Boolean delete(key)` — 删除
- `Boolean exists(key)` — 存在判断
- `void expire(key, ttl)` — 给已有 key 设 TTL
- `Long getExpire(key)` — 查剩余 TTL

实现要点：
- 注入 `StringRedisTemplate`（Spring Boot 自动配置，无需自定义 RedisConfig）
- `@Service` 注解，所有业务模块可直接 `@RequiredArgsConstructor` 注入
- 字符串序列化够用，不引入自定义 Jackson 序列化器（黑名单场景只存"1"这种标识值）

### 2. 改 AuthService.logout()

文件：[AuthService.java](file:///e:/StarLinkAttendant/BackEnd/Backend/starlink-system/src/main/java/com/starlink/system/service/AuthService.java#L78-L80)

把空实现改为：
- 签名改为 `public void logout(String token)`
- 注入 `RedisService`
- 计算黑名单 key：`"jwt:blacklist:" + SHA256(token)`
- 算剩余 TTL：用 `JwtTokenUtil` 取 token 的过期时间，减去当前时间，得到秒数
  - 剩余 ≤ 0：直接 return（token 已过期，没必要写黑名单）
  - 剩余 > 0：调 `redisService.set(key, "1", Duration.ofSeconds(剩余秒数))`

需要给 `JwtTokenUtil` 加一个方法：`long getRemainingSeconds(String token)`，返回 token 剩余有效期秒数（基于 claims.getExpiration()）。

### 3. 改 AuthController.logout()

文件：[AuthController.java](file:///e:/StarLinkAttendant/BackEnd/Backend/starlink-system/src/main/java/com/starlink/system/controller/AuthController.java#L28-L32)

- 签名改为 `logout(HttpServletRequest request)`
- 从 `Authorization` 头取 token（与 JwtAuthenticationFilter.extractToken 相同逻辑）
- 取不到 token 或格式不对：直接返回 `Result.ok()`（已退出，无需报错）
- 调 `authService.logout(token)`

### 4. 改 JwtAuthenticationFilter.doFilterInternal()

文件：[JwtAuthenticationFilter.java](file:///e:/StarLinkAttendant/BackEnd/Backend/starlink-system/src/main/java/com/starlink/system/security/JwtAuthenticationFilter.java#L34-L89)

在 `parseToken` 成功（即 `userId`、`username` 取出来）后、写 SecurityContext 之前，插一段：
- 计算黑名单 key：`"jwt:blacklist:" + SHA256(token)`
- 调 `redisService.exists(key)`
- 在 → 不写 SecurityContext（token 已被主动失效），让请求作为匿名继续走（与无 token 时一致）
- 不在 → 继续走原有逻辑（写 SecurityContext）

注入 `RedisService`（加到 `@RequiredArgsConstructor` 的 final 字段列表）。

### 5. 加 JwtTokenUtil.getRemainingSeconds()

文件：[JwtTokenUtil.java](file:///e:/StarLinkAttendant/BackEnd/Backend/starlink-system/src/main/java/com/starlink/system/security/JwtTokenUtil.java#L50-L65)

新方法：
```java
public long getRemainingSeconds(String token) {
    Claims claims = parseToken(token);
    long remainingMs = claims.getExpiration().getTime() - System.currentTimeMillis();
    return remainingMs > 0 ? remainingMs / 1000 : 0;
}
```

放在 `isTokenExpired` 方法附近。

### 6. SHA256 工具

`AuthService.logout` 和 `JwtAuthenticationFilter` 都要算 token 的 SHA256。两种选择：
- 用 Hutool 的 `cn.hutool.crypto.SecureUtil.sha256(token)`（已引入 hutool-all）
- 用 JDK `MessageDigest.getInstance("SHA-256")`

推荐用 Hutool（项目已依赖，少写代码）。

## 关键文件清单

| 文件 | 操作 |
|------|------|
| `starlink-common/src/main/java/com/starlink/common/util/RedisService.java` | 新建 |
| `starlink-system/src/main/java/com/starlink/system/security/JwtTokenUtil.java` | 加 `getRemainingSeconds()` |
| `starlink-system/src/main/java/com/starlink/system/service/AuthService.java` | 改 `logout()` |
| `starlink-system/src/main/java/com/starlink/system/controller/AuthController.java` | 改 `logout()` |
| `starlink-system/src/main/java/com/starlink/system/security/JwtAuthenticationFilter.java` | 加黑名单查询 |

## 验证步骤

1. **mvn install -DskipTests** 整个项目编译通过
2. **启动后端**，看日志无 ERROR
3. **端到端测试**：
   - POST `/api/auth/login` 拿 accessToken
   - GET `/api/system/users` 带 `Authorization: Bearer <accessToken>` → 正常返回
   - POST `/api/auth/logout` 带 `Authorization: Bearer <accessToken>` → 返回成功
   - GET `/api/system/users` 再用同一个 accessToken → 应该 401（或匿名身份被拒）
   - WSL 里 `redis-cli` 执行 `keys "jwt:blacklist:*"` → 能看到黑名单 key
   - 等 access token 过期时间（开发测试可以临时把 `JwtProperties.accessTokenExpire` 调成 60 秒快速验证）后，再查 Redis → key 自动消失
4. **回归**：登录 → 立刻调登出之外的任意接口 → 应正常工作（确认没把所有请求都误拒）
