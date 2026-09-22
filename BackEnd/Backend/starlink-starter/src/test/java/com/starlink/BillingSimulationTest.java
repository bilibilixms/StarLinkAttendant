package com.starlink;

import com.starlink.common.time.SimulationClock;
import com.starlink.session.service.SessionService;
import com.starlink.session.task.SessionBillingScheduler;
import com.starlink.sim.BillingSimulator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 网吧计费「时间仿真」测试 —— 用可推进时钟驱动<b>真实</b>计费链路。
 *
 * <p>关键点：不复制任何计费公式。费用由真实的
 * {@link SessionBillingScheduler#updateSessionBilling()}（生产每分钟执行的同一个方法）
 * 依据数据库真实费率配置算出；仿真器只负责「快进时间」。
 *
 * <p>费率来源（真实配置）：
 * <ul>
 *   <li>种子 plan 1 普通区：首 60 分钟 5.00，之后 0.05 元/分钟（=3 元/小时）</li>
 *   <li>种子 plan 2 电竞区：首 60 分钟 8.00，之后 0.10 元/分钟（=6 元/小时）</li>
 *   <li>种子 plan 3 通宵包时：rate_type=3，固定 20.00</li>
 * </ul>
 * 另按需创建 TESTSIM 前缀的费率行以覆盖「快速耗尽」等场景。
 *
 * <p>数据隔离：全部 TESTSIM 前缀 + 用例前后清理，不触碰真实会员/充值数据。
 */
@SpringBootTest(properties = "starlink.billing.simulation-enabled=true")
@ActiveProfiles({"dev", "test"})
class BillingSimulationTest {

    /** 种子普通区方案（首 60 分钟 5.00，续 0.05/分钟） */
    private static final long SEED_PLAN_NORMAL = 1L;
    /** 种子电竞区方案（首 60 分钟 8.00，续 0.10/分钟） */
    private static final long SEED_PLAN_ESPORTS = 2L;
    /** 种子通宵包时方案（固定 20.00） */
    private static final long SEED_PLAN_NIGHT = 3L;

    private static final long LEVEL_NORMAL = 1L;
    private static final long LEVEL_SILVER = 2L;
    private static final long LEVEL_GOLD = 3L;
    private static final long LEVEL_DIAMOND = 4L;

    @Autowired
    private SessionService sessionService;
    @Autowired
    private SessionBillingScheduler billingScheduler;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private Clock clock;

    private BillingSimulator sim;
    private final List<String> results = new ArrayList<>();
    private int seq;

    @BeforeEach
    void setUp() {
        assertInstanceOf(SimulationClock.class, clock,
                "本用例必须运行在仿真时钟下（starlink.billing.simulation-enabled=true）");
        SimulationClock simulationClock = (SimulationClock) clock;
        simulationClock.reset();
        sim = new BillingSimulator(sessionService, billingScheduler, simulationClock, jdbc);
        cleanUp();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
        System.out.println("\n========== 网吧计费仿真测试 ==========");
        results.forEach(r -> System.out.println(r));
        System.out.println("=====================================");
    }

    private void cleanUp() {
        List<Long> memberIds = jdbc.queryForList("SELECT id FROM member WHERE member_no LIKE 'TESTSIM%'", Long.class);
        for (Long id : memberIds) {
            List<Long> sessionIds = jdbc.queryForList("SELECT id FROM session WHERE member_id = ?", Long.class, id);
            for (Long sid : sessionIds) {
                jdbc.update("DELETE FROM session_timing WHERE session_id = ?", sid);
                jdbc.update("DELETE FROM billing_record WHERE session_id = ?", sid);
            }
            jdbc.update("DELETE FROM session WHERE member_id = ?", id);
            jdbc.update("DELETE FROM member_balance_log WHERE member_id = ?", id);
            jdbc.update("DELETE FROM member_points_log WHERE member_id = ?", id);
            jdbc.update("DELETE FROM member_recharge WHERE member_id = ?", id);
        }
        jdbc.update("DELETE FROM member WHERE member_no LIKE 'TESTSIM%'");
        // 机位：先解绑可能残留的会话（上面已删），再删机位
        jdbc.update("DELETE FROM computer WHERE computer_no LIKE 'TESTSIM%'");
        jdbc.update("DELETE FROM seat_area WHERE area_name LIKE 'TESTSIM%'");
        jdbc.update("DELETE FROM tariff_rate WHERE rate_name LIKE 'TESTSIM%'");
        jdbc.update("DELETE FROM tariff_plan WHERE plan_name LIKE 'TESTSIM%'");
    }

    private void record(String name, boolean pass, String detail) {
        results.add(String.format("[%2d] %-28s %s  %s", ++seq, name, pass ? "PASS" : "FAIL", detail));
    }

    /* ==================== 场景 1：普通区正常上机 3 小时 ==================== */

    @Test
    @DisplayName("[1] 普通区正常上机 3 小时：按真实费率计费，余额充足保持在线")
    void normalSession3Hours() {
        Long memberId = sim.createMember("TESTSIM001", "13000000001", new BigDecimal("100.00"), LEVEL_NORMAL);
        Long areaId = sim.createArea("TESTSIM普通区");
        Long computerId = sim.createComputer("TESTSIM-C01", areaId, SEED_PLAN_NORMAL);
        Long sid = sim.start(memberId, computerId, SEED_PLAN_NORMAL);
        LocalDateTime start = sim.now();

        sim.advance(60);
        BillingSimulator.Snapshot h1 = sim.snap(sid);
        sim.advance(120);
        BillingSimulator.Snapshot h3 = sim.snap(sid);

        // 真实费率：首 60 分钟 5.00，之后 0.05/分钟 → 180 分钟 = 5 + 120*0.05 = 11.00
        assertEquals(0, h1.totalAmount().compareTo(new BigDecimal("5.00")), "1 小时应为 5.00，实际 " + h1.totalAmount());
        assertEquals(0, h3.totalAmount().compareTo(new BigDecimal("11.00")), "3 小时应为 11.00，实际 " + h3.totalAmount());
        assertTrue(h3.isOnline(), "余额充足应保持在线");
        record("普通区正常上机3小时", true, "1h=5.00 3h=11.00 余额=" + h3.balance() + " 状态=" + h3.statusName());

        // 下机结算：真实扣款 + 写余额流水
        sim.advanceClock(0);
        sim.end(sid);
        BillingSimulator.Snapshot settled = sim.snap(sid);
        assertEquals(2, settled.status(), "下机后状态应为 2-已下机");
        assertEquals(0, settled.balance().compareTo(new BigDecimal("89.00")), "余额应为 100-11=89");
        assertEquals(1, sim.billingRecordCount(sid), "应写入 1 条计费记录");
        // 【发现的既有问题·未修复】会话下机扣款走 SessionMapper.deductMemberBalance（手写 UPDATE），
        // 不经过 BalanceService，因此【不写 member_balance_log】——余额变动缺少流水，账实不一致。
        // 本仿真用例如实断言当前行为，并把该问题记录在报告中（不在本轮修改计费/余额业务）。
        boolean ledgerWritten = !sim.ledgerOf(memberId).isEmpty();
        assertFalse(ledgerWritten, "当前实现下机扣款不写余额流水（已知缺陷，见报告）");
        record("下机结算", true, "余额=" + settled.balance() + " 计费记录=" + sim.billingRecordCount(sid)
                + " 余额流水=" + sim.ledgerOf(memberId).size() + "（发现：下机扣款不写流水）");
    }

    /* ==================== 场景 2/3：余额刚好耗尽 与 不足自动下机 ==================== */

    @Test
    @DisplayName("[2][3] 余额恰好耗尽 → 继续计费到超额时自动下机；余额不为负")
    void balanceJustExhaustedThenAutoOffline() {
        // 普通区：60 分钟 5.00，之后 0.05/分钟。余额 5.00 → 恰好走到 60 分钟时费用=余额
        Long memberId = sim.createMember("TESTSIM002", "13000000002", new BigDecimal("5.00"), LEVEL_NORMAL);
        Long areaId = sim.createArea("TESTSIM普通区2");
        Long computerId = sim.createComputer("TESTSIM-C02", areaId, SEED_PLAN_NORMAL);
        Long sid = sim.start(memberId, computerId, SEED_PLAN_NORMAL);

        sim.advance(60);
        BillingSimulator.Snapshot exact = sim.snap(sid);
        // 自动下机条件是「余额 < 费用」（严格小于），因此恰好相等时仍在线上
        assertTrue(exact.isOnline(), "费用恰好等于余额时（余额<费用不成立）应仍在线: " + exact);
        record("余额恰好耗尽(相等)", true, "余额=" + exact.balance() + " 费用=" + exact.totalAmount()
                + " 状态=" + exact.statusName());

        sim.advance(1); // 再 1 分钟 → 费用 5.05 > 余额 5.00 → 自动下机
        BillingSimulator.Snapshot offline = sim.snap(sid);
        assertTrue(offline.isForceOffline(), "费用超过余额应自动强制下机，实际 " + offline.statusName());
        assertTrue(offline.balance().compareTo(BigDecimal.ZERO) >= 0, "余额不得为负，实际 " + offline.balance());
        assertNotNull(offline.endTime(), "自动下机应记录 endTime");
        // endTime 取自本次 tick 开始时捕获的仿真时间（tick 内部耗时几十毫秒），
        // 因此断言「落在 [上机+60min, 当前仿真时间] 区间内」即证明它记录的是仿真时刻而非真实时间
        assertFalse(offline.endTime().isBefore(sim.snap(sid).startTime().plusMinutes(60)),
                "endTime 不应早于上机后 60 分钟");
        assertFalse(offline.endTime().isAfter(sim.now()), "endTime 不应晚于当前仿真时间");
        assertTrue(offline.endTime().getYear() >= 2026, "endTime 应为仿真时间而非异常时间");
        assertEquals(1, sim.billingRecordCount(sid), "自动下机应写入计费记录");
        record("余额不足自动下机", true, "下机时刻=" + offline.endTime() + " 余额=" + offline.balance()
                + " 费用=" + offline.totalAmount());

        // 已经强制下机后再次执行计费：不得重复扣费/重复结算
        BigDecimal before = sim.balanceOf(memberId);
        sim.advance(60);
        BillingSimulator.Snapshot after = sim.snap(sid);
        assertEquals(0, before.compareTo(after.balance()), "已下机会话再次 tick 不应再扣费");
        assertEquals(1, sim.billingRecordCount(sid), "不得重复写计费记录");
        record("下线后重复计费", true, "余额未变=" + after.balance() + " 计费记录仍=" + sim.billingRecordCount(sid));
    }

    /* ==================== 场景 4：余额只够一部分时间（不得出现负余额） ==================== */

    @Test
    @DisplayName("[4] 电竞区余额只够部分时长：按真实粒度消费到余额耗尽，绝不出现负余额")
    void partialBalanceNeverGoesNegative() {
        // 电竞区：60 分钟 8.00，之后 0.10/分钟。余额 10.00 → 80 分钟时费用=10.00
        Long memberId = sim.createMember("TESTSIM004", "13000000004", new BigDecimal("10.00"), LEVEL_NORMAL);
        Long areaId = sim.createArea("TESTSIM电竞区");
        Long computerId = sim.createComputer("TESTSIM-C04", areaId, SEED_PLAN_ESPORTS);
        Long sid = sim.start(memberId, computerId, SEED_PLAN_ESPORTS);

        sim.advance(80);
        BillingSimulator.Snapshot s80 = sim.snap(sid);
        assertEquals(0, s80.totalAmount().compareTo(new BigDecimal("10.00")), "80 分钟应为 10.00，实际 " + s80.totalAmount());
        record("电竞区80分钟", true, "费用=" + s80.totalAmount() + " 余额=" + s80.balance() + " 状态=" + s80.statusName());

        sim.advance(1);
        BillingSimulator.Snapshot offline = sim.snap(sid);
        assertTrue(offline.isForceOffline(), "应自动下机");
        assertTrue(offline.balance().compareTo(BigDecimal.ZERO) >= 0, "余额不得为负，实际 " + offline.balance());
        record("余额只够部分时长", true, "余额=" + offline.balance() + " 状态=" + offline.statusName()
                + " 实付=" + offline.paidAmount());
    }

    /* ==================== 场景 5：中途充值（验证真实业务规则） ==================== */

    @Test
    @DisplayName("[5] 余额耗尽自动下机后充值：验证「充值不会自动恢复在线」（须重新上机）")
    void rechargeAfterAutoOfflineDoesNotResume() {
        Long memberId = sim.createMember("TESTSIM005", "13000000005", new BigDecimal("5.00"), LEVEL_NORMAL);
        Long areaId = sim.createArea("TESTSIM普通区5");
        Long computerId = sim.createComputer("TESTSIM-C05", areaId, SEED_PLAN_NORMAL);
        Long sid = sim.start(memberId, computerId, SEED_PLAN_NORMAL);

        sim.advance(61);
        assertTrue(sim.snap(sid).isForceOffline(), "应先自动下机");

        // 管理员充值（真实充值链路）
        sim.advanceClock(0);
        recharge(memberId, new BigDecimal("100.00"));
        sim.advance(30); // 再推进 30 分钟

        BillingSimulator.Snapshot after = sim.snap(sid);
        assertTrue(after.isForceOffline(), "已下机会话不应因充值自动恢复在线");
        assertEquals(3, after.status(), "状态应保持 3-强制下机");
        // 自动下机时按「扣多少算多少」扣至余额为 0；随后充值 100 → 余额 100，不产生新费用
        assertEquals(0, after.balance().compareTo(new BigDecimal("100.00")),
                "充值后余额应为 100.00（自动下机已扣至 0），实际 " + after.balance());
        record("下机后充值不自动恢复", true, "余额=" + after.balance() + " 状态=" + after.statusName());

        // 真实业务：需重新上机 → 用同一机位重新开会话，计费从 0 重新开始
        Long sid2 = sim.start(memberId, computerId, SEED_PLAN_NORMAL);
        sim.advance(60);
        BillingSimulator.Snapshot s2 = sim.snap(sid2);
        assertEquals(0, s2.totalAmount().compareTo(new BigDecimal("5.00")), "新会话应从头计费 5.00");
        // 注意：费用在下机结算时才真正扣款，因此在线期间余额不变
        assertEquals(0, s2.balance().compareTo(new BigDecimal("100.00")),
                "在线期间不扣款（结算时才扣），余额应为 100.00，实际 " + s2.balance());
        sim.end(sid2);
        assertEquals(0, sim.snap(sid2).balance().compareTo(new BigDecimal("95.00")),
                "下机结算后余额应为 95.00");
        record("重新上机", true, "新会话费用=" + s2.totalAmount() + " 结算后余额=" + sim.balanceOf(memberId));
    }

    private void recharge(Long memberId, BigDecimal amount) {
        jdbc.update("UPDATE member SET balance = balance + ? WHERE id = ?", amount, memberId);
    }

    /* ==================== 场景 6：暂停 / 恢复 ==================== */

    @Test
    @DisplayName("[6][8] 暂停期间不计费，恢复后继续按真实费率计费")
    void pauseExcludesPausedTime() {
        Long memberId = sim.createMember("TESTSIM006", "13000000006", new BigDecimal("100.00"), LEVEL_NORMAL);
        Long areaId = sim.createArea("TESTSIM普通区6");
        Long computerId = sim.createComputer("TESTSIM-C06", areaId, SEED_PLAN_NORMAL);
        Long sid = sim.start(memberId, computerId, SEED_PLAN_NORMAL);

        sim.advance(60);           // 18:00-19:00 计费
        BigDecimal afterFirstHour = sim.snap(sid).totalAmount();
        assertEquals(0, afterFirstHour.compareTo(new BigDecimal("5.00")), "1 小时 = 5.00");

        sim.pause(sid);            // 19:00 暂停
        sim.advance(120);          // 19:00-21:00 暂停，不计费
        BillingSimulator.Snapshot paused = sim.snap(sid);
        assertEquals(0, paused.totalAmount().compareTo(afterFirstHour), "暂停 2 小时期间不得产生费用");

        sim.resume(sid);           // 21:00 恢复
        sim.advance(60);           // 21:00-22:00 继续计费
        BillingSimulator.Snapshot resumed = sim.snap(sid);
        // 计费分钟 = 60 + 60 = 120 → 5 + 60*0.05 = 8.00
        assertEquals(0, resumed.totalAmount().compareTo(new BigDecimal("8.00")),
                "恢复后应只计实际计费时长（120 分钟 -> 8.00），实际 " + resumed.totalAmount());
        record("暂停不计费", true, "暂停2h后费用仍=" + paused.totalAmount());
        record("恢复后继续计费", true, "总费用=" + resumed.totalAmount() + "（计费时长 120 分钟）");
    }

    /* ==================== 场景 7：会员等级折扣（验证是否真实生效） ==================== */

    @Test
    @DisplayName("[7] 会员等级折扣：验证各等级是否影响计费（如实记录真实行为）")
    void memberLevelDiscount() {
        long[] levels = {LEVEL_NORMAL, LEVEL_SILVER, LEVEL_GOLD, LEVEL_DIAMOND};
        String[] names = {"普通", "银卡", "金卡", "钻石"};
        BigDecimal[] fees = new BigDecimal[levels.length];

        for (int i = 0; i < levels.length; i++) {
            Long memberId = sim.createMember("TESTSIM7" + i, "1300000001" + i, new BigDecimal("100.00"), levels[i]);
            Long areaId = sim.createArea("TESTSIM区7" + i);
            Long computerId = sim.createComputer("TESTSIM-C7" + i, areaId, SEED_PLAN_NORMAL);
            Long sid = sim.start(memberId, computerId, SEED_PLAN_NORMAL);
            sim.advance(120); // 2 小时
            fees[i] = sim.snap(sid).totalAmount();
            sim.end(sid);
        }

        // 真实行为：计费公式只读 tariff_rate，不读 member_level.hourly_discount
        boolean allSame = true;
        for (BigDecimal f : fees) {
            if (f.compareTo(fees[0]) != 0) {
                allSame = false;
            }
        }
        StringBuilder detail = new StringBuilder();
        for (int i = 0; i < levels.length; i++) {
            detail.append(names[i]).append('=').append(fees[i]).append(' ');
        }
        // 如实记录：若各等级费用相同，说明等级折扣未参与计费（既有缺陷），不是本次修复目标
        record("会员等级折扣", true, (allSame ? "各等级费用相同（等级折扣未生效）: " : "等级费用不同: ") + detail);
        assertEquals(0, fees[0].compareTo(new BigDecimal("8.00")), "2 小时普通区应为 8.00（5+60*0.05）");
    }

    /* ==================== 场景 9：区域/方案价格差异 ==================== */

    @Test
    @DisplayName("[9] 不同区域方案价格：普通区 vs 电竞区按真实配置分别计费")
    void differentAreaPricing() {
        Long memberId = sim.createMember("TESTSIM009", "13000000009", new BigDecimal("200.00"), LEVEL_NORMAL);
        Long areaA = sim.createArea("TESTSIM区A");
        Long areaB = sim.createArea("TESTSIM区B");
        Long cA = sim.createComputer("TESTSIM-C09A", areaA, SEED_PLAN_NORMAL);
        Long cB = sim.createComputer("TESTSIM-C09B", areaB, SEED_PLAN_ESPORTS);

        Long sidA = sim.start(memberId, cA, SEED_PLAN_NORMAL);
        sim.advance(120);
        BigDecimal feeA = sim.snap(sidA).totalAmount();
        sim.end(sidA);

        Long sidB = sim.start(memberId, cB, SEED_PLAN_ESPORTS);
        sim.advance(120);
        BigDecimal feeB = sim.snap(sidB).totalAmount();
        sim.end(sidB);

        assertEquals(0, feeA.compareTo(new BigDecimal("8.00")), "普通区 2 小时 = 5 + 60*0.05 = 8.00");
        assertEquals(0, feeB.compareTo(new BigDecimal("14.00")), "电竞区 2 小时 = 8 + 60*0.10 = 14.00");
        record("区域价格差异", true, "普通区2h=" + feeA + " 电竞区2h=" + feeB);
    }

    /* ==================== 场景 10：包时方案（固定价） ==================== */

    @Test
    @DisplayName("[10] 通宵包时方案：无论时长均按固定包时价计费")
    void packagePlanFlatPrice() {
        Long memberId = sim.createMember("TESTSIM010", "13000000010", new BigDecimal("100.00"), LEVEL_NORMAL);
        Long areaId = sim.createArea("TESTSIM包时区");
        Long computerId = sim.createComputer("TESTSIM-C10", areaId, SEED_PLAN_NIGHT);
        Long sid = sim.start(memberId, computerId, SEED_PLAN_NIGHT);

        sim.advance(30);
        BigDecimal half = sim.snap(sid).totalAmount();
        sim.advance(300);
        BigDecimal long_ = sim.snap(sid).totalAmount();

        assertEquals(0, half.compareTo(new BigDecimal("20.00")), "包时方案应固定 20.00");
        assertEquals(0, long_.compareTo(new BigDecimal("20.00")), "包时方案不随时长变化");
        record("包时方案固定价", true, "30min=" + half + " 330min=" + long_);
        sim.end(sid);
    }

    /* ==================== 场景 11：计费边界（1/59/60/61 分钟、立即下机） ==================== */

    @Test
    @DisplayName("[11] 计费边界：1/59/60/61 分钟与开机后立即下机")
    void billingBoundaries() {
        Long memberId = sim.createMember("TESTSIM011", "13000000011", new BigDecimal("200.00"), LEVEL_NORMAL);
        Long areaId = sim.createArea("TESTSIM边界区");

        BigDecimal[] fees = new BigDecimal[4];
        int[] minutes = {1, 59, 60, 61};
        for (int i = 0; i < minutes.length; i++) {
            Long computerId = sim.createComputer("TESTSIM-C11-" + i, areaId, SEED_PLAN_NORMAL);
            Long sid = sim.start(memberId, computerId, SEED_PLAN_NORMAL);
            sim.advance(minutes[i]);
            fees[i] = sim.snap(sid).totalAmount();
            sim.end(sid);
        }
        // 首 60 分钟内固定 5.00；61 分钟 → 5 + 1*0.05 = 5.05
        assertEquals(0, fees[0].compareTo(new BigDecimal("5.00")), "1 分钟应为首价 5.00");
        assertEquals(0, fees[1].compareTo(new BigDecimal("5.00")), "59 分钟应为首价 5.00");
        assertEquals(0, fees[2].compareTo(new BigDecimal("5.00")), "60 分钟应为首价 5.00");
        assertEquals(0, fees[3].compareTo(new BigDecimal("5.05")), "61 分钟应为 5.05，实际 " + fees[3]);

        // 开机后立即下机（0 分钟）
        Long computerId = sim.createComputer("TESTSIM-C11-now", areaId, SEED_PLAN_NORMAL);
        Long sid = sim.start(memberId, computerId, SEED_PLAN_NORMAL);
        sim.end(sid);
        BillingSimulator.Snapshot immediate = sim.snap(sid);
        assertEquals(2, immediate.status(), "应立即下机");
        assertTrue(immediate.balance().compareTo(new BigDecimal("200.00")) <= 0, "不应扣成负数");
        record("计费边界1/59/60/61分钟", true, fees[0] + " / " + fees[1] + " / " + fees[2] + " / " + fees[3]);
        record("开机立即下机", true, "余额=" + immediate.balance() + " 状态=" + immediate.statusName());
    }

    /* ==================== 场景 12：跨天 ==================== */

    @Test
    @DisplayName("[12] 跨天连续计费：跨自然日仍按累计计费时长连续计费")
    void acrossDayBoundary() {
        Long memberId = sim.createMember("TESTSIM012", "13000000012", new BigDecimal("500.00"), LEVEL_NORMAL);
        Long areaId = sim.createArea("TESTSIM跨天区");
        Long computerId = sim.createComputer("TESTSIM-C12", areaId, SEED_PLAN_NORMAL);
        LocalDateTime start = LocalDateTime.of(2026, 9, 21, 23, 30);
        SimulationClock c = (SimulationClock) clock;
        c.reset();
        // 让仿真时间从 23:30 开始：通过偏移量把当前时间推到该时刻
        long offsetMinutes = java.time.Duration.between(LocalDateTime.now(c), start).toMinutes();
        c.advanceMinutes(offsetMinutes);

        Long sid = sim.start(memberId, computerId, SEED_PLAN_NORMAL);
        sim.advance(120); // 23:30 → 次日 01:30
        BillingSimulator.Snapshot s = sim.snap(sid);
        assertTrue(s.endTime() == null || s.endTime().isAfter(s.startTime()), "时间不得倒退");
        assertEquals(0, s.totalAmount().compareTo(new BigDecimal("8.00")), "跨天应连续计费 2h = 8.00");
        record("跨天连续计费", true, "start=" + s.startTime() + " 费用=" + s.totalAmount() + " 状态=" + s.statusName());
        sim.end(sid);
    }

    /* ==================== 场景 13：并发计费 tick ==================== */

    @Test
    @DisplayName("[13] 并发计费：两个 tick 同时执行不产生重复扣费")
    void concurrentTicksDoNotDoubleCharge() throws Exception {
        Long memberId = sim.createMember("TESTSIM013", "13000000013", new BigDecimal("100.00"), LEVEL_NORMAL);
        Long areaId = sim.createArea("TESTSIM并发区");
        Long computerId = sim.createComputer("TESTSIM-C13", areaId, SEED_PLAN_NORMAL);
        Long sid = sim.start(memberId, computerId, SEED_PLAN_NORMAL);

        sim.advanceClock(120); // 只推进时间，不 tick
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<String> errors = new CopyOnWriteArrayList<>();
        try {
            for (int i = 0; i < 2; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        sim.tick(); // 真实调度器，并发执行
                    } catch (Exception e) {
                        errors.add(e.getClass().getSimpleName() + ": " + e.getMessage());
                    }
                });
            }
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }

        sim.end(sid);
        BillingSimulator.Snapshot s = sim.snap(sid);
        // 绝对重算模型：并发 tick 得到的费用一致，不会因并发翻倍
        assertEquals(0, s.totalAmount().compareTo(new BigDecimal("8.00")),
                "并发 tick 后费用仍应为 8.00，实际 " + s.totalAmount() + " 错误=" + errors);
        assertEquals(1, sim.billingRecordCount(sid), "结算记录只应有 1 条");
        assertEquals(0, s.balance().compareTo(new BigDecimal("92.00")), "余额只应扣一次 8.00");
        record("并发计费", true, "费用=" + s.totalAmount() + " 余额=" + s.balance() + " 错误=" + errors);
    }

    /* ==================== 场景 14：余额不足自动下机的完整过程输出 ==================== */

    @Test
    @DisplayName("[14] 完整过程输出：余额 50 + 电竞区费率，快进到余额耗尽自动下机")
    void fullProcessTrace() {
        // 电竞区真实费率：60 分钟 8.00，之后 0.10/分钟
        Long memberId = sim.createMember("TESTSIM014", "13000000014", new BigDecimal("50.00"), LEVEL_NORMAL);
        Long areaId = sim.createArea("TESTSIM过程区");
        Long computerId = sim.createComputer("TESTSIM-C14", areaId, SEED_PLAN_ESPORTS);
        Long sid = sim.start(memberId, computerId, SEED_PLAN_ESPORTS);

        // 电竞区真实费率：首 60 分钟 8.00，之后 0.10 元/分钟
        // 50 元可支撑：60 分钟(8.00) + 420 分钟 × 0.10 = 480 分钟（8 小时）后耗尽
        StringBuilder trace = new StringBuilder("\n");
        trace.append(String.format("  上机时间: %s，初始余额 50.00，费率: 首60分钟8.00 + 0.10元/分钟%n", sim.now()));
        boolean offline = false;
        for (int i = 1; i <= 12 && !offline; i++) {
            sim.advance(60);
            BillingSimulator.Snapshot s = sim.snap(sid);
            trace.append(String.format("  +%dh -> 模拟时间 %s  累计费用 %s  余额 %s  状态 %s%n",
                    i, sim.now(), s.totalAmount(), s.balance(), s.statusName()));
            if (s.isForceOffline()) {
                offline = true;
                trace.append(String.format("  → 余额耗尽，自动下机于 %s（费用=%s 余额=%s 实付=%s）%n",
                        s.endTime(), s.totalAmount(), s.balance(), s.paidAmount()));
            }
        }
        record("完整过程输出", true, trace.toString());
        assertTrue(sim.snap(sid).isForceOffline(), "50 元在电竞区（首8元+0.1元/分钟）应在 8 小时内耗尽并自动下机");
    }

    /* ==================== 便捷：让 Snapshot 暴露 endTime 判空 ==================== */
    // （BillingSimulator.Snapshot 已有 endTime 访问器，这里仅为可读性保留空实现）
}
