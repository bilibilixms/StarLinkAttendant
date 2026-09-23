package com.intcaf.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * AI 会话持久化服务
 * ----------------------------------------------------------
 * 将用户端 / 管理端与 AI 的对话存入数据库（ai_chat_session / ai_chat_message），
 * 支持：创建会话、会话列表、会话详情（含消息）、追加消息、逻辑删除。
 * 遵循项目逻辑删除规范（deleted_at 非空 = 已删）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiSessionService {

    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final JdbcTemplate jdbcTemplate;

    /** 生成对外会话号：S + 时间戳 + 4 位随机（唯一约束 uk_session_no） */
    private String genSessionNo() {
        String ts = LocalDateTime.now().format(NO_FMT);
        int rnd = ThreadLocalRandom.current().nextInt(1000, 10000);
        return "S" + ts + rnd;
    }

    /**
     * 创建会话
     *
     * @param userType user=用户端 / admin=管理端
     * @param userId   归属用户ID（会员ID或管理员ID，未登录可为 null）
     * @param userName 展示名（昵称/工号，可空）
     * @param title    会话标题（空则默认"新对话"）
     */
    public Map<String, Object> createSession(String userType, Long userId, String userName, String title) {
        String sessionNo = genSessionNo();
        String finalTitle = (title == null || title.isBlank()) ? "新对话" : title.trim();
        jdbcTemplate.update(
                "INSERT INTO ai_chat_session (session_no, user_type, user_id, user_name, title) VALUES (?, ?, ?, ?, ?)",
                sessionNo,
                userType == null || userType.isBlank() ? "user" : userType,
                userId,
                userName,
                finalTitle
        );
        return getSessionByNo(sessionNo);
    }

    /** 会话列表（不含消息，按更新时间倒序） */
    public List<Map<String, Object>> listSessions(String userType, Long userId) {
        String ut = userType == null || userType.isBlank() ? "user" : userType;
        if (userId == null) {
            return jdbcTemplate.queryForList(
                    "SELECT id, session_no AS sessionNo, user_type AS userType, user_id AS userId, user_name AS userName, " +
                            "title, message_count AS messageCount, created_at AS createTime, updated_at AS updateTime " +
                            "FROM ai_chat_session WHERE user_type = ? AND user_id IS NULL AND deleted_at IS NULL " +
                            "ORDER BY updated_at DESC, id DESC",
                    ut
            );
        }
        return jdbcTemplate.queryForList(
                "SELECT id, session_no AS sessionNo, user_type AS userType, user_id AS userId, user_name AS userName, " +
                        "title, message_count AS messageCount, created_at AS createTime, updated_at AS updateTime " +
                        "FROM ai_chat_session WHERE user_type = ? AND user_id = ? AND deleted_at IS NULL " +
                        "ORDER BY updated_at DESC, id DESC",
                ut, userId
        );
    }

    /** 会话详情（含全部消息） */
    public Map<String, Object> getSessionDetail(String sessionNo) {
        Map<String, Object> session = getSessionByNo(sessionNo);
        if (session == null) return null;
        List<Map<String, Object>> messages = jdbcTemplate.queryForList(
                "SELECT id, role, content, thinking_json AS thinkingJson, cost_ms AS costMs, model, created_at AS createTime " +
                        "FROM ai_chat_message WHERE session_id = ? AND deleted_at IS NULL ORDER BY id ASC",
                session.get("id")
        );
        session.put("messages", messages);
        return session;
    }

    /**
     * 追加消息；若该会话标题仍为"新对话"且首条是用户提问，自动以提问前 20 字命名
     */
    public void addMessage(String sessionNo, String role, String content,
                           String thinkingJson, Integer costMs, String model) {
        Map<String, Object> session = getSessionByNo(sessionNo);
        if (session == null) {
            throw new IllegalArgumentException("会话不存在: " + sessionNo);
        }
        Object sessionId = session.get("id");
        String r = role == null || role.isBlank() ? "user" : role;
        String text = content == null ? "" : content;

        jdbcTemplate.update(
                "INSERT INTO ai_chat_message (session_id, role, content, thinking_json, cost_ms, model) VALUES (?, ?, ?, ?, ?, ?)",
                sessionId, r, text, thinkingJson, costMs, model
        );
        jdbcTemplate.update(
                "UPDATE ai_chat_session SET message_count = message_count + 1, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                sessionId
        );

        // 首条用户提问 → 自动命名会话
        Object title = session.get("title");
        if ("user".equals(r) && ("新对话".equals(title) || title == null)) {
            String t = text.trim();
            if (!t.isEmpty()) {
                String newTitle = t.length() > 20 ? t.substring(0, 20) : t;
                jdbcTemplate.update(
                        "UPDATE ai_chat_session SET title = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                        newTitle, sessionId
                );
            }
        }
    }

    /** 逻辑删除会话（消息一并逻辑删除） */
    public void deleteSession(String sessionNo) {
        Map<String, Object> session = getSessionByNo(sessionNo);
        if (session == null) return;
        Object sessionId = session.get("id");
        jdbcTemplate.update("UPDATE ai_chat_message SET deleted_at = CURRENT_TIMESTAMP WHERE session_id = ? AND deleted_at IS NULL", sessionId);
        jdbcTemplate.update("UPDATE ai_chat_session SET deleted_at = CURRENT_TIMESTAMP WHERE id = ? AND deleted_at IS NULL", sessionId);
    }

    /** 按会话号查会话（未删除） */
    private Map<String, Object> getSessionByNo(String sessionNo) {
        List<Map<String, Object>> list = jdbcTemplate.queryForList(
                "SELECT id, session_no AS sessionNo, user_type AS userType, user_id AS userId, user_name AS userName, " +
                        "title, message_count AS messageCount, created_at AS createTime, updated_at AS updateTime " +
                        "FROM ai_chat_session WHERE session_no = ? AND deleted_at IS NULL LIMIT 1",
                sessionNo
        );
        return list.isEmpty() ? null : list.get(0);
    }
}
