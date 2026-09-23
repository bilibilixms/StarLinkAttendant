package com.intcaf.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intcaf.rag.vector.RagVectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
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

        // 安全检查：仅允许 SELECT
        String trimmed = sql.trim().toUpperCase();
        if (!trimmed.startsWith("SELECT")) {
            return "Error: Only SELECT statements are allowed. Received: " + sql.trim().split("\\s+")[0];
        }

        // 安全检查：禁止危险关键词
        for (String forbidden : List.of("DROP", "INSERT", "UPDATE", "DELETE", "ALTER", "TRUNCATE", "CREATE", "GRANT", "REVOKE")) {
            if (trimmed.contains(" " + forbidden + " ") || trimmed.startsWith(forbidden + " ")) {
                return "Error: Forbidden keyword '" + forbidden + "' detected. Only read-only SELECT queries are allowed.";
            }
        }

        // 强制 LIMIT
        if (!trimmed.contains("LIMIT")) {
            sql = sql.replaceAll(";\\s*$", "") + " LIMIT " + MAX_ROWS;
        }

        log.info("SQLCalling executing: {}", sql);

        try {
            jdbcTemplate.setQueryTimeout(SQL_TIMEOUT_SECONDS);
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

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
        jdbcTemplate.setQueryTimeout(SQL_TIMEOUT_SECONDS);

        // 获取所有表
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE() ORDER BY TABLE_NAME",
                String.class
        );

        StringBuilder sb = new StringBuilder();
        for (String table : tables) {
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
