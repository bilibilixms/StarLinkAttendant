package com.intcaf.ai.tool;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * AI 工具定义注册表
 * 以 OpenAI Function Calling 格式定义工具，供 LLM 在对话中调用。
 * 注意：数据库 Schema 已通过系统提示词注入，无需 SQLCreate 工具。
 */
@Component
public class AiToolDefinitions {

    /**
     * 获取所有可用工具的定义
     */
    public List<Map<String, Object>> getToolDefinitions(boolean allowSql) {
        return allowSql ? List.of(sqlCallingDef(), knowledgeSearchDef()) : List.of(knowledgeSearchDef());
    }

    /**
     * SQLCalling — 执行 SQL 查询并返回结果
     * Schema 已在系统提示词中提供，LLM 可直接基于表结构生成 SQL。
     */
    private Map<String, Object> sqlCallingDef() {
        return Map.of(
            "type", "function",
            "function", Map.of(
                "name", "SQLCalling",
                "description", "Run one SELECT query over the approved reporting tables in the provided schema. " +
                    "Results are limited to 100 rows. Subqueries, UNION, file functions and other tables are unavailable.",
                "parameters", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "sql", Map.of(
                            "type", "string",
                            "description", "One MySQL SELECT query over the approved reporting tables. " +
                                "Use explicit JOIN and WHERE deleted_at IS NULL where applicable."
                        )
                    ),
                    "required", List.of("sql")
                )
            )
        );
    }

    /**
     * KnowledgeSearch — 检索业务知识库（RAG）
     * 当用户问业务规则、操作流程、计费逻辑等非数据库查询类问题时使用。
     */
    private Map<String, Object> knowledgeSearchDef() {
        return Map.of(
            "type", "function",
            "function", Map.of(
                "name", "KnowledgeSearch",
                "description", "检索星络灵侍馆（网咖/电竞馆）的业务知识库（RAG）。" +
                    "当用户询问业务规则、操作流程、计费逻辑、会员制度、商品管理等非数据库查询类问题时调用。" +
                    "数据库表结构类问题请用 SQLCalling，业务规则/操作流程类问题请用本工具。",
                "parameters", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "query", Map.of(
                            "type", "string",
                            "description", "检索关键词或问题，例如：上机流程、计费规则、会员等级、退款政策"
                        )
                    ),
                    "required", List.of("query")
                )
            )
        );
    }
}
