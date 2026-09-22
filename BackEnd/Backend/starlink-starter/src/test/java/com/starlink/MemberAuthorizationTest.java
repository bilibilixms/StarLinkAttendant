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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0-4 角色与资源权限隔离回归测试（TEST-P0-4-01 ~ TEST-P0-4-10）。
 * <p>
 * 采用 <b>真实 HTTP</b>（内嵌 Tomcat + TestRestTemplate）而非 MockMvc —— 这样 401/403
 * 是真实由 Security 过滤器链 + accessDeniedHandler 产生的状态码，
 * 不存在「Mock 层放行」的假象。
 * <p>
 * 三层断言，缺一不可：
 * <ol>
 *   <li><b>角色边界</b>（SecurityConfig）：会员不得访问收银/报表/系统等管理域；</li>
 *   <li><b>资源归属</b>（Service 层 MemberAccessGuard）：会员不得操作其他会员的数据；</li>
 *   <li><b>数据未被改动</b>：越权被拒后回读数据库确认目标数据原样（不只看响应码）。</li>
 * </ol>
 * <p>
 * JWT 密钥由测试 profile 提供（{@code src/test/resources/application-test.yml}）：
 * 生产配置 {@code ${JWT_SECRET:}} 无默认值，且启动期 JwtSecretValidator 会强制校验，
 * 故测试必须显式提供测试专用密钥（不注入则应用直接启动失败）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"dev", "test"})
class MemberAuthorizationTest {

    /** 测试会员 A（越权发起方） */
    private static final String PHONE_A = "13900000001";
    /** 测试会员 B（被越权目标） */
    private static final String PHONE_B = "13900000002";
    private static final String MEMBER_PWD = "Member@123456";

    /** 种子员工：super_admin（张伟） */
    private static final String STAFF_ADMIN = "13800000001";
    /** 种子员工：cashier（王磊） */
    private static final String STAFF_CASHIER = "13800000003";
    /** 种子员工：net_admin（刘洋） */
    private static final String STAFF_NET_ADMIN = "13800000005";
    /** 种子员工统一密码 */
    private static final String STAFF_PWD = "123456";

    private static final String ORIGINAL_B_NAME = "会员B原名";

    private static final List<String> TEST_PHONES = List.of(PHONE_A, PHONE_B);

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

    private Long memberAId;
    private Long memberBId;
    private String tokenA;
    private String tokenAdmin;
    private String tokenCashier;

    /* ==================== 夹具 ==================== */

    @BeforeEach
    void setUp() throws Exception {
        cleanUpTestData();

        memberAId = insertMember(PHONE_A, "TESTP4A001", "会员A");
        memberBId = insertMember(PHONE_B, "TESTP4B002", ORIGINAL_B_NAME);

        tokenA = memberToken(PHONE_A);
        tokenAdmin = employeeToken(STAFF_ADMIN);
        tokenCashier = employeeToken(STAFF_CASHIER);
    }

    @AfterEach
    void tearDown() {
        cleanUpTestData();
    }

    /**
     * 清理测试数据。
     * <p>
     * 以<b>稳定的 member_no 前缀</b>（TESTP4）为主键，而非手机号 —— 用例会尝试修改手机号，
     * 一旦某次（例如反向验证时）改动成功，按手机号清理就会漏掉该行，导致下一轮 setUp
     * 撞 uk_member_no。按 member_no 前缀清理可避免这种夹具泄漏。
     */
    private void cleanUpTestData() {
        List<Long> ids = jdbc.queryForList(
                "SELECT id FROM member WHERE member_no LIKE 'TESTP4%'", Long.class);
        for (Long id : ids) {
            // 必须先清理全部子表再删会员：本用例含「会员给自己充值」，
            // 充值现已写 member_balance_log（P1 起），漏删会留下孤儿流水。
            jdbc.update("DELETE FROM member_balance_log WHERE member_id = ?", id);
            jdbc.update("DELETE FROM member_recharge WHERE member_id = ?", id);
            jdbc.update("DELETE FROM member_points_log WHERE member_id = ?", id);
        }
        jdbc.update("DELETE FROM member WHERE member_no LIKE 'TESTP4%'");
        for (String phone : TEST_PHONES) {
            jdbc.update("DELETE FROM member WHERE phone = ?", phone);
        }
    }

    private Long insertMember(String phone, String memberNo, String realName) {
        jdbc.update("INSERT INTO member (member_no, phone, password_hash, real_name, gender, level_id, "
                        + "total_points, available_points, total_recharge, balance, total_consumption, "
                        + "register_source, status) VALUES (?, ?, ?, ?, 1, 1, 500, 500, 0.00, 100.00, 0.00, 1, 1)",
                memberNo, phone, passwordEncoder.encode(MEMBER_PWD), realName);
        return jdbc.queryForObject("SELECT id FROM member WHERE phone = ?", Long.class, phone);
    }

