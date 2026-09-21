package com.starlink;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * P0-01 安全回归测试：已注销（软删除）会员手机号不得被匿名注册接口恢复/接管。
 * <p>
 * 漏洞原状：{@code POST /api/member/register}（permitAll）命中一条已软删除的 member 记录时，
 * 会走 reviveMember → reviveDeletedMember，用请求方提供的 password/realName/idCard
 * 覆盖原账号，并把 balance / total_points / total_consumption 清零。
 * 攻击者仅需知道手机号即可接管账号并清空资产。
 * <p>
 * 本测试覆盖 TEST-P0-01 ~ TEST-P0-05。所有断言均回读数据库，
 * 不依赖接口返回文本（接口"看起来成功"不等于数据正确）。
 * <p>
 * 测试数据使用专用手机号 13800000001~03（与 Insert.sql 种子数据的 1381100100x 不冲突），
 * 每个用例前后都会按手机号硬删除，绝不触碰正式种子数据。
 */
@SpringBootTest
@AutoConfigureMockMvc
// dev 提供数据源，"test" 提供测试专用 JWT 密钥（见 src/test/resources/application-test.yml）
@ActiveProfiles({"dev", "test"})
class MemberRegisterSecurityTest {

    /** 已存在且状态正常的会员 */
    private static final String PHONE_ACTIVE = "13800000001";
    /** 已注销（软删除）会员 —— 本次 P0 的攻击目标 */
    private static final String PHONE_DELETED = "13800000002";
    /** 全新手机号 —— 正常注册路径 */
    private static final String PHONE_FRESH = "13800000003";

    private static final String ORIGINAL_PWD = "Orig@123456";
    private static final String ATTACKER_PWD = "Attacker@999";

    private static final String ORIGINAL_NAME = "原账号主人";
    private static final String ORIGINAL_ID_CARD = "110101199003071234";
    private static final String ATTACKER_NAME = "攻击者";
    private static final String ATTACKER_ID_CARD = "999999199901019999";

    private static final BigDecimal SEED_BALANCE = new BigDecimal("100.00");
    private static final long SEED_POINTS = 500L;
    private static final BigDecimal SEED_CONSUMPTION = new BigDecimal("1000.00");

    private static final List<String> TEST_PHONES = List.of(PHONE_ACTIVE, PHONE_DELETED, PHONE_FRESH);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    /* ==================== 夹具 ==================== */

    @BeforeEach
    void seedFixtures() {
        hardDeleteTestMembers();

        // 1) 状态正常的会员：余额 100 / 积分 500 / 累计消费 1000
        insertMember(PHONE_ACTIVE, "TESTP0A001", ORIGINAL_PWD, (byte) 1, null);

        // 2) 已注销（软删除）会员：同样的资产，但 status=4 且 deleted_at 有值
        insertMember(PHONE_DELETED, "TESTP0D002", ORIGINAL_PWD, (byte) 4, "NOW(3)");
    }

    @AfterEach
    void cleanUp() {
        hardDeleteTestMembers();
        // 清理证明：测试结束后不得残留任何测试手机号记录（含软删除行）
        for (String phone : TEST_PHONES) {
            assertEquals(0, rowCount(phone),
                    "测试数据未清理干净：" + phone + " 仍有残留记录");
        }
    }

    /**
     * 硬删除测试手机号（绕过 @TableLogic），确保含软删除行在内一并清理干净。
     */
    private void hardDeleteTestMembers() {
        for (String phone : TEST_PHONES) {
            jdbc.update("DELETE FROM member WHERE phone = ?", phone);
        }
    }

    private void insertMember(String phone, String memberNo, String rawPassword, byte status, String deletedAtExpr) {
        String sql = "INSERT INTO member "
                + "(member_no, phone, password_hash, real_name, id_card, level_id, "
                + " total_points, available_points, total_recharge, balance, total_consumption, "
                + " register_source, status, deleted_at) "
                + "VALUES (?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?, 1, ?, "
                + (deletedAtExpr == null ? "NULL" : deletedAtExpr) + ")";
        jdbc.update(sql,
                memberNo, phone, passwordEncoder.encode(rawPassword), ORIGINAL_NAME, ORIGINAL_ID_CARD,
                SEED_POINTS, SEED_POINTS, SEED_CONSUMPTION, SEED_BALANCE, SEED_CONSUMPTION,
                status);
    }

    /** 读取该手机号的原始行（含软删除行；裸 SQL 不受 @TableLogic 影响）。 */
    private Map<String, Object> row(String phone) {
        List<Map<String, Object>> rows =
                jdbc.queryForList("SELECT * FROM member WHERE phone = ?", phone);
        assertEquals(1, rows.size(), "手机号 " + phone + " 应恰好存在 1 行会员记录");
        return rows.get(0);
    }

