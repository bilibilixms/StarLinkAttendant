package com.starlink.marketing.controller;

import com.starlink.common.result.Result;
import com.starlink.marketing.service.IntegrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 第三方集成控制器（INT-01 ~ INT-05）。
 * <p>
 * 提供集成配置、实名认证、短信通道、公安审计与集成日志接口，
 * 全部基于现有表实现，不依赖新建表。
 */
@Slf4j
@RestController
@RequestMapping("/api/marketing/integration")
@RequiredArgsConstructor
public class IntegrationController {

    private final IntegrationService integrationService;

    // ==================== INT-01 集成配置（system_config） ====================

    @GetMapping("/configs")
    public Result<List<Map<String, Object>>> getConfigs() {
        log.info("查询集成配置列表");
        return Result.ok(integrationService.getConfigs(), "查询成功");
    }

    @PostMapping("/configs")
    public Result<Map<String, Object>> saveConfig(@RequestBody Map<String, Object> config) {
        log.info("新增集成配置，类型: {}", config.get("integrationType"));
        return Result.ok(integrationService.saveConfig(config), "保存成功");
    }

    @PutMapping("/configs/{id}")
    public Result<Map<String, Object>> updateConfig(@PathVariable Long id,
                                                     @RequestBody Map<String, Object> config) {
        log.info("更新集成配置，ID: {}", id);
        Map<String, Object> result = integrationService.updateConfig(id, config);
        return result != null ? Result.ok(result, "更新成功") : Result.fail(404, "配置不存在");
    }

    @PostMapping("/configs/{id}/test")
    public Result<Map<String, Object>> testIntegration(@PathVariable Long id) {
        log.info("测试集成配置连通性，ID: {}", id);
        return Result.ok(integrationService.testIntegration(id), "测试完成");
    }

    // ==================== INT-02 实名认证（member + audit_log） ====================

    @PostMapping("/id-verify")
    public Result<Map<String, Object>> verifyIdentity(@RequestBody Map<String, Object> body) {
        String realName = (String) body.get("realName");
        String idCard = (String) body.get("idCard");
        Long memberId = body.get("memberId") != null
                ? Long.valueOf(body.get("memberId").toString()) : null;

        log.info("实名认证请求，姓名: {}", realName);
        Map<String, Object> result = integrationService.verifyIdentity(realName, idCard, memberId);
        boolean success = Boolean.TRUE.equals(result.get("success"));
        return Result.ok(result, success ? "验证通过" : (String) result.getOrDefault("message", "验证不通过"));
    }

    @GetMapping("/id-verify-logs")
    public Result<Map<String, Object>> getIdVerificationLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer verificationStatus) {
        log.info("查询实名认证日志，page: {}, size: {}", page, size);
        return Result.ok(integrationService.getIdVerificationLogs(page, size, verificationStatus), "查询成功");
    }

    // ==================== INT-03 短信通道（notification） ====================

    @PostMapping("/sms/send")
    public Result<Map<String, Object>> sendSms(@RequestBody Map<String, Object> body) {
        String phone = (String) body.get("phone");
        String title = (String) body.getOrDefault("title", "验证码");
        String content = (String) body.get("content");

        log.info("短信发送请求，手机号: {}", phone);
        Map<String, Object> result = integrationService.sendSms(phone, title, content);
        return Result.ok(result, (String) result.get("message"));
    }

    @GetMapping("/sms-logs")
    public Result<Map<String, Object>> getSmsLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String phone) {
        log.info("查询短信日志，page: {}, size: {}", page, size);
        return Result.ok(integrationService.getSmsLogs(page, size, status, phone), "查询成功");
    }

    // ==================== INT-04 公安审计（session + audit_log） ====================

    @PostMapping("/audit/generate")
    public Result<Map<String, Object>> generateAuditData() {
        log.info("生成公安审计上报数据");
        int count = integrationService.generateAuditData();
        return Result.ok(Map.of("generatedCount", count), "数据生成完成");
    }

    @PostMapping("/audit/upload")
    public Result<Map<String, Object>> uploadAuditData() {
        log.info("上报公安审计数据");
        int count = integrationService.uploadAuditData();
        return Result.ok(Map.of("uploadedCount", count), "上报完成（Demo 模拟）");
    }

    @PostMapping("/audit/add")
    public Result<Map<String, Object>> addAuditLog(@RequestBody Map<String, Object> audit) {
        log.info("新增公安审计记录");
        return Result.ok(integrationService.addAuditLog(audit), "添加成功");
    }

    @GetMapping("/audit/pending-sessions")
    public Result<List<Map<String, Object>>> getPendingAuditSessions() {
        log.info("查询待生成审计数据的上网会话");
        List<Map<String, Object>> sessions = integrationService.getPendingAuditSessions();
        return Result.ok(sessions, "查询成功");
    }

    @GetMapping("/audit-logs")
    public Result<Map<String, Object>> getAuditLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer uploaded) {
        log.info("查询公安审计日志，page: {}, size: {}", page, size);
        return Result.ok(integrationService.getAuditLogs(page, size, uploaded), "查询成功");
    }

    // ==================== INT-05 集成日志（audit_log 聚合） ====================

    @GetMapping("/logs")
    public Result<Map<String, Object>> getIntegrationLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer integrationType,
            @RequestParam(required = false) Integer status) {
        log.info("查询集成调用日志，page: {}, size: {}, type: {}", page, size, integrationType);
        String bizType = integrationType != null ? intToBizType(integrationType) : null;
        return Result.ok(integrationService.getIntegrationLogs(page, size, bizType, status), "查询成功");
    }

    private String intToBizType(int type) {
        return switch (type) {
            case 1 -> "identity_verify";
            case 2 -> "sms_send";
            case 3 -> "police_audit";
            default -> null;
        };
    }
}