    /* ==================== 真实 HTTP 调用 ==================== */

    private ResponseEntity<String> call(HttpMethod method, String path, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        HttpEntity<Object> entity = new HttpEntity<>(body, headers);
        return rest.exchange("http://localhost:" + port + path, method, entity, String.class);
    }

    private int status(HttpMethod method, String path, String token) {
        return call(method, path, token, null).getStatusCode().value();
    }

    private int status(HttpMethod method, String path, String token, Object body) {
        return call(method, path, token, body).getStatusCode().value();
    }

    private int codeOf(ResponseEntity<String> resp) {
        try {
            return objectMapper.readTree(resp.getBody()).path("code").asInt(-1);
        } catch (Exception e) {
            return -1;
        }
    }

    private String employeeToken(String phone) throws Exception {
        ResponseEntity<String> resp = call(HttpMethod.POST, "/api/auth/login", null,
                Map.of("username", phone, "password", STAFF_PWD));
        JsonNode node = objectMapper.readTree(resp.getBody());
        String token = node.path("data").path("accessToken").asText("");
        assertFalse(token.isBlank(), "员工登录失败，无法取得 token: " + resp.getBody());
        return token;
    }

    private String memberToken(String phone) throws Exception {
        ResponseEntity<String> resp = call(HttpMethod.POST, "/api/member/login", null,
                Map.of("phone", phone, "password", MEMBER_PWD));
        String token = objectMapper.readTree(resp.getBody()).path("data").path("token").asText("");
        assertFalse(token.isBlank(), "会员登录失败，无法取得 token: " + resp.getBody());
        return token;
    }

    /* ==================== 数据回读 ==================== */