    private int rowCount(String phone) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM member WHERE phone = ?", Integer.class, phone);
        return n == null ? 0 : n;
    }

    /**
     * 发起匿名注册请求，返回响应体的 code 字段。
     * <p>
     * 注意：payload 必须是**完整合法**的（含 gender）。若省略 gender，漏洞版
     * reviveDeletedMember 会执行 {@code gender = NULL} 撞上 {@code member.gender NOT NULL}
     * 约束而抛 500 并回滚，导致"数据未被改动"在漏洞依然存在时也成立 —— 测试将变成假绿。
     * （insert 路径容忍 null 是因为 MyBatis-Plus 默认字段策略会跳过 null 字段，手写 UPDATE 不会。）
     */
    private int register(String phone, String password, String realName, String idCard) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "phone", phone,
                "password", password,
                "realName", realName,
                "gender", 1,
                "idCard", idCard));

        String resp = mockMvc.perform(post("/api/member/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(resp).path("code").asInt();
    }

    /** 断言原账号的身份与资产未被覆盖/清零。 */
    private void assertAssetsAndIdentityUntouched(String phone, String expectedJsonPath) {
        Map<String, Object> r = row(phone);

        assertEquals(0, SEED_BALANCE.compareTo(new BigDecimal(r.get("balance").toString())),
                expectedJsonPath + ": balance 被改动");
        assertEquals(0, SEED_CONSUMPTION.compareTo(new BigDecimal(r.get("total_consumption").toString())),
                expectedJsonPath + ": total_consumption 被改动");
        assertEquals(SEED_POINTS, ((Number) r.get("total_points")).longValue(),
                expectedJsonPath + ": total_points 被改动");
        assertEquals(SEED_POINTS, ((Number) r.get("available_points")).longValue(),
                expectedJsonPath + ": available_points 被改动");
        assertEquals(ORIGINAL_NAME, r.get("real_name"), expectedJsonPath + ": real_name 被覆盖");
        assertEquals(ORIGINAL_ID_CARD, r.get("id_card"), expectedJsonPath + ": id_card 被覆盖");

        String storedHash = String.valueOf(r.get("password_hash"));
        assertTrue(passwordEncoder.matches(ORIGINAL_PWD, storedHash),
                expectedJsonPath + ": 原密码已失效（password_hash 被覆盖）");
        assertFalse(passwordEncoder.matches(ATTACKER_PWD, storedHash),
                expectedJsonPath + ": 攻击者密码已生效 —— 账号被接管");
    }

    /* ==================== TEST-P0-01 ==================== */

    @Test
    @DisplayName("TEST-P0-01 ACTIVE 会员手机号重复注册必须失败，且原数据完全不变")
    void activeMemberPhoneCannotBeReRegistered() throws Exception {
        int code = register(PHONE_ACTIVE, ATTACKER_PWD, ATTACKER_NAME, ATTACKER_ID_CARD);

        assertNotEquals(0, code, "已注册手机号重复注册应失败");

        Map<String, Object> r = row(PHONE_ACTIVE);
        assertNull(r.get("deleted_at"), "ACTIVE 会员不应被置为软删除");
        assertEquals(1, ((Number) r.get("status")).intValue(), "ACTIVE 会员状态不应被改动");
        assertEquals(1, rowCount(PHONE_ACTIVE), "不应因重复注册而产生第二行记录");
        assertAssetsAndIdentityUntouched(PHONE_ACTIVE, "TEST-P0-01");
    }

    /* ==================== TEST-P0-02 ==================== */

    @Test
    @DisplayName("TEST-P0-02 DELETED 会员手机号不能被匿名注册恢复/接管，资产不得清零")
    void deletedMemberPhoneCannotBeHijackedByRegister() throws Exception {
        // 攻击前先确认夹具处于"已注销"状态
        Map<String, Object> before = row(PHONE_DELETED);
        assertNotNull(before.get("deleted_at"), "夹具应为已注销（deleted_at 非空）");
        assertEquals(4, ((Number) before.get("status")).intValue(), "夹具状态应为 4-已注销");

        int code = register(PHONE_DELETED, ATTACKER_PWD, ATTACKER_NAME, ATTACKER_ID_CARD);

        assertNotEquals(0, code, "DELETED 会员手机号注册必须被拒绝");

        Map<String, Object> after = row(PHONE_DELETED);

        // 1) 账号未被恢复
        assertNotNull(after.get("deleted_at"), "账号不得被解除软删除（revive 被阻止）");
        assertEquals(4, ((Number) after.get("status")).intValue(), "状态不得被改回正常");

        // 2) 身份未被覆盖
        // 3) 余额 / 积分 / 消费未被清零
        assertAssetsAndIdentityUntouched(PHONE_DELETED, "TEST-P0-02");

        // 4) 未新建第二条记录
        assertEquals(1, rowCount(PHONE_DELETED), "不得为同一手机号再插入一行");
    }

    /* ==================== TEST-P0-03 ==================== */

    @Test
    @DisplayName("TEST-P0-03 全新手机号应能正常注册")
    void freshPhoneCanRegisterNormally() throws Exception {
        int code = register(PHONE_FRESH, "Fresh@123456", "新会员", "110101200001011234");

        assertEquals(0, code, "全新手机号注册应成功");

        Map<String, Object> r = row(PHONE_FRESH);
        assertNull(r.get("deleted_at"), "新会员不应是软删除状态");
        assertEquals(1, ((Number) r.get("status")).intValue(), "新会员状态应为 1-正常");
        assertEquals(0, BigDecimal.ZERO.compareTo(new BigDecimal(r.get("balance").toString())),
                "新会员余额应为 0");
        assertEquals(0L, ((Number) r.get("total_points")).longValue(), "新会员积分应为 0");
        assertEquals(0, BigDecimal.ZERO.compareTo(new BigDecimal(r.get("total_consumption").toString())),
                "新会员累计消费应为 0");
        assertTrue(passwordEncoder.matches("Fresh@123456", String.valueOf(r.get("password_hash"))),
                "新会员密码应为注册时提交的密码");
    }

    /* ==================== TEST-P0-04 ==================== */

    @Test
    @DisplayName("TEST-P0-04 ACTIVE 会员重复注册必须失败且不产生新记录")
    void duplicateActiveRegistrationIsRejected() throws Exception {
        int before = rowCount(PHONE_ACTIVE);

        int code = register(PHONE_ACTIVE, ATTACKER_PWD, ATTACKER_NAME, ATTACKER_ID_CARD);

        assertNotEquals(0, code, "重复注册应失败");
        assertEquals(before, rowCount(PHONE_ACTIVE), "记录数不应变化");
        assertAssetsAndIdentityUntouched(PHONE_ACTIVE, "TEST-P0-04");
    }

    /* ==================== TEST-P0-05 ==================== */

    @Test
    @DisplayName("TEST-P0-05 不存在合法的开放恢复接口：register 不恢复，且无匿名 restore 端点")
    void noOpenRestoreApiExists() throws Exception {
        // 1) 普通 register 不允许恢复 DELETED 会员（与 TEST-P0-02 互为印证）
        register(PHONE_DELETED, ATTACKER_PWD, ATTACKER_NAME, ATTACKER_ID_CARD);
        assertNotNull(row(PHONE_DELETED).get("deleted_at"),
                "普通 register 不得恢复 DELETED 会员");

        // 2) 已注销会员无法登录（软删除会员在 selectByPhone 中不可见）
        String loginBody = objectMapper.writeValueAsString(
                Map.of("phone", PHONE_DELETED, "password", ORIGINAL_PWD));
        String loginResp = mockMvc.perform(post("/api/member/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andReturn().getResponse().getContentAsString();
        assertNotEquals(0, objectMapper.readTree(loginResp).path("code").asInt(),
                "已注销会员不应能登录");

        // 3) 文档 doc/Front_Back-API.md §6.4 预留的 PATCH /api/member/{id}/restore 尚未实现，
        //    且当前没有任何匿名可用的恢复入口：该路径不得返回业务成功。
        String restoreResp = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .patch("/api/member/" + row(PHONE_DELETED).get("id") + "/restore"))
                .andReturn().getResponse().getContentAsString();
        int restoreCode = restoreResp.isBlank()
                ? -1
                : objectMapper.readTree(restoreResp).path("code").asInt(-1);
        assertNotEquals(0, restoreCode,
                "当前不应存在任何可用的账号恢复接口（本次修复刻意不提供匿名恢复能力）");

        // 4) 账号仍处于注销状态，资产完好
        assertAssetsAndIdentityUntouched(PHONE_DELETED, "TEST-P0-05");
    }

    /* ==================== 数据完整性（评审要求 §11：不能只看接口返回） ==================== */

    @Test
    @DisplayName("数据完整性：无重复手机号，且种子会员未被本轮测试破坏")
    void seedDataIntactAndNoDuplicatePhones() {
        // 1) 全表不得存在重复手机号（uk_phone 的业务语义校验）
        List<Map<String, Object>> dups =
                jdbc.queryForList("SELECT phone, COUNT(*) c FROM member GROUP BY phone HAVING c > 1");
        assertTrue(dups.isEmpty(), "存在重复手机号: " + dups);

        // 2) 种子会员（Insert.sql 中的 13811001001 陈浩宇）必须仍然存在、未被软删除、状态正常
        List<Map<String, Object>> seed =
                jdbc.queryForList("SELECT status, deleted_at FROM member WHERE phone = ?", "13811001001");
        assertEquals(1, seed.size(), "种子会员 13811001001 丢失");
        assertNull(seed.get(0).get("deleted_at"), "种子会员被误置为软删除");
        assertEquals(1, ((Number) seed.get(0).get("status")).intValue(), "种子会员状态被误改");

        // 注：测试手机号的"零残留"由 @AfterEach 在每次用例清理后断言，此处不重复校验
        //（@BeforeEach 会为每个用例植入夹具，在用例内部断言残留为 0 会与之矛盾）。
    }
}
