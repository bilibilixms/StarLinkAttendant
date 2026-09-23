package com.starlink.marketing.service;

import java.util.List;
import java.util.Map;

/**
 * 第三方集成服务接口（INT-01 ~ INT-05）。
 * <p>
 * 覆盖集成配置、实名认证、短信通道、公安审计与集成日志，
 * 全部基于现有表实现：member / notification / audit_log / system_config / session。
 */
public interface IntegrationService {

    // ==================== INT-01 集成配置（system_config） ====================

    /** 查询所有集成配置 */
    List<Map<String, Object>> getConfigs();

    /** 新增集成配置 */
    Map<String, Object> saveConfig(Map<String, Object> config);

    /** 更新集成配置 */
    Map<String, Object> updateConfig(Long id, Map<String, Object> config);

    /** 测试连通性（Demo：始终返回成功） */
    Map<String, Object> testIntegration(Long id);

    // ==================== INT-02 实名认证（member + audit_log） ====================

    /** 执行实名认证（Demo：校验 member 表 + 写 audit_log） */
    Map<String, Object> verifyIdentity(String realName, String idCard, Long memberId);

    /** 查询实名认证日志（从 audit_log 读取） */
    Map<String, Object> getIdVerificationLogs(int page, int size, Integer verificationStatus);

    // ==================== INT-03 短信通道（notification） ====================

    /** 发送短信（Demo：仅记录到 notification 表，不实际发送） */
    Map<String, Object> sendSms(String phone, String title, String content);

    /** 查询短信日志（从 notification 表查询） */
    Map<String, Object> getSmsLogs(int page, int size, Integer status, String phone);

    // ==================== INT-04 公安审计（session + audit_log） ====================

    /** 生成待上报审计数据（从 session 抽取 → 写 audit_log） */
    int generateAuditData();

    /** 模拟上报（将 audit_log 中的记录标记为已上报） */
    int uploadAuditData();

    /** 手动添加一条审计记录 */
    Map<String, Object> addAuditLog(Map<String, Object> audit);

    /** 查询审计日志（从 audit_log 读取） */
    Map<String, Object> getAuditLogs(int page, int size, Integer uploaded);

    /** 查询待生成审计数据的 session 列表 */
    List<Map<String, Object>> getPendingAuditSessions();

    // ==================== INT-05 集成日志（audit_log 聚合） ====================

    /** 查询集成调用日志（聚合三种 biz_type） */
    Map<String, Object> getIntegrationLogs(int page, int size, String bizType, Integer status);
}
