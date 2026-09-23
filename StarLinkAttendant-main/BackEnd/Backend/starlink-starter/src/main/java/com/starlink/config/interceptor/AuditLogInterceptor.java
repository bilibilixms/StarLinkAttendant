package com.starlink.config.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.starlink.system.security.SecurityUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 审计日志拦截器 — 自动拦截所有数据库写操作（INSERT / UPDATE / DELETE），
 * 并将操作记录写入 audit_log 表。SELECT 查询不会被记录。
 *
 */
@Slf4j
@Component
@Intercepts({
        @Signature(type = Executor.class, method = "update",
                args = {MappedStatement.class, Object.class})
})
public class AuditLogInterceptor implements Interceptor {

    private final DataSource dataSource;

    /** 不记录日志的表 */
    private static final Set<String> SKIP_TABLES = Set.of(
            "audit_log"
    );

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    /** JSON 序列化时需要忽略的敏感字段 */
    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "password", "passwordHash", "password_hash", "salt",
            "token", "secret", "credential"
    );

    /** 预编译的正则表达式，用于从 SQL 中提取表名 */
    private static final Pattern INSERT_PATTERN = Pattern.compile("(?i)INSERT\\s+INTO\\s+(\\w+)");
    private static final Pattern UPDATE_PATTERN = Pattern.compile("(?i)UPDATE\\s+(\\w+)");
    private static final Pattern DELETE_PATTERN = Pattern.compile("(?i)DELETE\\s+FROM\\s+(\\w+)");

    public AuditLogInterceptor(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /* ======================== 拦截配置 ======================== */

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object result = invocation.proceed();
        try {
            recordAuditLog(invocation);
        } catch (Exception e) {
            log.warn("审计日志记录失败: {}", e.getMessage());
        }
        return result;
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    /* ======================== 核心逻辑 ======================== */

    private void recordAuditLog(Invocation invocation) {
        Object[] args = invocation.getArgs();
        MappedStatement ms = (MappedStatement) args[0];
        SqlCommandType commandType = ms.getSqlCommandType();

        // 仅处理写操作
        if (commandType != SqlCommandType.INSERT
                && commandType != SqlCommandType.UPDATE
                && commandType != SqlCommandType.DELETE) {
            return;
        }

        Object parameterObject = args.length > 1 ? args[1] : null;
        String sql = ms.getBoundSql(parameterObject).getSql();
        String tableName = extractTableName(sql, commandType);

        if (tableName == null || SKIP_TABLES.contains(tableName.toLowerCase())) {
            return;
        }

        // 操作类型
        String action = switch (commandType) {
            case INSERT -> "create";
            case UPDATE -> "update";
            case DELETE -> "delete";
            default -> commandType.name().toLowerCase();
        };

        // 业务 ID
        Long bizId = extractBizId(parameterObject, commandType);

        // 详情 JSON
        String detail = buildDetail(parameterObject, commandType);

        // 操作人
        String[] operator = extractOperator();
        Long operatorId = operator[0] != null ? Long.parseLong(operator[0]) : null;
        String operatorName = operator[1];

        // IP
        String ip = extractIp();

        // 写入 audit_log（使用独立连接，避免干扰主事务）
        insertAuditLog(operatorId, operatorName, tableName, bizId, action, detail, ip);
    }

    /* ======================== 信息提取 ======================== */

    /**
     * 从 Spring Security 上下文获取当前操作人。
     */
    private String[] extractOperator() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof SecurityUser su) {
                return new String[]{
                        String.valueOf(su.getUserId()),
                        su.getEmployee().getRealName()
                };
            }
        } catch (Exception ignored) {
        }
        return new String[]{null, null};
    }

    /**
     * 从当前 HTTP 请求中提取客户端 IP。
     */
    private String extractIp() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return null;
            HttpServletRequest request = attrs.getRequest();
            String ip = request.getHeader("X-Forwarded-For");
            if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("X-Real-IP");
            }
            if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getRemoteAddr();
            }
            // X-Forwarded-For 可能包含多个 IP，取第一个
            if (ip != null && ip.contains(",")) {
                ip = ip.split(",")[0].trim();
            }
            return ip.length() > 15 ? ip.substring(0, 15) : ip;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从 SQL 语句中提取表名。
     */
    private String extractTableName(String sql, SqlCommandType commandType) {
        if (sql == null) return null;
        String normalized = sql.replaceAll("`", "").trim();
        Pattern pattern = switch (commandType) {
            case INSERT -> INSERT_PATTERN;
            case UPDATE -> UPDATE_PATTERN;
            case DELETE -> DELETE_PATTERN;
            default -> null;
        };
        if (pattern == null) return null;
        Matcher matcher = pattern.matcher(normalized);
        return matcher.find() ? matcher.group(1).toLowerCase() : null;
    }

    /**
     * 从参数对象中提取业务 ID。
     */
    private Long extractBizId(Object param, SqlCommandType commandType) {
        if (param == null) return null;

        // 直接是 Long（如 deleteById）
        if (param instanceof Long l) return l;
        if (param instanceof Number n) return n.longValue();

        try {
            // MyBatis-Plus 的 Wrapper 参数通常是 Map
            if (param instanceof Map<?, ?> map) {
                // updateById / deleteById 时 map 中可能有 "et"（entity）或 "id"
                Object et = map.get("et");
                if (et != null) {
                    Long id = getIdByReflection(et);
                    if (id != null) return id;
                }
                Object id = map.get("id");
                if (id instanceof Long l) return l;
                if (id instanceof Number n) return n.longValue();
                // collection / list 参数（批量操作）不提取 ID
                return null;
            }

            // 实体对象
            return getIdByReflection(param);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 通过反射获取对象的 id 字段值。
     */
    private Long getIdByReflection(Object obj) {
        if (obj == null) return null;
        try {
            // 先尝试 getter
            Method getId = obj.getClass().getMethod("getId");
            Object val = getId.invoke(obj);
            if (val instanceof Long l) return l;
            if (val instanceof Number n) return n.longValue();
        } catch (Exception ignored) {
        }
        try {
            // 再尝试字段
            Field field = findField(obj.getClass(), "id");
            if (field != null) {
                field.setAccessible(true);
                Object val = field.get(obj);
                if (val instanceof Long l) return l;
                if (val instanceof Number n) return n.longValue();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * 构建操作详情 JSON。
     * 仅序列化实际业务实体对象，跳过 MyBatis-Plus Wrapper 等框架内部对象。
     */
    private String buildDetail(Object param, SqlCommandType commandType) {
        if (param == null) return null;
        try {
            Object toSerialize = null;

            if (param instanceof Map<?, ?> map) {
                // MyBatis-Plus 的 Map 参数中，"et" 是实际实体对象
                Object et = map.get("et");
                if (et != null && !isWrapperObject(et)) {
                    toSerialize = et;
                } else {
                    // 纯 Wrapper 查询（无实体），尝试从 map 中查找其他业务实体
                    toSerialize = findEntityInMap(map);
                }
            } else if (!isWrapperObject(param)) {
                toSerialize = param;
            }

            if (toSerialize == null) return null;

            // 基本类型不序列化
            if (toSerialize instanceof Number || toSerialize instanceof String) {
                return "{\"value\": " + MAPPER.writeValueAsString(toSerialize) + "}";
            }

            // 转 Map 并过滤敏感字段和 null 值
            @SuppressWarnings("unchecked")
            Map<String, Object> raw = MAPPER.convertValue(toSerialize, Map.class);
            Map<String, Object> filtered = new LinkedHashMap<>();
            raw.forEach((k, v) -> {
                if (!SENSITIVE_FIELDS.contains(k) && v != null) {
                    filtered.put(k, v);
                }
            });

            if (filtered.isEmpty()) return null;

            String json = MAPPER.writeValueAsString(filtered);
            // 限制长度，防止 detail 字段溢出
            return json.length() > 4000 ? json.substring(0, 4000) : json;
        } catch (Exception e) {
            log.debug("detail 序列化异常: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 判断对象是否为 MyBatis-Plus Wrapper 或框架内部对象。
     */
    private boolean isWrapperObject(Object obj) {
        if (obj == null) return false;
        String className = obj.getClass().getName();
        return className.startsWith("com.baomidou.mybatisplus")
                || className.contains("Wrapper")
                || className.contains("Segment")
                || className.contains("Expression");
    }

    /**
     * 从 MyBatis-Plus 参数 Map 中查找业务实体对象。
     * 跳过 Wrapper、Collection 等框架内部对象。
     */
    private Object findEntityInMap(Map<?, ?> map) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object val = entry.getValue();
            if (val == null) continue;
            // 跳过 Wrapper、集合、基本类型
            if (isWrapperObject(val)) continue;
            if (val instanceof Collection || val instanceof Map) continue;
            if (val instanceof Number || val instanceof String) continue;
            // 检查是否为业务实体（包含 id 字段的 POJO）
            try {
                val.getClass().getMethod("getId");
                return val;
            } catch (NoSuchMethodException ignored) {}
        }
        return null;
    }

    /* ======================== 日志写入 ======================== */

    /**
     * 使用独立数据库连接写入审计日志，避免干扰主事务。
     */
    private void insertAuditLog(Long operatorId, String operatorName,
                                String bizType, Long bizId,
                                String action, String detail, String ipAddress) {
        String sql = "INSERT INTO audit_log (operator_id, operator_name, biz_type, biz_id, " +
                "action, detail, ip_address, created_at, updated_at, version) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            if (operatorId != null) {
                ps.setLong(1, operatorId);
            } else {
                ps.setNull(1, java.sql.Types.BIGINT);
            }
            ps.setString(2, operatorName);
            ps.setString(3, bizType);
            if (bizId != null) {
                ps.setLong(4, bizId);
            } else {
                ps.setNull(4, java.sql.Types.BIGINT);
            }
            ps.setString(5, action);
            ps.setString(6, detail);
            ps.setString(7, ipAddress);
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            ps.setTimestamp(8, now);
            ps.setTimestamp(9, now);
            ps.executeUpdate();
        } catch (Exception e) {
            log.warn("审计日志写入失败 [bizType={}, action={}]: {}", bizType, action, e.getMessage());
        }
    }

    /* ======================== 工具方法 ======================== */

    private Field findField(Class<?> clazz, String name) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
