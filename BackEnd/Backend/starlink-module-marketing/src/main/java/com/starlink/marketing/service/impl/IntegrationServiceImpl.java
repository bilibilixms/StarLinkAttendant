package com.starlink.marketing.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.starlink.marketing.mapper.IntegrationMapper;
import com.starlink.marketing.mapper.SmsNotificationMapper;
import com.starlink.marketing.service.IntegrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 公安审计服务实现 — Demo 模拟模式。
 * <p>
 * 全部基于现有 40 张表，无新建表。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntegrationServiceImpl implements IntegrationService {

    private final IntegrationMapper integrationMapper;
    private final SmsNotificationMapper smsNotificationMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== 配置管理（system_config） ====================

    @Override
    public List<Map<String, Object>> getConfigs() {
        List<Map<String, Object>> all = integrationMapper.selectIntegrationConfigs();
        List<Map<String, Object>> parsed = new ArrayList<>();
        for (Map<String, Object> row : all) {
            parsed.add(flattenConfig(row));
        }
        return parsed;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> saveConfig(Map<String, Object> config) {
        int integrationType = toInt(config.get("integrationType"));
        String key = "integration." + integrationType;
        // 检查是否已存在同类型配置
        Map<String, Object> existing = integrationMapper.selectConfigByKey(key);
        String value = toJson(config);
        String description = "集成类型：" + integrationType;
        if (existing != null) {
            integrationMapper.updateIntegrationConfig(key, value, description);
            log.info("更新集成配置，类型: {}", integrationType);
        } else {
            integrationMapper.insertIntegrationConfig(key, value, description);
            log.info("新增集成配置，类型: {}", integrationType);
        }
        Map<String, Object> saved = integrationMapper.selectConfigByKey(key);
        return flattenConfig(saved);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateConfig(Long id, Map<String, Object> config) {
        Map<String, Object> existing = integrationMapper.selectConfigById(id);
        if (existing == null) return null;
        String value = toJson(config);
        String description = (String) config.getOrDefault("description", "");
        integrationMapper.updateIntegrationConfigById(id, value, description);
        log.info("更新集成配置，ID: {}", id);
        Map<String, Object> updated = integrationMapper.selectConfigById(id);
        return flattenConfig(updated);
    }

    @Override
    public Map<String, Object> testIntegration(Long id) {
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> config = integrationMapper.selectConfigById(id);
        if (config == null) {
            result.put("success", false);
            result.put("message", "配置不存在");
            return result;
        }
        // Demo: 始终返回成功，更新 lastSyncTime
        Map<String, Object> parsed = parseJson((String) config.get("config_value"));
        if (parsed == null) parsed = new LinkedHashMap<>();
        parsed.put("lastSyncTime", LocalDateTime.now().toString());
        parsed.put("errorCount", 0);
        integrationMapper.updateConfigValueById(id, toJson(parsed));

        result.put("success", true);
        result.put("message", "连接测试成功（Demo 模拟）");
        result.put("configName", parsed.getOrDefault("configName", ""));
        return result;
    }

    // ==================== INT-01 实名认证（member + audit_log） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> verifyIdentity(String realName, String idCard, Long memberId) {
        boolean valid = realName != null && !realName.isBlank()
                && idCard != null && idCard.length() >= 15;

        String errorMsg = null;
        if (valid) {
            Map<String, Object> member = integrationMapper.selectMemberByNameAndIdCard(realName, idCard);
            if (member == null) {
                valid = false;
                errorMsg = "未找到匹配的会员信息";
            }
        } else {
            errorMsg = "身份信息格式不正确";
        }

        // 构造 detail JSON
        Map<String, Object> detailMap = new LinkedHashMap<>();
        detailMap.put("realName", realName);
        detailMap.put("idCard", idCard);
        detailMap.put("memberId", memberId);
        detailMap.put("verificationStatus", valid ? 1 : 2);
        detailMap.put("errorMessage", errorMsg);
        detailMap.put("status", valid ? 1 : 0);

        // 写 audit_log
        integrationMapper.insertAuditLog("identity_verify", memberId, "verify", toJson(detailMap));

        Map<String, Object> result = new LinkedHashMap<>(detailMap);
        result.put("success", valid);
        result.put("message", valid ? "验证通过" : errorMsg);

        log.info("实名认证模拟完成，姓名: {}, 结果: {}", realName, valid ? "通过" : "不通过");
        return result;
    }

    @Override
    public Map<String, Object> getIdVerificationLogs(int page, int size, Integer verificationStatus) {
        long total = integrationMapper.countAuditLogs("identity_verify", verificationStatus);
        int offset = (page - 1) * size;
        List<Map<String, Object>> records = integrationMapper.selectAuditLogs("identity_verify", verificationStatus, offset, size);
        List<Map<String, Object>> parsed = new ArrayList<>();
        for (Map<String, Object> row : records) {
            parsed.add(flattenAuditLog(row));
        }
        return buildPageResult(parsed, total, page, size);
    }

    // ==================== INT-02 短信通道（notification） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> sendSms(String phone, String title, String content) {
        Map<String, Object> result = new HashMap<>();

        int recentCount = smsNotificationMapper.countRecentSms(phone);
        if (recentCount > 0) {
            result.put("success", false);
            result.put("message", "发送过于频繁，请60秒后再试");
            // 记录失败日志
            Map<String, Object> failDetail = new LinkedHashMap<>();
            failDetail.put("phone", phone);
            failDetail.put("title", title);
            failDetail.put("status", 0);
            failDetail.put("errorMessage", "发送过于频繁");
            integrationMapper.insertAuditLog("sms_send", null, "send", toJson(failDetail));
            return result;
        }

        // 记录到 notification 表
        String targetIds = "[\"" + phone + "\"]";
        String expiredAt = LocalDateTime.now().plusMinutes(5).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        smsNotificationMapper.insertSmsNotification(title, content, targetIds, expiredAt);

        // 记录到 audit_log
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("phone", phone);
        detail.put("title", title);
        detail.put("status", 1);
        integrationMapper.insertAuditLog("sms_send", null, "send", toJson(detail));

        result.put("success", true);
        result.put("message", "短信发送成功（Demo 模拟）");
        result.put("phone", phone);
        log.info("模拟短信发送，手机号: {}, 标题: {}", phone, title);
        return result;
    }

    @Override
    public Map<String, Object> getSmsLogs(int page, int size, Integer status, String phone) {
        long total = smsNotificationMapper.countSmsLogs(phone);
        int offset = (page - 1) * size;
        List<Map<String, Object>> records = smsNotificationMapper.selectSmsLogs(phone, offset, size);
        return buildPageResult(records, total, page, size);
    }

    // ==================== INT-03 公安审计（session + audit_log） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int generateAuditData() {
        List<Map<String, Object>> sessions = integrationMapper.selectPendingAuditSessions(50);
        int inserted = 0;
        for (Map<String, Object> row : sessions) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("memberId", row.get("member_id"));
            detail.put("computerId", row.get("computer_id"));
            detail.put("loginTime", toString(row.get("login_time")));
            detail.put("logoutTime", toString(row.get("logout_time")));
            detail.put("status", row.get("status"));
            detail.put("uploaded", 0);
            detail.put("uploadTime", null);

            Long sessionId = toLong(row.get("session_id"));
            integrationMapper.insertAuditLog("police_audit", sessionId, "generate", toJson(detail));
            inserted++;
        }
        log.info("生成公安审计数据，从 {} 条 session 中抽取了 {} 条记录", sessions.size(), inserted);
        return inserted;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int uploadAuditData() {
        long pending = integrationMapper.countPendingUpload();
        if (pending > 0) {
            integrationMapper.batchMarkAuditUploaded();
        }
        log.info("模拟上报公安审计数据，上报 {} 条记录", pending);
        return (int) pending;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> addAuditLog(Map<String, Object> audit) {
        Long sessionId = toLong(audit.get("sessionId"));
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("memberId", audit.get("memberId"));
        detail.put("computerId", audit.get("computerId"));
        detail.put("loginTime", audit.get("loginTime"));
        detail.put("logoutTime", audit.get("logoutTime"));
        detail.put("status", audit.getOrDefault("status", 0));
        detail.put("uploaded", 0);
        detail.put("uploadTime", null);

        integrationMapper.insertAuditLog("police_audit", sessionId, "add", toJson(detail));
        log.info("新增公安审计记录，会话ID: {}", sessionId);
        return audit;
    }

    @Override
    public Map<String, Object> getAuditLogs(int page, int size, Integer uploaded) {
        long total = integrationMapper.countPoliceAuditLogs(uploaded);
        int offset = (page - 1) * size;
        List<Map<String, Object>> records = integrationMapper.selectPoliceAuditLogs(uploaded, offset, size);
        List<Map<String, Object>> parsed = new ArrayList<>();
        for (Map<String, Object> row : records) {
            parsed.add(flattenAuditLog(row));
        }
        return buildPageResult(parsed, total, page, size);
    }

    @Override
    public List<Map<String, Object>> getPendingAuditSessions() {
        return integrationMapper.selectPendingAuditSessions(50);
    }

    // ==================== 统一日志 ====================

    @Override
    public Map<String, Object> getIntegrationLogs(int page, int size, String bizType, Integer status) {
        long total = integrationMapper.countUnifiedLogs(bizType, status);
        int offset = (page - 1) * size;
        List<Map<String, Object>> records = integrationMapper.selectUnifiedLogs(bizType, status, offset, size);
        List<Map<String, Object>> parsed = new ArrayList<>();
        for (Map<String, Object> row : records) {
            Map<String, Object> flat = flattenAuditLog(row);
            Map<String, Object> log = new LinkedHashMap<>();
            log.put("id", flat.get("id"));
            log.put("integrationType", bizTypeToInt((String) flat.get("bizType")));
            log.put("requestData", flat.get("detail"));
            log.put("responseData", "");
            log.put("status", flat.getOrDefault("status", 0));
            log.put("errorMessage", flat.get("errorMessage"));
            log.put("createdAt", flat.get("createdAt"));
            parsed.add(log);
        }
        return buildPageResult(parsed, total, page, size);
    }

    // ==================== 内部工具方法 ====================

    /** 构造分页结果 Map */
    private Map<String, Object> buildPageResult(List<Map<String, Object>> records, long total, int page, int size) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        result.put("pages", size > 0 ? (int) Math.ceil((double) total / size) : 0);
        return result;
    }

    /** 展开 system_config 行为前端格式 */
    private Map<String, Object> flattenConfig(Map<String, Object> row) {
        Map<String, Object> flat = new LinkedHashMap<>();
        flat.put("id", row.get("id"));
        Map<String, Object> value = parseJson((String) row.get("config_value"));
        if (value != null) flat.putAll(value);
        flat.putIfAbsent("configName", row.get("description"));
        flat.put("createdAt", row.get("created_at"));
        flat.put("updatedAt", row.get("updated_at"));
        return flat;
    }

    /** 展开 audit_log 行的 detail JSON */
    private Map<String, Object> flattenAuditLog(Map<String, Object> row) {
        Map<String, Object> flat = new LinkedHashMap<>();
        flat.put("id", row.get("id"));
        flat.put("bizType", row.get("bizType"));
        flat.put("bizId", row.get("bizId"));
        flat.put("action", row.get("action"));
        flat.put("createdAt", row.get("createdAt"));
        Object detailObj = row.get("detail");
        if (detailObj instanceof String) {
            Map<String, Object> detail = parseJson((String) detailObj);
            if (detail != null) flat.putAll(detail);
        }
        return flat;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("JSON序列化失败: {}", e.getMessage());
            return "{}";
        }
    }

    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("JSON解析失败: {}", e.getMessage());
            return null;
        }
    }

    private int bizTypeToInt(String bizType) {
        if (bizType == null) return 0;
        return switch (bizType) {
            case "identity_verify" -> 1;
            case "sms_send" -> 2;
            case "police_audit" -> 3;
            default -> 0;
        };
    }

    private Long toLong(Object val) {
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).longValue();
        return Long.valueOf(val.toString());
    }

    private Integer toInt(Object val) {
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).intValue();
        return Integer.valueOf(val.toString());
    }

    private String toString(Object val) {
        return val == null ? null : val.toString();
    }
}
