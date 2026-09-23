package com.starlink;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 充值「端到端接通」契约测试（真实 HTTP + 真实 MySQL）。
 * <p>
 * 覆盖 R-F1 / R-F2 / R-F3 / R-F7 与响应字段要求（§五）：
 * 以<b>小程序会员身份</b>真实登录拿 token，再走真实 HTTP 调用充值试算与充值接口，
 * 验证「预览金额 == 实际入账」「同一幂等键只入账一次」，
 * 并证明新增的试算接口对会员令牌可访问（即前端确实接通、未被 403 拦截）。
 * <p>
 * 测试数据一律 TESTP1F 前缀，用例前后清理；不触碰真实会员与其历史充值记录。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"dev", "test"})
class RechargeApiContractTest {

    private static final String TEST_PHONE = "13930000001";
    private static final String TEST_MEMBER_NO = "TESTP1F001";
    private static final String TEST_CAMPAIGN_NO = "TESTP1F001C";
    private static final String MEMBER_PWD = "Member@123456";
    private static final long DIAMOND_LEVEL_ID = 4L; // points_multiple = 3.00

    private static final String TIERS =
            "{\"type\":\"recharge_bonus\",\"tiers\":["
                    + "{\"min\":200,\"bonus_rate\":5},"
                    + "{\"min\":500,\"bonus_rate\":10},"
                    + "{\"min\":1000,\"bonus_rate\":15}]}";

    @LocalServerPort
    private int port;
    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ObjectMapper objectMapper;

    private Long memberId;
    private String token;

    @BeforeEach
    void setUp() {
        cleanUp();
        insertTestCampaign();
        memberId = insertTestMember();
        token = memberLogin();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        List<Long> ids = jdbc.queryForList("SELECT id FROM member WHERE member_no LIKE 'TESTP1F%'", Long.class);
        for (Long id : ids) {
            jdbc.update("DELETE FROM member_balance_log WHERE member_id = ?", id);
            jdbc.update("DELETE FROM member_points_log WHERE member_id = ?", id);
            jdbc.update("DELETE FROM member_recharge WHERE member_id = ?", id);
        }
        jdbc.update("DELETE FROM member WHERE member_no LIKE 'TESTP1F%'");
        jdbc.update("DELETE FROM member WHERE phone = ?", TEST_PHONE);
        jdbc.update("DELETE FROM campaign WHERE campaign_no LIKE 'TESTP1F%'");
    }

    private void insertTestCampaign() {
        jdbc.update("INSERT INTO campaign (campaign_no, campaign_name, campaign_type, start_time, end_time, "
                        + "rules, used_budget, used_count, member_limit, status) "
                        + "VALUES (?, 'P1F测试-充值赠送', 1, '2000-01-01 00:00:00', '2099-12-31 23:59:59', "
                        + "?, 0.00, 0, 1, 2)",
                TEST_CAMPAIGN_NO, TIERS);
    }

    private Long insertTestMember() {
        jdbc.update("INSERT INTO member (member_no, phone, password_hash, real_name, gender, level_id, "
                        + "total_points, available_points, total_recharge, balance, total_consumption, "
                        + "register_source, status) VALUES (?, ?, ?, 'P1F测试会员', 1, ?, 0, 0, 0.00, 0.00, 0.00, 1, 1)",
                TEST_MEMBER_NO, TEST_PHONE, passwordEncoder.encode(MEMBER_PWD), DIAMOND_LEVEL_ID);
        return jdbc.queryForObject("SELECT id FROM member WHERE member_no = ?", Long.class, TEST_MEMBER_NO);
    }

    /* ==================== HTTP 辅助 ==================== */

