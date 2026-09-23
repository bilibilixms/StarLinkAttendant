package com.starlink;

import com.starlink.common.constant.CommonConstants;
import com.starlink.common.exception.BusinessException;
import com.starlink.member.dto.req.RechargeRequest;
import com.starlink.member.service.BalanceService;
import com.starlink.member.service.RechargeService;
import com.starlink.system.security.SecurityUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 充值 / 余额 / 余额流水 一致性与幂等测试（<b>真实 MySQL 开发库</b>）。
 * <p>
 * 覆盖：正常充值（阶梯规则）、重复充值、并发充值（同键 / 不同键）、余额流水不变量、
 * 事务回滚、无幂等键退化、黑名单拒绝。
 * <p>
 * 安全性：全部数据使用 TEST 前缀（member_no/phone/campaign_no），用例前后硬删除；
 * <b>不触碰</b>真实会员与其 8 条真实充值记录。
 */
@SpringBootTest
@ActiveProfiles({"dev", "test"})
class RechargeConsistencyTest {

    private static final String TEST_PHONE = "13920000001";
    private static final String TEST_MEMBER_NO = "TESTP1R001";
    private static final String TEST_CAMPAIGN_NO = "TESTP1C001";
    /** 钻石会员：points_multiple = 3.00 */
    private static final long DIAMOND_LEVEL_ID = 4L;

    /** 测试用充值阶梯（与种子活动同构的比例式 tiers） */
    private static final String TIERS =
            "{\"type\":\"recharge_bonus\",\"tiers\":["
                    + "{\"min\":200,\"bonus_rate\":5},"
                    + "{\"min\":500,\"bonus_rate\":10},"
                    + "{\"min\":1000,\"bonus_rate\":15}]}";

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RechargeService rechargeService;
    @Autowired
    private BalanceService balanceService;

    private Long memberId;
    private Long campaignId;

    /* ==================== 夹具 ==================== */

    @BeforeEach
    void setUp() {
        cleanUp();
        campaignId = insertTestCampaign();
        memberId = insertTestMember(0L, 0L);
        authenticateAsMember(memberId);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        cleanUp();
    }

    private void cleanUp() {
        List<Long> ids = jdbc.queryForList(
                "SELECT id FROM member WHERE member_no LIKE 'TESTP1%'", Long.class);
        for (Long id : ids) {
            jdbc.update("DELETE FROM member_balance_log WHERE member_id = ?", id);
            jdbc.update("DELETE FROM member_points_log WHERE member_id = ?", id);
            jdbc.update("DELETE FROM member_recharge WHERE member_id = ?", id);
        }
        jdbc.update("DELETE FROM member WHERE member_no LIKE 'TESTP1%'");
        jdbc.update("DELETE FROM member WHERE phone = ?", TEST_PHONE);
        jdbc.update("DELETE FROM campaign WHERE campaign_no LIKE 'TESTP1%'");
    }

    private Long insertTestCampaign() {
        jdbc.update("INSERT INTO campaign (campaign_no, campaign_name, campaign_type, start_time, end_time, "
                        + "rules, used_budget, used_count, member_limit, status) "
                        + "VALUES (?, ?, 1, '2000-01-01 00:00:00', '2099-12-31 23:59:59', ?, 0.00, 0, 1, 2)",
                TEST_CAMPAIGN_NO, "P1测试-充值赠送", TIERS);
        return jdbc.queryForObject("SELECT id FROM campaign WHERE campaign_no = ?", Long.class, TEST_CAMPAIGN_NO);
    }

    private Long insertTestMember(long balance, long points) {
        jdbc.update("INSERT INTO member (member_no, phone, password_hash, real_name, gender, level_id, "
                        + "total_points, available_points, total_recharge, balance, total_consumption, "
                        + "register_source, status) VALUES (?, ?, 'x', 'P1测试会员', 1, ?, ?, ?, 0.00, ?, 0.00, 1, 1)",
                TEST_MEMBER_NO, TEST_PHONE, DIAMOND_LEVEL_ID, points, points, BigDecimal.valueOf(balance));
        return jdbc.queryForObject("SELECT id FROM member WHERE member_no = ?", Long.class, TEST_MEMBER_NO);
    }

