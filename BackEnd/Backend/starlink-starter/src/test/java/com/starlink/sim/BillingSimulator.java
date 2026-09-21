package com.starlink.sim;

import com.starlink.common.time.SimulationClock;
import com.starlink.session.dto.req.ForceEndRequest;
import com.starlink.session.dto.req.SessionEndRequest;
import com.starlink.session.dto.req.SessionPauseRequest;
import com.starlink.session.dto.req.SessionResumeRequest;
import com.starlink.session.dto.req.SessionStartRequest;
import com.starlink.session.service.SessionService;
import com.starlink.session.task.SessionBillingScheduler;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 计费时间仿真器（<b>仅存在于测试源码树</b>，不会进入生产产物）。
 *
 * <p>设计原则（对应需求「不要复制一套假计费算法」）：
 * <ul>
 *   <li>计费完全走<b>真实链路</b>：{@link SessionService} 负责上机/下机/暂停/恢复，
 *       {@link SessionBillingScheduler#updateSessionBilling()} 就是生产每分钟执行的那个方法，
 *       仿真器只是「代替定时器」在时间快进后手动调用它一次；</li>
 *   <li>费率、折扣、区域价格等一律<b>读取数据库真实配置行</b>（seed 数据或本仿真器创建的 TESTSIM 配置行），
 *       仿真器内部不出现任何计费公式；</li>
 *   <li>时间推进只改 {@link SimulationClock} 的偏移量，<b>不改操作系统/MySQL/JWT 时间</b>。</li>
 * </ul>
 *
 * <p>典型用法：
 * <pre>
 *   Long sid = sim.start(memberId, computerId, planId);   // 18:00 上机
 *   sim.advance(60);                                      // 快进 1 小时并跑一次真实计费
 *   sim.snap(sid);                                        // 看当前费用/余额/状态
 * </pre>
 */
public class BillingSimulator {

    private final SessionService sessionService;
    private final SessionBillingScheduler billingScheduler;
    private final SimulationClock clock;
    private final JdbcTemplate jdbc;

    public BillingSimulator(SessionService sessionService,
                            SessionBillingScheduler billingScheduler,
                            SimulationClock clock,
                            JdbcTemplate jdbc) {
        this.sessionService = sessionService;
        this.billingScheduler = billingScheduler;
        this.clock = clock;
        this.jdbc = jdbc;
    }

    /* ==================== 时间 ==================== */

    /** 当前仿真时间 */
    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    /** 只推进时间（不跑计费） */
    public void advanceClock(long minutes) {
        clock.advanceMinutes(minutes);
    }

    /** 推进时间并执行一次真实计费 tick —— 等价于「这段时间里调度器每分钟跑了一次」的结果 */
    public void advance(long minutes) {
        clock.advanceMinutes(minutes);
        tick();
    }

    /** 执行一次真实计费 tick（生产定时任务调用的同一个 public 方法） */
    public void tick() {
        billingScheduler.updateSessionBilling();
    }

    /* ==================== 夹具（全部 TESTSIM 前缀，便于清理） ==================== */

    public Long createArea(String areaName) {
        jdbc.update("INSERT INTO seat_area (area_name, area_color, sort_order, is_active) VALUES (?, '#1890ff', 1, 1)",
                areaName);
        return jdbc.queryForObject("SELECT id FROM seat_area WHERE area_name = ? ORDER BY id DESC LIMIT 1",
                Long.class, areaName);
    }

    public Long createComputer(String computerNo, Long areaId, Long tariffPlanId) {
        // 注意：computer 表没有 hourly_rate 列 —— 机位/区域本身不带价格，
        // 计价完全由 computer.tariff_plan_id → tariff_rate 决定（这是项目真实设计）。
        jdbc.update("INSERT INTO computer (area_id, computer_no, computer_name, seat_label, device_type, status, "
                        + "tariff_plan_id, is_active) VALUES (?, ?, ?, ?, 1, 0, ?, 1)",
                areaId, computerNo, computerNo, computerNo, tariffPlanId);
        return jdbc.queryForObject("SELECT id FROM computer WHERE computer_no = ?", Long.class, computerNo);
    }

    /** 创建费率方案（planType：1-按时 2-包时 3-混合） */
    public Long createPlan(String planName, int planType) {
        jdbc.update("INSERT INTO tariff_plan (plan_name, plan_type, is_default, priority, status) "
                + "VALUES (?, ?, 0, 0, 1)", planName, planType);
        return jdbc.queryForObject("SELECT id FROM tariff_plan WHERE plan_name = ? ORDER BY id DESC LIMIT 1",
                Long.class, planName);
    }

    /** 创建费率明细（rateType：1-首价 2-续价 3-包时价） */
    public void createRate(Long planId, int rateType, int firstMinutes, BigDecimal firstPrice, BigDecimal renewalPrice) {
        jdbc.update("INSERT INTO tariff_rate (plan_id, rate_name, rate_type, first_minutes, first_price, "
                        + "renewal_price, min_charge_minutes, round_rule) VALUES (?, ?, ?, ?, ?, ?, 1, 1)",
                planId, "TESTSIM-rate-" + rateType, rateType, firstMinutes, firstPrice, renewalPrice);
    }

    public Long createMember(String memberNo, String phone, BigDecimal balance, long levelId) {
        jdbc.update("INSERT INTO member (member_no, phone, password_hash, real_name, gender, level_id, "
                        + "total_points, available_points, total_recharge, balance, total_consumption, "
                        + "register_source, status) VALUES (?, ?, 'x', '仿真会员', 1, ?, 0, 0, 0.00, ?, 0.00, 1, 1)",
                memberNo, phone, levelId, balance);
        return jdbc.queryForObject("SELECT id FROM member WHERE member_no = ?", Long.class, memberNo);
    }

    /* ==================== 业务操作（真实 Service） ==================== */

    public Long start(Long memberId, Long computerId, Long tariffPlanId) {
        SessionStartRequest req = new SessionStartRequest();
        req.setMemberId(memberId);
        req.setComputerId(computerId);
        req.setAuthMethod((byte) 2);
        req.setTariffPlanId(tariffPlanId);
        return sessionService.startSession(req).getId();
    }

    public void pause(Long sessionId) {
        SessionPauseRequest req = new SessionPauseRequest();
        req.setSessionId(sessionId);
        sessionService.pauseSession(req);
    }

    public void resume(Long sessionId) {
        SessionResumeRequest req = new SessionResumeRequest();
        req.setSessionId(sessionId);
        sessionService.resumeSession(req);
    }

    public void end(Long sessionId) {
        SessionEndRequest req = new SessionEndRequest();
        req.setSessionId(sessionId);
        sessionService.endSession(req);
    }

    public void forceEnd(Long sessionId, String reason) {
        ForceEndRequest req = new ForceEndRequest();
        req.setSessionId(sessionId);
        req.setReason(reason);
        sessionService.forceEndSession(req);
    }

    /* ==================== 查询 ==================== */

    public BigDecimal balanceOf(long memberId) {
        return jdbc.queryForObject("SELECT balance FROM member WHERE id = ?", BigDecimal.class, memberId);
    }

    /** 一次即时快照 */
    public Snapshot snap(Long sessionId) {
        Map<String, Object> s = jdbc.queryForMap(
                "SELECT id, status, start_time, end_time, billed_minutes, total_amount, discount_amount, paid_amount, "
                        + "member_id, computer_id, tariff_plan_id FROM session WHERE id = ?", sessionId);
        long memberId = ((Number) s.get("member_id")).longValue();
        return new Snapshot(
                ((Number) s.get("id")).longValue(),
                s.get("status") instanceof Number n ? n.intValue() : -1,
                toLdt(s.get("start_time")),
                toLdt(s.get("end_time")),
                dec(s.get("total_amount")),
                dec(s.get("discount_amount")),
                dec(s.get("paid_amount")),
                balanceOf(memberId),
                s.get("billed_minutes") instanceof Number n ? n.intValue() : 0,
                memberId);
    }

    /** 该会话产生的余额流水（按 id 升序） */
    public List<Map<String, Object>> ledgerOf(long memberId) {
        return jdbc.queryForList(
                "SELECT id, amount, balance_before, balance_after, biz_type, biz_id FROM member_balance_log "
                        + "WHERE member_id = ? ORDER BY id", memberId);
    }

    /** 该会话的计费记录条数（用于验证「不重复结算」） */
    public int billingRecordCount(long sessionId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM billing_record WHERE session_id = ?",
                Integer.class, sessionId);
        return n == null ? 0 : n;
    }

    public static String statusName(int status) {
        return switch (status) {
            case 0 -> "ONLINE";
            case 1 -> "PAUSED";
            case 2 -> "OFFLINE";
            case 3 -> "FORCE_OFFLINE";
            case 4 -> "ERROR";
            default -> "UNKNOWN(" + status + ")";
        };
    }

    /* ==================== 内部工具 ==================== */

    private static LocalDateTime toLdt(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime();
        }
        return LocalDateTime.parse(String.valueOf(v).replace(' ', 'T'));
    }

    private static BigDecimal dec(Object v) {
        return v == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(v));
    }

    /** 会话 + 会员余额的即时快照 */
    public record Snapshot(long sessionId, int status, LocalDateTime startTime, LocalDateTime endTime,
                           BigDecimal totalAmount, BigDecimal discountAmount, BigDecimal paidAmount,
                           BigDecimal balance, int billedMinutes, long memberId) {

        public String statusName() {
            return BillingSimulator.statusName(status);
        }

        public boolean isOnline() {
            return status == 0;
        }

        public boolean isForceOffline() {
            return status == 3;
        }

        @Override
        public String toString() {
            return String.format("session=%d status=%s start=%s end=%s 费用=%s 折扣=%s 实付=%s 余额=%s 计费分钟=%d",
                    sessionId, statusName(), startTime, endTime, totalAmount, discountAmount, paidAmount,
                    balance, billedMinutes);
        }
    }
}
