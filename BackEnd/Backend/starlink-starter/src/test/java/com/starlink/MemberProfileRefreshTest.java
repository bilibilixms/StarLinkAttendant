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
 * 会员端余额实时刷新接口测试（真实 HTTP + 真实 MySQL）。
 * <p>
 * 验证会员端「我的」页刷新余额所依赖的 `GET /api/auth/info`：
 * <ol>
 *   <li>会员令牌可访问，并返回<b>实时</b>会员信息（含 balance/availablePoints）；</li>
 *   <li>后台给该会员充值后，同一令牌再次调用即可看到新余额
 *       —— 这正是「后台充值后小程序无需重新登录即可看到最新余额」的数据来源；</li>
 *   <li>连续两次充值后返回的是<b>最新</b>余额（不出现旧值覆盖新值）；</li>
 *   <li>无令牌/无效令牌 → 401（会员端据此走既有统一登录失效处理）；</li>
 *   <li><b>回归</b>：员工令牌的响应结构不变（仍返回员工字段，member 段为 null）。</li>
 * </ol>
 * 数据全部使用 TESTPF 前缀，用例前后清理；不触碰真实会员与其历史充值记录。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"dev", "test"})
class MemberProfileRefreshTest {

    private static final String TEST_PHONE = "13940000001";
    private static final String TEST_MEMBER_NO = "TESTPF0001";
    private static final String TEST_CAMPAIGN_NO = "TESTPF0001C";
    private static final String MEMBER_PWD = "Member@123456";
    private static final long DIAMOND_LEVEL_ID = 4L;

    /** 阶梯：满 200 送 5%，便于断言「到账 = 实付 + 赠送」 */
    private static final String TIERS =
            "{\"type\":\"recharge_bonus\",\"tiers\":[{\"min\":200,\"bonus_rate\":5}]}";

    /** 种子员工（super_admin），用于员工响应回归 */
    private static final String STAFF_PHONE = "13800000001";
    private static final String STAFF_PWD = "123456";

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
    private String memberToken;

    @BeforeEach
    void setUp() {
        cleanUp();
        insertTestCampaign();
        memberId = insertTestMember();
        memberToken = memberLogin();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        List<Long> ids = jdbc.queryForList("SELECT id FROM member WHERE member_no LIKE 'TESTPF%'", Long.class);
        for (Long id : ids) {
            jdbc.update("DELETE FROM member_balance_log WHERE member_id = ?", id);
            jdbc.update("DELETE FROM member_points_log WHERE member_id = ?", id);
            jdbc.update("DELETE FROM member_recharge WHERE member_id = ?", id);
        }
        jdbc.update("DELETE FROM member WHERE member_no LIKE 'TESTPF%'");
        jdbc.update("DELETE FROM member WHERE phone = ?", TEST_PHONE);
        jdbc.update("DELETE FROM campaign WHERE campaign_no LIKE 'TESTPF%'");
    }

    private void insertTestCampaign() {
        jdbc.update("INSERT INTO campaign (campaign_no, campaign_name, campaign_type, start_time, end_time, "
                        + "rules, used_budget, used_count, member_limit, status) "
                        + "VALUES (?, 'PF测试赠送', 1, '2000-01-01 00:00:00', '2099-12-31 23:59:59', ?, 0.00, 0, 1, 2)",
                TEST_CAMPAIGN_NO, TIERS);
    }

    private Long insertTestMember() {
        jdbc.update("INSERT INTO member (member_no, phone, password_hash, real_name, gender, level_id, "
                        + "total_points, available_points, total_recharge, balance, total_consumption, "
                        + "register_source, status) VALUES (?, ?, ?, 'PF测试会员', 1, ?, 0, 0, 0.00, 100.00, 0.00, 1, 1)",
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
            assertFalse(t.isBlank(), "会员登录失败: " + resp.getBody());
            return t;
        } catch (Exception e) {
            throw new IllegalStateException("解析登录响应失败: " + resp.getBody(), e);
        }
    }

    private String employeeLogin() {
        ResponseEntity<String> resp = postJson("/api/auth/login",
                Map.of("username", STAFF_PHONE, "password", STAFF_PWD), null);
        try {
            String t = objectMapper.readTree(resp.getBody()).path("data").path("accessToken").asText("");
            assertFalse(t.isBlank(), "员工登录失败: " + resp.getBody());
            return t;
        } catch (Exception e) {
            throw new IllegalStateException("解析员工登录响应失败: " + resp.getBody(), e);
        }
    }