    /** 以会员身份放入 SecurityContext —— 充值服务有 P0-4 资源归属校验，不能绕过。 */
    private void authenticateAsMember(Long id) {
        SecurityUser user = new SecurityUser(id, TEST_PHONE, (byte) 1);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private RechargeRequest request(String amount, String idempotentKey) {
        RechargeRequest req = new RechargeRequest();
        req.setMemberId(memberId);
        req.setAmount(new BigDecimal(amount));
        req.setPaymentMethod((byte) 1); // 现金
        req.setIdempotentKey(idempotentKey);
        return req;
    }

    /* ==================== 读取辅助 ==================== */

    private BigDecimal balance() {
        return jdbc.queryForObject("SELECT balance FROM member WHERE id = ?", BigDecimal.class, memberId);
    }

    private long availablePoints() {
        Long v = jdbc.queryForObject("SELECT available_points FROM member WHERE id = ?", Long.class, memberId);
        return v == null ? 0L : v;
    }

    private BigDecimal totalRecharge() {
        return jdbc.queryForObject("SELECT total_recharge FROM member WHERE id = ?", BigDecimal.class, memberId);
    }

    private List<Map<String, Object>> recharges() {
        return jdbc.queryForList(
                "SELECT id, recharge_no, recharge_amount, bonus_amount, total_amount, balance_before, "
                        + "balance_after, campaign_id, status FROM member_recharge WHERE member_id = ? ORDER BY id",
                memberId);
    }

    private List<Map<String, Object>> ledger() {
        return jdbc.queryForList(
                "SELECT id, amount, balance_before, balance_after, biz_type, biz_id, remark "
                        + "FROM member_balance_log WHERE member_id = ? ORDER BY id",
                memberId);
    }

    private List<Map<String, Object>> pointsLogs() {
        return jdbc.queryForList(
                "SELECT id, points, balance_before, balance_after, biz_type, biz_id "
                        + "FROM member_points_log WHERE member_id = ? ORDER BY id",
                memberId);
    }

    private static BigDecimal dec(Object v) {
        return v == null ? null : new BigDecimal(String.valueOf(v));
    }

    /** 校验余额流水不变量：链式连续 + after = before + amount + 与会员余额一致 */
    private void assertLedgerInvariants() {
        List<Map<String, Object>> rows = ledger();
        BigDecimal prevAfter = null;
        for (Map<String, Object> r : rows) {
            BigDecimal amount = dec(r.get("amount"));
            BigDecimal before = dec(r.get("balance_before"));
            BigDecimal after = dec(r.get("balance_after"));
            assertEquals(0, after.compareTo(before.add(amount)),
                    "流水 " + r.get("id") + " 不满足 balance_after = balance_before + amount");
            if (prevAfter != null) {
                assertEquals(0, before.compareTo(prevAfter),
                        "流水 " + r.get("id") + " 的 balance_before 与上一条 balance_after 不连续");
            }
            prevAfter = after;
        }
        if (prevAfter != null) {
            assertEquals(0, prevAfter.compareTo(balance()),
                    "member.balance 应等于最新一条流水的 balance_after");
        }
    }

    /* ==================== 1. 正常充值：阶梯规则（核心回归） ==================== */

    @Test
    @DisplayName("充 100 未满首档 → 赠送 0、到账 100，并写流水与赠送积分（修复「100 却到账 115」）")
    void recharge100GetsNoBonus() {
        var resp = rechargeService.recharge(request("100", "K-100"));

        assertEquals(0, dec(resp.getRechargeAmount()).compareTo(new BigDecimal("100")));
        assertEquals(0, dec(resp.getBonusAmount()).compareTo(BigDecimal.ZERO), "100 未满 200 档，赠送必须为 0");
        assertEquals(0, dec(resp.getTotalAmount()).compareTo(new BigDecimal("100")), "到账应为 100 而非 115");

        List<Map<String, Object>> rs = recharges();
        assertEquals(1, rs.size());
        assertEquals(0, dec(rs.get(0).get("bonus_amount")).compareTo(BigDecimal.ZERO));
        assertNull(rs.get(0).get("campaign_id"), "未命中档位时不应记录活动 ID");

        assertEquals(0, balance().compareTo(new BigDecimal("100")), "余额应增加 100");
        assertEquals(0, totalRecharge().compareTo(new BigDecimal("100")), "累计充值按实付累计");

        List<Map<String, Object>> ls = ledger();
        assertEquals(1, ls.size(), "每次充值必须有一条余额流水");
        assertEquals(0, dec(ls.get(0).get("amount")).compareTo(new BigDecimal("100")));
        assertEquals(CommonConstants.BALANCE_BIZ_RECHARGE, ((Number) ls.get(0).get("biz_type")).byteValue());
        assertEquals(((Number) rs.get(0).get("id")).longValue(),
                ((Number) ls.get(0).get("biz_id")).longValue(), "流水 biz_id 应指向充值单");
        assertLedgerInvariants();

        // 充值赠送积分 = 100 × 钻石倍数 3.00
        List<Map<String, Object>> pl = pointsLogs();
        assertEquals(1, pl.size());
        assertEquals(300, ((Number) pl.get(0).get("points")).intValue());
        assertEquals(CommonConstants.POINTS_BIZ_RECHARGE_GIFT, ((Number) pl.get(0).get("biz_type")).byteValue());
        assertEquals(300L, availablePoints());
    }

    @Test
    @DisplayName("充 500 命中 10% 档 → 赠送 50、到账 550，并记录活动 ID")
    void recharge500HitsSecondTier() {
        var resp = rechargeService.recharge(request("500", "K-500"));

        assertEquals(0, dec(resp.getBonusAmount()).compareTo(new BigDecimal("50")));
        assertEquals(0, dec(resp.getTotalAmount()).compareTo(new BigDecimal("550")));
        assertEquals(0, balance().compareTo(new BigDecimal("550")));
        assertEquals(0, totalRecharge().compareTo(new BigDecimal("500")), "累计充值不含赠送");
        assertEquals(campaignId, ((Number) recharges().get(0).get("campaign_id")).longValue(),
                "命中档位时应记录来源活动");
        assertEquals(1500L, availablePoints(), "500 × 3.00 倍数");
        assertLedgerInvariants();
    }

    /* ==================== 2. 余额流水跨操作一致性 ==================== */

    @Test
    @DisplayName("充值 + 余额扣减 + 退款回充 后，流水链连续且与会员余额一致")
    void ledgerInvariantsAcrossOperations() {
        rechargeService.recharge(request("200", "K-LEDGER"));          // +210 (200×5%)
        balanceService.deductBalance(memberId, new BigDecimal("50"), (byte) CommonConstants.BALANCE_BIZ_CONSUME,
                9001L, "测试消费");
        balanceService.addBalance(memberId, new BigDecimal("20"), (byte) CommonConstants.BALANCE_BIZ_REFUND,
                9002L, "测试退款回充");

        List<Map<String, Object>> ls = ledger();
        assertEquals(3, ls.size(), "三次余额变动应产生三条流水");
        assertEquals((byte) CommonConstants.BALANCE_BIZ_RECHARGE, ((Number) ls.get(0).get("biz_type")).byteValue());
        assertEquals((byte) CommonConstants.BALANCE_BIZ_CONSUME, ((Number) ls.get(1).get("biz_type")).byteValue());
        assertEquals((byte) CommonConstants.BALANCE_BIZ_REFUND, ((Number) ls.get(2).get("biz_type")).byteValue());
        assertEquals(0, dec(ls.get(1).get("amount")).compareTo(new BigDecimal("-50")), "扣减流水应为负数");
        assertLedgerInvariants();
        assertEquals(0, balance().compareTo(new BigDecimal("180")), "210 - 50 + 20 = 180");
    }

    /* ==================== 3. 重复充值（幂等） ==================== */

    @Test
    @DisplayName("同一幂等键串行重复提交 → 只入账一次，不重复写充值单/流水/积分")
    void repeatedSameKeyIsIdempotent() {
        var first = rechargeService.recharge(request("100", "K-IDEMPOTENT"));
        var second = rechargeService.recharge(request("100", "K-IDEMPOTENT"));

        assertEquals(first.getId(), second.getId(), "重复提交应返回同一充值单");
        assertEquals(first.getRechargeNo(), second.getRechargeNo());

        assertEquals(1, recharges().size(), "不得产生第二条充值单");
        assertEquals(1, ledger().size(), "不得重复写余额流水");
        assertEquals(1, pointsLogs().size(), "不得重复写积分流水");
        assertEquals(0, balance().compareTo(new BigDecimal("100")), "余额只能增加一次");
        assertLedgerInvariants();
    }

    /* ==================== 4. 并发充值 ==================== */

    @Test
    @DisplayName("并发同一幂等键 → 只入账一次")
    void concurrentSameKeyCreditsOnce() throws Exception {
        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    authenticateAsMember(memberId); // SecurityContext 是线程局部的
                    start.await();
                    try {
                        return rechargeService.recharge(request("100", "K-CONC-SAME"));
                    } catch (Exception e) {
                        return e;
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                }));
            }
            start.countDown();
            int success = 0;
            int failures = 0;
            for (Future<Object> f : futures) {
                Object r = f.get(30, TimeUnit.SECONDS);
                if (r instanceof BusinessException) {
                    failures++;
                } else {
                    success++;
                }
            }
            assertTrue(success >= 1, "至少应有一次成功");
            assertEquals(threads, success + failures);
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, recharges().size(), "同一幂等键并发只应落一条充值单");
        assertEquals(1, ledger().size(), "同一幂等键并发只应有一条余额流水");
        assertEquals(0, balance().compareTo(new BigDecimal("100")), "余额只能增加一次");
        assertEquals(300L, availablePoints(), "积分只能赠送一次");
        assertLedgerInvariants();
    }

    @Test
    @DisplayName("并发不同幂等键（合法两笔）→ 都成功、无丢更新、流水链连续")
    void concurrentDifferentKeysDoNotLoseUpdates() throws Exception {
        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        List<String> errors = new CopyOnWriteArrayList<>();
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                final String key = "K-CONC-" + i;
                futures.add(pool.submit(() -> {
                    authenticateAsMember(memberId);
                    try {
                        start.await();
                        rechargeService.recharge(request("100", key));
                        success.incrementAndGet();
                    } catch (Exception e) {
                        // 如实记录失败原因（不吞掉），失败时断言信息会带上它，便于定位
                        errors.add(e.getClass().getSimpleName() + ": " + e.getMessage());
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                }));
            }
            start.countDown();
            for (Future<?> f : futures) {
                f.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(2, success.get(),
                "两笔不同幂等键的充值都应成功（行锁串行化，不应出现冲突假失败）；实际失败: " + errors);
        assertEquals(2, recharges().size());
        assertEquals(2, ledger().size());
        assertEquals(0, balance().compareTo(new BigDecimal("200")), "两笔 100 应累计为 200，无丢更新");
        assertEquals(600L, availablePoints());
        assertLedgerInvariants();
    }

    /* ==================== 5. 事务回滚 ==================== */

    @Test
    @DisplayName("事务中途失败 → 充值单/余额/流水/积分全部回滚，不留半套数据")
    void failureRollsBackAllWrites() {
        // 把积分改到接近 INT 上限，使「充值赠送积分」在写积分流水时越界失败。
        // 用 UPDATE 而非「删会员再重建」：避免在会员仍有子表数据时先删父行。
        jdbc.update("UPDATE member SET available_points = 2147483600, total_points = 2147483600 WHERE id = ?",
                memberId);

        assertThrows(BusinessException.class, () -> rechargeService.recharge(request("100", "K-ROLLBACK")),
                "积分越界应导致整笔充值失败");

        assertEquals(0, recharges().size(), "回滚后不应留下充值单");
        assertEquals(0, ledger().size(), "回滚后不应留下余额流水");
        assertEquals(0, pointsLogs().size(), "回滚后不应留下积分流水");
        assertEquals(0, balance().compareTo(BigDecimal.ZERO), "回滚后余额不应变化");
        assertEquals(2147483600L, availablePoints(), "回滚后积分不应变化");
    }

    /* ==================== 6. 边界与降级 ==================== */

    @Test
    @DisplayName("未携带幂等键 → 退化为两笔独立充值（如实记录该限制）")
    void missingIdempotentKeyAllowsTwoRecharges() {
        rechargeService.recharge(request("100", null));
        rechargeService.recharge(request("100", null));

        assertEquals(2, recharges().size(), "无幂等键时应产生两笔（无幂等保护）");
        assertEquals(0, balance().compareTo(new BigDecimal("200")));
        assertLedgerInvariants();
    }

    @Test
    @DisplayName("黑名单会员充值被拒绝且不留任何数据")
    void blacklistedMemberRejected() {
        jdbc.update("UPDATE member SET status = 3 WHERE id = ?", memberId);

        assertThrows(BusinessException.class, () -> rechargeService.recharge(request("100", "K-BLACK")));

        assertEquals(0, recharges().size());
        assertEquals(0, ledger().size());
        assertEquals(0, balance().compareTo(BigDecimal.ZERO));
    }
}
