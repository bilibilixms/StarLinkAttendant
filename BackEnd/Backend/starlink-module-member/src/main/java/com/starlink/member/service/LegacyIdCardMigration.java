package com.starlink.member.service;

import com.starlink.common.exception.BusinessException;
import com.starlink.common.util.IdCardProtector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/** 启动时迁移旧版明文身份证号；每行更新可重试，不输出敏感值。 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class LegacyIdCardMigration implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;
    private final IdCardProtector protector;

    @Override
    public void run(ApplicationArguments args) {
        long lastId = 0;
        int migrated = 0;
        while (true) {
            List<LegacyRow> rows = jdbcTemplate.query(
                    "SELECT id, id_card FROM member WHERE id > ? AND id_card IS NOT NULL " +
                            "AND id_card <> '' ORDER BY id LIMIT 500",
                    (rs, rowNum) -> new LegacyRow(rs.getLong("id"), rs.getString("id_card")), lastId);
            if (rows.isEmpty()) {
                break;
            }
            for (LegacyRow row : rows) {
                String hash;
                try {
                    hash = protector.hash(row.idCard());
                } catch (BusinessException e) {
                    hash = null;
                    log.warn("旧身份证号格式无效，已清除明文: memberId={}", row.id());
                }
                migrated += jdbcTemplate.update(
                        "UPDATE member SET id_card_hash = ?, id_card = NULL WHERE id = ? AND id_card = ?",
                        hash, row.id(), row.idCard());
                lastId = row.id();
            }
        }
        if (migrated > 0) {
            log.info("旧版身份证明文迁移完成: {} 条", migrated);
        }
        scrubAuditLogs();
    }

    private void scrubAuditLogs() {
        long lastId = 0;
        int scrubbed = 0;
        while (true) {
            List<LegacyRow> rows = jdbcTemplate.query(
                    "SELECT id, JSON_UNQUOTE(JSON_EXTRACT(detail, '$.idCard')) AS id_card " +
                            "FROM audit_log WHERE biz_type = 'identity_verify' AND id > ? " +
                            "AND JSON_EXTRACT(detail, '$.idCard') IS NOT NULL ORDER BY id LIMIT 500",
                    (rs, rowNum) -> new LegacyRow(rs.getLong("id"), rs.getString("id_card")), lastId);
            if (rows.isEmpty()) {
                break;
            }
            for (LegacyRow row : rows) {
                String value = row.idCard();
                if (value != null && !value.contains("*") && !"null".equals(value)) {
                    String masked;
                    try {
                        masked = protector.mask(value);
                    } catch (BusinessException e) {
                        masked = null;
                    }
                    scrubbed += jdbcTemplate.update(
                            "UPDATE audit_log SET detail = JSON_SET(detail, '$.idCard', ?) WHERE id = ?",
                            masked, row.id());
                }
                lastId = row.id();
            }
        }
        if (scrubbed > 0) {
            log.info("实名认证审计明文清理完成: {} 条", scrubbed);
        }
    }

    private record LegacyRow(long id, String idCard) {}
}