    private ResponseEntity<String> get(String path, String bearer) {
        HttpHeaders headers = new HttpHeaders();
        if (bearer != null) {
            headers.setBearerAuth(bearer);
        }
        return rest.exchange("http://localhost:" + port + path, HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> postJson(String path, Object body, String bearer) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (bearer != null) {
            headers.setBearerAuth(bearer);
        }
        return rest.exchange("http://localhost:" + port + path, HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
    }

    private JsonNode data(ResponseEntity<String> resp) throws Exception {
        return objectMapper.readTree(resp.getBody()).path("data");
    }

    private String memberLogin() {
        ResponseEntity<String> resp = postJson("/api/member/login",
                Map.of("phone", TEST_PHONE, "password", MEMBER_PWD), null);
        try {
            String t = objectMapper.readTree(resp.getBody()).path("data").path("token").asText("");
            assertFalse(t.isBlank(), "会员登录失败，无法取得 token: " + resp.getBody());
            return t;
        } catch (Exception e) {
            throw new IllegalStateException("解析登录响应失败: " + resp.getBody(), e);
        }
    }

    private Map<String, Object> memberRow() {
        return jdbc.queryForMap("SELECT balance, total_recharge, available_points FROM member WHERE id = ?", memberId);
    }

    private int rechargeCount() {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM member_recharge WHERE member_id = ?", Integer.class, memberId);
        return n == null ? 0 : n;
    }

    private static BigDecimal dec(JsonNode node) {
        return new BigDecimal(node.asText("0"));
    }

    /* ==================== R-F1 ==================== */

    @Test
    @DisplayName("R-F1 充 100：预览返回 实付100 / 赠送0 / 到账100（会员令牌可访问试算接口）")
    void preview100() throws Exception {
        ResponseEntity<String> resp = get("/api/member/recharge/preview?memberId=" + memberId + "&amount=100", token);

        assertEquals(HttpStatus.OK, resp.getStatusCode(), "会员令牌应可访问充值试算: " + resp.getBody());
        JsonNode d = data(resp);
        assertEquals(0, dec(d.path("rechargeAmount")).compareTo(new BigDecimal("100")));
        assertEquals(0, dec(d.path("bonusAmount")).compareTo(BigDecimal.ZERO), "100 未满 200 档，赠送应为 0");
        assertEquals(0, dec(d.path("totalAmount")).compareTo(new BigDecimal("100")), "到账应为 100 而非 115");
        // 全局 Jackson 配置为 non_null（null 字段不序列化），故「未命中活动」表现为字段缺失而非显式 null
        JsonNode campaignId = d.path("campaignId");
        assertTrue(campaignId.isMissingNode() || campaignId.isNull(),
                "未命中任何档位时不应记录活动 ID，实际: " + campaignId);
    }

    /* ==================== R-F2 ==================== */

    @Test
    @DisplayName("R-F2 充 500：活动命中 10% → 预览返回 实付500 / 赠送50 / 到账550")
    void preview500() throws Exception {
        ResponseEntity<String> resp = get("/api/member/recharge/preview?memberId=" + memberId + "&amount=500", token);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        JsonNode d = data(resp);
        assertEquals(0, dec(d.path("rechargeAmount")).compareTo(new BigDecimal("500")));
        assertEquals(0, dec(d.path("bonusAmount")).compareTo(new BigDecimal("50")));
        assertEquals(0, dec(d.path("totalAmount")).compareTo(new BigDecimal("550")));
        assertFalse(d.path("campaignId").isNull(), "命中档位时应返回活动 ID");
    }

    /* ==================== §五 响应字段 ==================== */

    @Test
    @DisplayName("§五 充值响应包含 rechargeId/rechargeNo/rechargeAmount/bonusAmount/totalAmount/balanceAfter/status")
    void rechargeResponseFields() throws Exception {
        ResponseEntity<String> resp = postJson("/api/member/recharge",
                Map.of("memberId", memberId, "amount", 500, "paymentMethod", 1, "idempotentKey", "K-FIELDS"), token);

        assertEquals(HttpStatus.OK, resp.getStatusCode(), resp.getBody());
        JsonNode d = data(resp);
        for (String field : List.of("id", "rechargeNo", "rechargeAmount", "bonusAmount",
                "totalAmount", "balanceAfter", "status")) {
            assertTrue(d.has(field) && !d.path(field).isNull(), "响应缺少字段: " + field + " → " + resp.getBody());
        }
        assertEquals(0, dec(d.path("rechargeAmount")).compareTo(new BigDecimal("500")));
        assertEquals(0, dec(d.path("bonusAmount")).compareTo(new BigDecimal("50")));
        assertEquals(0, dec(d.path("totalAmount")).compareTo(new BigDecimal("550")));
        assertEquals(0, dec(d.path("balanceAfter")).compareTo(new BigDecimal("550")), "到账后余额应为 550");
    }

    /* ==================== R-F7 ==================== */

    @Test
    @DisplayName("R-F7 预览与最终实际入账一致（预览仅展示，入账由后端重算）")
    void previewMatchesActual() throws Exception {
        JsonNode preview = data(get("/api/member/recharge/preview?memberId=" + memberId + "&amount=500", token));

        JsonNode actual = data(postJson("/api/member/recharge",
                Map.of("memberId", memberId, "amount", 500, "paymentMethod", 1, "idempotentKey", "K-MATCH"), token));

        assertEquals(0, dec(preview.path("rechargeAmount")).compareTo(dec(actual.path("rechargeAmount"))));
        assertEquals(0, dec(preview.path("bonusAmount")).compareTo(dec(actual.path("bonusAmount"))),
                "预览赠送与实际赠送必须一致");
        assertEquals(0, dec(preview.path("totalAmount")).compareTo(dec(actual.path("totalAmount"))),
                "预览到账与实际到账必须一致");

        Map<String, Object> row = memberRow();
        assertEquals(0, new BigDecimal(String.valueOf(row.get("balance"))).compareTo(new BigDecimal("550")),
                "余额应等于实际到账金额");
    }

    /* ==================== R-F3（HTTP 层幂等接通） ==================== */

    @Test
    @DisplayName("R-F3 同一 idempotentKey 经 HTTP 连续提交两次 → 只有一笔充值、余额只增加一次")
    void httpIdempotency() throws Exception {
        Map<String, Object> body = Map.of("memberId", memberId, "amount", 500,
                "paymentMethod", 1, "idempotentKey", "K-HTTP-IDEMPOTENT");

        JsonNode first = data(postJson("/api/member/recharge", body, token));
        JsonNode second = data(postJson("/api/member/recharge", body, token));

        assertEquals(first.path("rechargeNo").asText(), second.path("rechargeNo").asText(),
                "重复提交应返回同一充值单号");
        assertEquals(first.path("id").asLong(), second.path("id").asLong());

        assertEquals(1, rechargeCount(), "HTTP 重复提交只应产生一笔充值");
        Integer ledgerCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM member_balance_log WHERE member_id = ? AND biz_type = 3",
                Integer.class, memberId);
        assertEquals(1, ledgerCount == null ? 0 : ledgerCount, "只应写一条充值余额流水");
        assertEquals(0, new BigDecimal(String.valueOf(memberRow().get("balance")))
                .compareTo(new BigDecimal("550")), "余额只应增加一次");
    }

    @Test
    @DisplayName("R-F3b 缺省幂等键时仍可充值（记录该降级行为）")
    void missingKeyStillCharges() throws Exception {
        ResponseEntity<String> resp = postJson("/api/member/recharge",
                Map.of("memberId", memberId, "amount", 100, "paymentMethod", 1), token);

        assertEquals(HttpStatus.OK, resp.getStatusCode(), resp.getBody());
        assertEquals(1, rechargeCount());
    }
}