    private BigDecimal profileBalance(String bearer) throws Exception {
        JsonNode d = data(get("/api/auth/info", bearer));
        assertFalse(d.path("member").isMissingNode() || d.path("member").isNull(),
                "会员令牌应返回 member 段: " + d);
        return new BigDecimal(d.path("member").path("balance").asText("0"));
    }

    /* ==================== 场景：会员令牌可读实时余额 ==================== */

    @Test
    @DisplayName("会员令牌调用 /api/auth/info → 200 且返回实时余额（初始 100）")
    void memberCanReadRealtimeBalance() throws Exception {
        ResponseEntity<String> resp = get("/api/auth/info", memberToken);
        assertEquals(HttpStatus.OK, resp.getStatusCode(), "会员令牌应可访问: " + resp.getBody());

        JsonNode d = data(resp);
        assertEquals(memberId, d.path("member").path("id").asLong());
        assertEquals(0, new BigDecimal(d.path("member").path("balance").asText()).compareTo(new BigDecimal("100.00")));
        assertFalse(d.path("member").path("levelName").asText("").isBlank(), "应返回等级名");
    }

    /* ==================== 场景 A/B：后台充值后会员端读到新余额 ==================== */

    @Test
    @DisplayName("场景A/B：后台充值 200（送 5% = 10）后，同一令牌读到 310，无需重新登录")
    void balanceReflectsAdminRechargeWithoutRelogin() throws Exception {
        assertEquals(0, profileBalance(memberToken).compareTo(new BigDecimal("100.00")), "充值前应 100");

        // 模拟「后台管理员给该会员充值 200」：走真实充值接口
        ResponseEntity<String> recharge = postJson("/api/member/recharge",
                Map.of("memberId", memberId, "amount", 200, "paymentMethod", 1, "idempotentKey", "PF-A"),
                memberToken);
        assertEquals(HttpStatus.OK, recharge.getStatusCode(), recharge.getBody());

        // 不重新登录，直接用同一令牌读取 → 应是 100 + (200 + 200*5%) = 310
        assertEquals(0, profileBalance(memberToken).compareTo(new BigDecimal("310.00")),
                "后台充值后应立即可读到新余额（100 + 210）");
    }

    /* ==================== 场景 C：连续两次充值取最新值 ==================== */

    @Test
    @DisplayName("场景C：连续两次充值后读到最新余额，不出现旧值覆盖新值")
    void consecutiveRechargesReturnLatestBalance() throws Exception {
        postJson("/api/member/recharge",
                Map.of("memberId", memberId, "amount", 200, "paymentMethod", 1, "idempotentKey", "PF-C1"), memberToken);
        BigDecimal afterFirst = profileBalance(memberToken);
        assertEquals(0, afterFirst.compareTo(new BigDecimal("310.00")));

        postJson("/api/member/recharge",
                Map.of("memberId", memberId, "amount", 400, "paymentMethod", 1, "idempotentKey", "PF-C2"), memberToken);
        // 第二次：400 + 400*5% = 420 → 310 + 420 = 730
        BigDecimal afterSecond = profileBalance(memberToken);
        assertEquals(0, afterSecond.compareTo(new BigDecimal("730.00")), "应返回最新余额，而非第一次的旧值");
        assertTrue(afterSecond.compareTo(afterFirst) > 0);
    }

    /* ==================== 场景 E：无令牌/无效令牌 → 401 ==================== */

    @Test
    @DisplayName("场景E：无令牌与无效令牌访问 /api/auth/info → 401（不是 403）")
    void unauthenticatedGets401() {
        assertEquals(HttpStatus.UNAUTHORIZED, get("/api/auth/info", null).getStatusCode(),
                "无令牌应 401");
        assertEquals(HttpStatus.UNAUTHORIZED, get("/api/auth/info", "not-a-valid-token").getStatusCode(),
                "无效令牌应 401");
    }

    /* ==================== 回归：员工响应结构不变 ==================== */

    @Test
    @DisplayName("回归：员工令牌调用 /api/auth/info 仍返回员工字段，且 member 段为空")
    void employeeResponseUnchanged() throws Exception {
        ResponseEntity<String> resp = get("/api/auth/info", employeeLogin());
        assertEquals(HttpStatus.OK, resp.getStatusCode(), resp.getBody());

        JsonNode d = data(resp);
        assertFalse(d.path("roles").isMissingNode(), "员工响应应包含 roles");
        assertTrue(d.path("employeeNo").asText("").length() > 0, "员工响应应包含 employeeNo");
        // 员工响应不包含会员段（附加字段为 null，被 non_null 序列化策略省略）
        assertTrue(d.path("member").isMissingNode() || d.path("member").isNull(),
                "员工响应不应包含会员段: " + d);
    }
}