    private Map<String, Object> row(String phone) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM member WHERE phone = ?", phone);
        assertEquals(1, rows.size(), "手机号 " + phone + " 应恰好存在 1 行");
        return rows.get(0);
    }

    /* ==================== TEST-P0-4-01 ==================== */

    @Test
    @DisplayName("TEST-P0-4-01 匿名访问需登录接口必须 401")
    void anonymousGetsUnauthorized() {
        for (String url : List.of("/api/member/list", "/api/cashier/orders",
                "/api/report/dashboard", "/api/system/users")) {
            assertEquals(401, status(HttpMethod.GET, url, null),
                    url + " 匿名访问应返回 401");
        }
    }

    /* ==================== TEST-P0-4-02/03/04 ==================== */

    @Test
    @DisplayName("TEST-P0-4-02 会员访问 /api/cashier/** 必须 403")
    void memberCannotAccessCashier() {
        assertEquals(403, status(HttpMethod.GET, "/api/cashier/orders", tokenA),
                "会员访问收银接口应 403");
    }

    @Test
    @DisplayName("TEST-P0-4-03 会员访问 /api/report/** 必须 403")
    void memberCannotAccessReport() {
        assertEquals(403, status(HttpMethod.GET, "/api/report/dashboard", tokenA),
                "会员访问报表接口应 403");
    }

    @Test
    @DisplayName("TEST-P0-4-04 会员访问 /api/system/** 必须 403")
    void memberCannotAccessSystem() {
        assertEquals(403, status(HttpMethod.GET, "/api/system/users", tokenA),
                "会员访问系统管理接口应 403");
    }

    /* ==================== TEST-P0-4-05 ==================== */

    @Test
    @DisplayName("TEST-P0-4-05 会员A 注销 会员B 必须 403，且 B 数据不变")
    void memberACannotDeleteMemberB() {
        Map<String, Object> before = row(PHONE_B);

        assertEquals(403, status(HttpMethod.DELETE, "/api/member/" + memberBId, tokenA),
                "会员注销他人应 403");

        Map<String, Object> after = row(PHONE_B);
        assertNull(after.get("deleted_at"), "B 被误置为软删除");
        assertEquals(((Number) before.get("status")).intValue(), ((Number) after.get("status")).intValue(),
                "B 的状态被改动");
        assertEquals(ORIGINAL_B_NAME, after.get("real_name"), "B 的姓名被改动");
    }

    @Test
    @DisplayName("TEST-P0-4-05b 会员A 注销自己 也必须 403（注销属会员管理动作）")
    void memberCannotDeleteSelf() {
        assertEquals(403, status(HttpMethod.DELETE, "/api/member/" + memberAId, tokenA),
                "会员自助注销应 403");
        assertNull(row(PHONE_A).get("deleted_at"), "A 被误置为软删除");
    }

    /* ==================== TEST-P0-4-06 ==================== */

    @Test
    @DisplayName("TEST-P0-4-06 会员A 修改 会员B 必须 403，且 B 数据不变")
    void memberACannotUpdateMemberB() {
        Map<String, Object> before = row(PHONE_B);

        int st = status(HttpMethod.PUT, "/api/member/" + memberBId, tokenA,
                Map.of("realName", "被攻击者改名"));
        assertEquals(403, st, "会员修改他人资料应 403");

        Map<String, Object> after = row(PHONE_B);
        assertEquals(ORIGINAL_B_NAME, after.get("real_name"), "B 的姓名被覆盖");
        assertEquals(before.get("balance").toString(), after.get("balance").toString(), "B 的余额被改动");
        assertEquals(before.get("phone"), after.get("phone"), "B 的手机号被改动");
    }

    /* ==================== TEST-P0-4-07 ==================== */

    @Test
    @DisplayName("TEST-P0-4-07 会员A 读取 B 的敏感信息必须被拒（名册/详情/流水）")
    void memberACannotReadMemberBData() {
        // 1) 会员名册：403（SecurityConfig 角色边界）
        assertEquals(403, status(HttpMethod.GET, "/api/member/list", tokenA), "会员访问会员名册应 403");

        // 2) 他人详情：403，且响应体不得泄露 B 的姓名/手机号
        ResponseEntity<String> detail = call(HttpMethod.GET, "/api/member/" + memberBId, tokenA, null);
        assertEquals(403, detail.getStatusCode().value(), "会员读取他人详情应 403");
        String body = detail.getBody() == null ? "" : detail.getBody();
        assertFalse(body.contains(ORIGINAL_B_NAME), "响应体泄露了 B 的姓名");
        assertFalse(body.contains(PHONE_B), "响应体泄露了 B 的手机号");

        // 3) 他人充值流水：该路径对「已登录」放行，必须由 Service 层资源归属拒绝
        assertEquals(403, status(HttpMethod.GET, "/api/member/" + memberBId + "/recharge-records", tokenA),
                "会员读取他人充值流水应 403");
        // 4) 他人积分流水：同上
        assertEquals(403, status(HttpMethod.GET, "/api/member/" + memberBId + "/points-records", tokenA),
                "会员读取他人积分流水应 403");
    }

    /* ==================== TEST-P0-4-08 ==================== */

    @Test
    @DisplayName("TEST-P0-4-08 会员A 操作自己的允许接口应正常")
    void memberACanUseOwnSelfServiceEndpoints() {
        // 自己的充值流水 / 积分流水：放行
        assertEquals(200, status(HttpMethod.GET, "/api/member/" + memberAId + "/recharge-records", tokenA),
                "会员读取本人充值流水应放行");
        assertEquals(200, status(HttpMethod.GET, "/api/member/" + memberAId + "/points-records", tokenA),
                "会员读取本人积分流水应放行");

        // 自助修改允许字段：放行
        ResponseEntity<String> putResp = call(HttpMethod.PUT, "/api/member/" + memberAId, tokenA,
                Map.of("realName", "会员A新名"));
        assertEquals(0, codeOf(putResp), "会员修改本人允许字段应成功: " + putResp.getBody());
        assertEquals("会员A新名", row(PHONE_A).get("real_name"), "自助改名未生效");

        // 自助修改受控字段（手机号）：拒绝
        assertEquals(403, status(HttpMethod.PUT, "/api/member/" + memberAId, tokenA,
                Map.of("phone", "13911111111")), "会员自助修改手机号应被拒绝");
        assertEquals(PHONE_A, row(PHONE_A).get("phone"), "A 的手机号被改动");

        // 给自己充值：放行（会员账务自助写入路径）
        ResponseEntity<String> rechargeResp = call(HttpMethod.POST, "/api/member/recharge", tokenA,
                Map.of("memberId", memberAId, "amount", 10, "paymentMethod", 1));
        assertEquals(0, codeOf(rechargeResp), "会员给本人充值应成功: " + rechargeResp.getBody());

        // 给别人充值：拒绝（资源归属）
        assertEquals(403, status(HttpMethod.POST, "/api/member/recharge", tokenA,
                Map.of("memberId", memberBId, "amount", 10, "paymentMethod", 1)),
                "会员给他人充值应被拒绝");
    }

    /* ==================== TEST-P0-4-09 ==================== */

    @Test
    @DisplayName("TEST-P0-4-09 ADMIN 访问管理接口应正常")
    void adminCanAccessManagementEndpoints() {
        for (String url : List.of("/api/system/users", "/api/member/list",
                "/api/report/dashboard", "/api/cashier/orders")) {
            assertEquals(200, status(HttpMethod.GET, url, tokenAdmin), url + " 管理员应可访问");
        }
        // 管理员可管理他人（改名成功）
        ResponseEntity<String> resp = call(HttpMethod.PUT, "/api/member/" + memberBId, tokenAdmin,
                Map.of("realName", "管理员改名"));
        assertEquals(0, codeOf(resp), "管理员改名应成功: " + resp.getBody());
        assertEquals("管理员改名", row(PHONE_B).get("real_name"), "管理员改名未生效");
    }

    /* ==================== TEST-P0-4-10 ==================== */

    @Test
    @DisplayName("TEST-P0-4-10 CASHIER 可访问收银接口，但不可访问 ADMIN 专属接口")
    void cashierCanUseCashierButNotAdminOnly() {
        assertEquals(200, status(HttpMethod.GET, "/api/cashier/orders", tokenCashier),
                "收银员应可访问收银接口");
        assertEquals(200, status(HttpMethod.GET, "/api/member/list", tokenCashier),
                "收银员应可查看会员名册（member:query）");

        // ADMIN 专属：系统管理
        assertEquals(403, status(HttpMethod.GET, "/api/system/users", tokenCashier),
                "收银员访问系统管理应 403");

        // 会员管理动作：收银员无 member:update / member:freeze
        assertEquals(403, status(HttpMethod.DELETE, "/api/member/" + memberBId, tokenCashier),
                "收银员注销会员应 403");
        assertNull(row(PHONE_B).get("deleted_at"), "B 被误置为软删除");
        assertEquals(403, status(HttpMethod.PUT, "/api/member/" + memberBId, tokenCashier,
                Map.of("realName", "收银员改名")), "收银员修改会员应 403");
        assertEquals(ORIGINAL_B_NAME, row(PHONE_B).get("real_name"), "B 的姓名被收银员覆盖");
    }

    /* ==================== 细粒度角色边界（结合实际业务语义） ==================== */

    @Test
    @DisplayName("细化角色边界：退款/日结确认/强制下机/机位维护/第三方集成 按业务语义限定角色")
    void refinedRoleBoundaries() throws Exception {
        String tokenNetAdmin = employeeToken(STAFF_NET_ADMIN);

        // 收银员：不得退款、不得确认日结、不得强制下机、不得访问第三方集成配置
        assertEquals(403, status(HttpMethod.POST, "/api/cashier/orders/1/refund", tokenCashier, Map.of()),
                "收银员退款应 403（主管动作）");
        assertEquals(403, status(HttpMethod.POST, "/api/cashier/settlement/1/confirm", tokenCashier, Map.of()),
                "收银员确认日结应 403（主管动作）");
        assertEquals(403, status(HttpMethod.POST, "/api/session/force-end", tokenCashier, Map.of()),
                "收银员强制下机应 403（Controller 注释即标明管理员操作）");
        assertEquals(403, status(HttpMethod.GET, "/api/marketing/integration/configs", tokenCashier),
                "收银员访问第三方集成应 403");

        // 管理员：上述动作放行（业务可能因参数失败，但不得是 403）
        assertNotEquals(403, status(HttpMethod.GET, "/api/marketing/integration/configs", tokenAdmin),
                "管理员应可访问第三方集成配置");
        assertNotEquals(403, status(HttpMethod.POST, "/api/session/force-end", tokenAdmin, Map.of()),
                "管理员应可通过强制下机的授权检查");

        // 网管：设备/区域维护放行；强制下机与收银域拒绝
        assertNotEquals(403, status(HttpMethod.POST, "/api/session/areas", tokenNetAdmin, Map.of()),
                "网管应可通过区域维护的授权检查");
        assertEquals(403, status(HttpMethod.POST, "/api/session/force-end", tokenNetAdmin, Map.of()),
                "网管强制下机应 403");
        assertEquals(403, status(HttpMethod.GET, "/api/cashier/orders", tokenNetAdmin),
                "网管访问收银域应 403");

        // 会员：上机/收银/集成域全部拒绝
        assertEquals(403, status(HttpMethod.POST, "/api/session/force-end", tokenA, Map.of()),
                "会员强制下机应 403");
        assertEquals(403, status(HttpMethod.POST, "/api/marketing/coupons/1/redeem", tokenA, Map.of()),
                "会员券核销应 403");
    }

    /* ==================== 数据完整性 ==================== */

    @Test
    @DisplayName("数据完整性：种子数据未被破坏，测试数据零残留")
    void seedIntactAndNoLeftovers() {
        assertNotNull(tokenAdmin, "超级管理员 token 获取失败");
        assertNotNull(tokenCashier, "收银员 token 获取失败");

        List<Map<String, Object>> dups =
                jdbc.queryForList("SELECT phone, COUNT(*) c FROM member GROUP BY phone HAVING c > 1");
        assertTrue(dups.isEmpty(), "存在重复手机号: " + dups);

        List<Map<String, Object>> seed =
                jdbc.queryForList("SELECT status, deleted_at FROM member WHERE phone = ?", "13811001001");
        assertEquals(1, seed.size(), "种子会员 13811001001 丢失");
        assertNull(seed.get(0).get("deleted_at"), "种子会员被误置为软删除");
    }
}
