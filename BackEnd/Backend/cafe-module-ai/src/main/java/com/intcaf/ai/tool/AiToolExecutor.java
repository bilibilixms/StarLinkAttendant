package com.intcaf.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intcaf.rag.vector.RagVectorService;
import com.intcaf.ai.service.AiIdentity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * AI 工具执行器
 * 负责执行 LLM 请求的工具调用（SQLCalling），并返回结果。
 * 数据库 Schema 通过 getSchemaForPrompt() 提供给系统提示词注入。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiToolExecutor {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RagVectorService ragVectorService;

    /** 最大返回行数 */
    private static final int MAX_ROWS = 100;

    /** SQL 执行超时（秒） */
    private static final int SQL_TIMEOUT_SECONDS = 10;
    private static final Set<String> QUERY_TABLES = Set.of(
            "seat_area", "tariff_plan", "computer", "session", "session_timing",
            "tariff_rate", "billing_record", "product_category", "product",
            "product_combo", "product_combo_item", "inventory", "orders", "order_item",
            "payment_record", "refund_record", "cashier_shift", "daily_settlement", "campaign");
    private static final Pattern TABLE_REFERENCE = Pattern.compile("\\b(?:FROM|JOIN)\\s+([a-z_][a-z_0-9]*)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DANGEROUS_TOKEN = Pattern.compile(
            "\\b(?:INTO|OUTFILE|DUMPFILE|LOAD_FILE|SLEEP|BENCHMARK|GET_LOCK|RELEASE_LOCK|" +
            "UNION|PROCEDURE|INFORMATION_SCHEMA|PERFORMANCE_SCHEMA|MYSQL|SYS|FOR\\s+UPDATE|LOCK\\s+IN)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FUNCTION_CALL = Pattern.compile("\\b([a-z_][a-z_0-9]*)\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Set<String> QUERY_FUNCTIONS = Set.of(
            "count", "sum", "avg", "min", "max", "date", "date_format", "hour",
            "day", "month", "year", "ifnull", "coalesce", "round", "cast", "in");

    /** 缓存的数据库 Schema，避免每轮重复查询 */
    private volatile String cachedSchema = null;
    private volatile long schemaCacheTime = 0;
    /** Schema 缓存有效期：5 分钟 */
    private static final long SCHEMA_CACHE_TTL_MS = 5 * 60 * 1000;

    /**
     * 执行工具调用
     *
     * @param toolName 工具名称
     * @param argumentsJson 工具参数 JSON 字符串
     * @return 工具执行结果（文本形式）
     */
    public String executeTool(String toolName, String argumentsJson) {
        if (!AiIdentity.current().canQueryData()) {
            return "Error: AI tools are not available for this account.";
        }
        log.debug("Executing tool: {} with args: {}", toolName, argumentsJson);
        try {
            JsonNode args = objectMapper.readTree(argumentsJson);
            return switch (toolName) {
                case "SQLCalling" -> executeSqlCalling(args);
                case "KnowledgeSearch" -> executeKnowledgeSearch(args);
                default -> "Error: Unknown tool '" + toolName + "'";
            };
        } catch (Exception e) {
            log.error("Tool execution error [{}]: {}", toolName, e.getMessage(), e);
            return "Error executing tool " + toolName + ": " + e.getMessage();
        }
    }

    // ========================= SQLCalling =========================

    /**
     * SQLCalling: 执行 SELECT 查询并返回结果
     */
    private String executeSqlCalling(JsonNode args) {
        String sql = args.has("sql") ? args.get("sql").asText() : "";
        if (sql.isBlank()) {
            return "Error: 'sql' parameter is required and cannot be empty.";
        }

        String validationError = validateSql(sql);
        if (validationError != null) return "Error: " + validationError;

        log.info("SQLCalling executing: {}", sql);

        try {
            List<Map<String, Object>> rows = jdbcTemplate.execute((ConnectionCallback<List<Map<String, Object>>>) connection -> {
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setMaxRows(MAX_ROWS);
                    statement.setQueryTimeout(SQL_TIMEOUT_SECONDS);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        List<Map<String, Object>> result = new ArrayList<>();
                        ResultSetMetaData metadata = resultSet.getMetaData();
                        while (resultSet.next()) {
                            Map<String, Object> row = new LinkedHashMap<>();
                            for (int column = 1; column <= metadata.getColumnCount(); column++) {
                                row.put(metadata.getColumnLabel(column), resultSet.getObject(column));
                            }
                            result.add(row);
                        }
                        return result;
                    }
                }
            });

            if (rows.isEmpty()) {
                return "Query executed successfully. Result: 0 rows (empty result set).";
            }

            // 格式化输出
            StringBuilder sb = new StringBuilder();
            sb.append("Query result (").append(rows.size()).append(" rows):\n\n");

            // 表头
            List<String> columns = new ArrayList<>(rows.get(0).keySet());
            sb.append("| ").append(String.join(" | ", columns)).append(" |\n");
            sb.append("| ").append(columns.stream().map(c -> "---").collect(Collectors.joining(" | "))).append(" |\n");

            // 数据行
            for (Map<String, Object> row : rows) {
                sb.append("| ");
                for (String col : columns) {
                    Object val = row.get(col);
                    sb.append(val == null ? "NULL" : val.toString());
                    sb.append(" | ");
                }
                sb.append("\n");
            }

            if (rows.size() >= MAX_ROWS) {
                sb.append("\n(Result truncated to ").append(MAX_ROWS).append(" rows)");
            }

            return sb.toString();
        } catch (Exception e) {
            log.error("SQL execution error: {}", e.getMessage());
            return "SQL execution error: " + e.getMessage();
        }
    }

    /** Only one query over explicitly approved reporting tables can reach the database. */
    static String validateSql(String sql) {
        String normalized = sql.trim();
        if (!normalized.matches("(?is)^SELECT\\s+.*")) return "Only SELECT queries are allowed.";
        if (normalized.length() > 4000 || normalized.contains(";") || normalized.contains("--")
                || normalized.contains("/*") || normalized.contains("#") || normalized.contains("@")
                || DANGEROUS_TOKEN.matcher(normalized).find()) {
            return "Unsupported SQL syntax.";
        }
        if (Pattern.compile("\\bSELECT\\b", Pattern.CASE_INSENSITIVE).matcher(normalized).results().count() != 1) {
            return "Subqueries are not allowed.";
        }
        Matcher function = FUNCTION_CALL.matcher(normalized);
        while (function.find()) {
            if (!QUERY_FUNCTIONS.contains(function.group(1).toLowerCase(Locale.ROOT))) {
                return "Function is not available for AI reporting.";
            }
        }
        Matcher matcher = TABLE_REFERENCE.matcher(normalized);
        boolean found = false;
        while (matcher.find()) {
            found = true;
            if (!QUERY_TABLES.contains(matcher.group(1).toLowerCase(Locale.ROOT))) {
                return "Table is not available for AI reporting.";
            }
        }
        if (!found || Pattern.compile("(?i)\\bFROM\\s+[a-z_][a-z_0-9]*(?:\\s+(?:AS\\s+)?[a-z_][a-z_0-9]*)?\\s*,")
                .matcher(normalized).find()) {
            return "Use one approved table or explicit JOIN clauses.";
        }
        return null;
    }

    // ========================= KnowledgeSearch =========================

    /**
     * KnowledgeSearch: 检索业务知识库（RAG），返回相关业务规则片段
     */
    private String executeKnowledgeSearch(JsonNode args) {
        String query = args.has("query") ? args.get("query").asText() : "";
        if (query.isBlank()) {
            return "Error: 'query' parameter is required.";
        }
        log.info("KnowledgeSearch query: {}", query);
        try {
            String context = ragVectorService.searchAsPromptContext(query, 3);
            if (context.isBlank()) {
                return "知识库中未找到与「" + query + "」相关的内容。";
            }
            return context;
        } catch (Exception e) {
            log.error("KnowledgeSearch error: {}", e.getMessage());
            return "知识库检索失败: " + e.getMessage();
        }
    }

    // ========================= Schema 预加载（供系统提示词注入） =========================

    /**
     * 获取数据库 Schema 摘要，用于注入系统提示词。
     * 带缓存，TTL 5 分钟自动刷新。
     */
    public String getSchemaForPrompt() {
        try {
            return getDatabaseSchema();
        } catch (Exception e) {
            log.warn("Failed to load schema for prompt: {}", e.getMessage());
            return "（Schema 加载失败）";
        }
    }

    /**
     * 查询数据库所有业务表的 Schema（表名、列名、类型、注释）
     */
    private String getDatabaseSchema() {
        // 检查缓存是否有效
        if (cachedSchema != null && (System.currentTimeMillis() - schemaCacheTime) < SCHEMA_CACHE_TTL_MS) {
            log.debug("Using cached database schema ({}ms old)", System.currentTimeMillis() - schemaCacheTime);
            return cachedSchema;
        }
        log.info("Refreshing database schema cache...");

        // 获取所有表
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE() ORDER BY TABLE_NAME",
                String.class
        );

        StringBuilder sb = new StringBuilder();
        for (String table : tables) {
            if (!QUERY_TABLES.contains(table.toLowerCase(Locale.ROOT))) continue;
            // 获取表的列信息
            List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                    "SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_KEY, COLUMN_COMMENT " +
                    "FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? " +
                    "ORDER BY ORDINAL_POSITION",
                    table
            );

            // 表注释（容错：某些 MySQL 版本 TABLE_COMMENT 可能为 NULL 或不存在）
            String tableComment = null;
            try {
                List<Map<String, Object>> commentRows = jdbcTemplate.queryForList(
                        "SELECT TABLE_COMMENT FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                        table
                );
                if (!commentRows.isEmpty()) {
                    Object c = commentRows.get(0).get("TABLE_COMMENT");
                    if (c != null) tableComment = c.toString();
                }
            } catch (Exception e) {
                log.debug("Table comment not available for '{}': {}", table, e.getMessage());
            }

            sb.append("### ").append(table);
            if (tableComment != null && !tableComment.isBlank()) {
                sb.append(" -- ").append(tableComment);
            }
            sb.append("\n");

            for (Map<String, Object> col : columns) {
                String colName = (String) col.get("COLUMN_NAME");
                String colType = (String) col.get("COLUMN_TYPE");
                String nullable = (String) col.get("IS_NULLABLE");
                String key = (String) col.get("COLUMN_KEY");
                String comment = (String) col.get("COLUMN_COMMENT");

                sb.append("  - ").append(colName).append(": ").append(colType);
                if ("PRI".equals(key)) sb.append(" [PK]");
                if ("MUL".equals(key)) sb.append(" [FK]");
                if ("NO".equals(nullable)) sb.append(" NOT NULL");
                if (comment != null && !comment.isBlank()) sb.append("  -- ").append(comment);
                sb.append("\n");
            }
            sb.append("\n");
        }
        cachedSchema = sb.toString();
        schemaCacheTime = System.currentTimeMillis();
        return cachedSchema;
    }
}
