package com.starlink.session.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.starlink.session.entity.BillingRecord;
import com.starlink.session.entity.Computer;
import com.starlink.session.entity.Session;
import com.starlink.session.entity.SessionTiming;
import com.starlink.session.mapper.BillingRecordMapper;
import com.starlink.session.mapper.ComputerMapper;
import com.starlink.session.mapper.SessionMapper;
import com.starlink.session.mapper.SessionTimingMapper;
import com.starlink.common.utils.NumberGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * 会话实时计费定时任务。
 * <p>
 * 每分钟扫描所有活跃会话（上机中 / 临时下机），根据关联的费率方案
 * 计算并更新 session_timing.amount 与 session.total_amount。
 * <p>
 * 计费规则（按时方案 plan_type=1）：
 * <ul>
 *   <li>首时价（rate_type=1）：前 first_minutes 分钟固定收费 first_price</li>
 *   <li>续价（rate_type=2）：超出首时后按 renewal_price 元/分钟计费</li>
 * </ul>
 * 包时方案（plan_type=2）：固定收费 first_price。
 *
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionBillingScheduler {

    private final SessionMapper sessionMapper;
    private final SessionTimingMapper sessionTimingMapper;
    private final ComputerMapper computerMapper;
    private final BillingRecordMapper billingRecordMapper;

    /**
     * 每分钟执行一次会话计费更新。
     */
    @Scheduled(fixedRate = 60_000)
    public void updateSessionBilling() {
        List<Session> activeSessions = sessionMapper.selectList(
                new LambdaQueryWrapper<Session>()
                        .in(Session::getStatus, 0, 1));

        if (activeSessions.isEmpty()) {
            return;
        }

        int updatedCount = 0;
        for (Session session : activeSessions) {
            try {
                if (updateSessionBillingAmounts(session)) {
                    updatedCount++;
                }
            } catch (Exception e) {
                log.error("会话计费更新失败: sessionId={}, sessionNo={}", session.getId(), session.getSessionNo(), e);
            }
        }

        log.info("会话计费更新完成，活跃会话 {} 条，已更新 {} 条",
                activeSessions.size(), updatedCount);
    }

    // ==================== 私有方法 ====================

    /**
     * 为单个活跃会话计算并更新计费金额，并检查钱包余额。
     *
     * @return true 表示有更新发生
     */
    private boolean updateSessionBillingAmounts(Session session) {
        LocalDateTime now = LocalDateTime.now();
        boolean updated = false;

        // 1. 查询该会话所有计时段（按开始时间升序）
        List<SessionTiming> timings = sessionTimingMapper.selectList(
                new LambdaQueryWrapper<SessionTiming>()
                        .eq(SessionTiming::getSessionId, session.getId())
                        .orderByAsc(SessionTiming::getStartTime));

        // 2. 无计时段时自动创建初始段（容错）
        if (timings.isEmpty()) {
            createInitialTiming(session, now);
            timings = sessionTimingMapper.selectList(
                    new LambdaQueryWrapper<SessionTiming>()
                            .eq(SessionTiming::getSessionId, session.getId())
                            .orderByAsc(SessionTiming::getStartTime));
            updated = true;
        }

        // 3. 上机中会话若所有计时段均已关闭，自动创建新的开放计费段
        if (session.getStatus() == 0) {
            boolean hasOpenTiming = timings.stream()
                    .anyMatch(t -> t.getEndTime() == null && t.getTimingType() != null && t.getTimingType() == 1);
            if (!hasOpenTiming && !timings.isEmpty()) {
                SessionTiming lastTiming = timings.get(timings.size() - 1);
                LocalDateTime newStart = lastTiming.getEndTime() != null ? lastTiming.getEndTime() : now;

                SessionTiming newTiming = new SessionTiming();
                newTiming.setSessionId(session.getId());
                newTiming.setTimingType((byte) 1); // 正常计费
                newTiming.setRateType((byte) 2);   // 续价（已过首时段）
                newTiming.setStartTime(newStart);
                newTiming.setAmount(BigDecimal.ZERO);
                sessionTimingMapper.insert(newTiming);

                // 重新加载计时段列表
                timings = sessionTimingMapper.selectList(
                        new LambdaQueryWrapper<SessionTiming>()
                                .eq(SessionTiming::getSessionId, session.getId())
                                .orderByAsc(SessionTiming::getStartTime));
                updated = true;
                log.info("自动创建续价计时段: sessionId={}, startFrom={}", session.getId(), newStart);
            }
        }

        // 4. 计算所有 type=1 段的累计分钟数
        int cumulativeMinutes = 0;
        for (SessionTiming timing : timings) {
            if (timing.getTimingType() != null && timing.getTimingType() == 1) {
                if (timing.getEndTime() != null) {
                    cumulativeMinutes += (timing.getDurationMinutes() != null) ? timing.getDurationMinutes() : 0;
                } else {
                    cumulativeMinutes += (int) ChronoUnit.MINUTES.between(timing.getStartTime(), now);
                }
            }
        }

        // 5. 用累计分钟数代入费率公式计算一次总费用
        BigDecimal totalAmount = calculateAmountByMinutes(cumulativeMinutes, session.getTariffPlanId());

        // 6. 将总费用分配到各计费段
        BigDecimal distributed = BigDecimal.ZERO;
        List<SessionTiming> billingTimings = timings.stream()
                .filter(t -> t.getTimingType() != null && t.getTimingType() == 1)
                .toList();

        for (int i = 0; i < billingTimings.size(); i++) {
            SessionTiming timing = billingTimings.get(i);
            BigDecimal segmentAmount;
            if (i == billingTimings.size() - 1) {
                segmentAmount = totalAmount.subtract(distributed);
            } else {
                int mins = (timing.getEndTime() != null && timing.getDurationMinutes() != null)
                        ? timing.getDurationMinutes()
                        : (int) ChronoUnit.MINUTES.between(timing.getStartTime(), now);
                mins = Math.max(mins, 0);
                segmentAmount = (cumulativeMinutes > 0)
                        ? totalAmount.multiply(BigDecimal.valueOf(mins))
                                .divide(BigDecimal.valueOf(cumulativeMinutes), 2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                distributed = distributed.add(segmentAmount);
            }

            if (timing.getAmount() == null || timing.getAmount().compareTo(segmentAmount) != 0) {
                timing.setAmount(segmentAmount);
                sessionTimingMapper.updateById(timing);
                updated = true;
            }
        }

        // 临时下机时段金额始终为 0
        for (SessionTiming timing : timings) {
            if (timing.getTimingType() != null && timing.getTimingType() == 2) {
                if (timing.getAmount() == null || timing.getAmount().compareTo(BigDecimal.ZERO) != 0) {
                    timing.setAmount(BigDecimal.ZERO);
                    sessionTimingMapper.updateById(timing);
                }
            }
        }

        // 7. 更新会话累计费用
        if (session.getTotalAmount() == null || session.getTotalAmount().compareTo(totalAmount) != 0) {
            session.setTotalAmount(totalAmount);
            sessionMapper.updateById(session);
            updated = true;
        }

        if (updated) {
            log.debug("会话计费更新: sessionId={}, sessionNo={}, totalAmount={}",
                    session.getId(), session.getSessionNo(), totalAmount);
        }

        // 8. 检查钱包余额，不足则强制下机
        if (session.getMemberId() != null) {
            BigDecimal balance = sessionMapper.selectMemberBalance(session.getMemberId());
            if (balance != null && balance.compareTo(totalAmount) < 0) {
                log.warn("余额不足，触发强制下机: sessionId={}, memberId={}, balance={}, totalAmount={}",
                        session.getId(), session.getMemberId(), balance, totalAmount);
                forceEndSessionDueToInsufficientBalance(session, totalAmount, now);
            }
        }

        return updated;
    }

    /**
     * 为缺失计时段的活跃会话创建初始计费段。
     * <p>
     * 上机中（status=0）→ 创建从 session.startTime 开始的正常计费段；
     * 临时下机（status=1）→ 创建从当前时间开始的临时下机段（数据自愈）。
     */
    private void createInitialTiming(Session session, LocalDateTime now) {
        SessionTiming timing = new SessionTiming();
        timing.setSessionId(session.getId());

        if (session.getStatus() == 0) {
            // 上机中：创建正常计费段，从会话开始时间起算
            timing.setTimingType((byte) 1);
            timing.setRateType((byte) 1);
            timing.setStartTime(session.getStartTime());
        } else {
            // 临时下机但无计时段记录：创建临时下机段（数据自愈）
            timing.setTimingType((byte) 2);
            timing.setStartTime(now);
        }

        timing.setAmount(BigDecimal.ZERO);
        sessionTimingMapper.insert(timing);
        log.warn("自动创建初始计时段: sessionId={}, timingType={}", session.getId(), timing.getTimingType());
    }

    /**
     * 根据累计上机分钟数和费率方案计算会话总费用。
     * <p>
     * 将整个会话的累计时长作为整体代入费率公式，只计算一次。
     */
    private BigDecimal calculateAmountByMinutes(int totalMinutes, Long tariffPlanId) {
        if (tariffPlanId == null || totalMinutes <= 0) {
            return BigDecimal.ZERO;
        }

        List<Map<String, Object>> tariffRates = sessionMapper.selectTariffRates(tariffPlanId);
        if (tariffRates.isEmpty()) {
            return BigDecimal.ZERO;
        }

        // 检查是否包含包时价（rate_type=3）
        for (Map<String, Object> rate : tariffRates) {
            int rt = ((Number) rate.get("rateType")).intValue();
            if (rt == 3) {
                return toBigDecimal(rate.get("firstPrice")).setScale(2, RoundingMode.HALF_UP);
            }
        }

        // 按时方案：首时价 + 续价（renewal_price 数据库单位为 元/分钟）
        BigDecimal firstPrice = BigDecimal.ZERO;
        int firstMinutes = 0;
        BigDecimal renewalPricePerMinute = BigDecimal.ZERO;

        for (Map<String, Object> rate : tariffRates) {
            int rt = ((Number) rate.get("rateType")).intValue();
            if (rt == 1) {
                firstPrice = toBigDecimal(rate.get("firstPrice"));
                firstMinutes = ((Number) rate.get("firstMinutes")).intValue();
            } else if (rt == 2) {
                renewalPricePerMinute = toBigDecimal(rate.get("renewalPrice"));
            }
        }

        if (totalMinutes <= firstMinutes) {
            return firstPrice.setScale(2, RoundingMode.HALF_UP);
        } else {
            int extraMinutes = totalMinutes - firstMinutes;
            BigDecimal extra = renewalPricePerMinute.multiply(BigDecimal.valueOf(extraMinutes))
                    .setScale(2, RoundingMode.HALF_UP);
            return firstPrice.add(extra);
        }
    }

    /**
     * 安全地将 Object 转换为 BigDecimal。
     */
    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        return new BigDecimal(value.toString());
    }

    /**
     * 因余额不足触发强制下机。
     * <p>
     * 结束计时段 → 扣款（扣多少算多少） → 设置强制下机状态 → 释放机位。
     */
    private void forceEndSessionDueToInsufficientBalance(Session session, BigDecimal totalAmount, LocalDateTime now) {
        // 1. 结束当前未结束的计时段
        List<SessionTiming> openTimings = sessionTimingMapper.selectList(
                new LambdaQueryWrapper<SessionTiming>()
                        .eq(SessionTiming::getSessionId, session.getId())
                        .isNull(SessionTiming::getEndTime)
                        .orderByDesc(SessionTiming::getStartTime));
        for (SessionTiming timing : openTimings) {
            timing.setEndTime(now);
            int minutes = (int) ChronoUnit.MINUTES.between(timing.getStartTime(), now);
            timing.setDurationMinutes(Math.max(minutes, 0));
            if (timing.getTimingType() != null && timing.getTimingType() != 1) {
                timing.setAmount(BigDecimal.ZERO);
            }
            sessionTimingMapper.updateById(timing);
        }

        // 2. 计算累计时长和费用
        List<SessionTiming> allTimings = sessionTimingMapper.selectList(
                new LambdaQueryWrapper<SessionTiming>()
                        .eq(SessionTiming::getSessionId, session.getId())
                        .eq(SessionTiming::getTimingType, 1));
        int totalMinutes = 0;
        for (SessionTiming t : allTimings) {
            totalMinutes += (t.getDurationMinutes() != null) ? t.getDurationMinutes() : 0;
        }
        BigDecimal finalAmount = calculateAmountByMinutes(totalMinutes, session.getTariffPlanId());

        // 3. 尝试扣款（余额不足时扣现有余额）
        BigDecimal balance = sessionMapper.selectMemberBalance(session.getMemberId());
        BigDecimal deductAmount = (balance != null && balance.compareTo(BigDecimal.ZERO) > 0)
                ? balance.min(finalAmount)
                : BigDecimal.ZERO;
        if (deductAmount.compareTo(BigDecimal.ZERO) > 0) {
            sessionMapper.deductMemberBalance(session.getMemberId(), deductAmount);
            log.info("余额不足强制下机扣款: memberId={}, deductAmount={}, finalAmount={}",
                    session.getMemberId(), deductAmount, finalAmount);
        }

        // 4. 更新会话状态
        session.setEndTime(now);
        session.setBilledMinutes(totalMinutes);
        session.setTotalAmount(finalAmount);
        session.setPaidAmount(deductAmount);
        session.setStatus((byte) 3); // 强制下机
        session.setRemark("余额不足");
        sessionMapper.updateById(session);

        // 5. 写入计费记录
        BillingRecord record = new BillingRecord();
        record.setSessionId(session.getId());
        record.setMemberId(session.getMemberId());
        record.setComputerId(session.getComputerId());
        record.setRecordNo(NumberGenerator.generateBillingNo());
        record.setTariffPlanId(session.getTariffPlanId());
        record.setMaintenanceMinutes(totalMinutes);
        if (totalMinutes > 0 && finalAmount.compareTo(BigDecimal.ZERO) > 0) {
            record.setUnitPrice(finalAmount.divide(BigDecimal.valueOf(totalMinutes), 4, RoundingMode.HALF_UP));
        } else {
            record.setUnitPrice(BigDecimal.ZERO);
        }
        BigDecimal discountAmount = session.getDiscountAmount() != null ? session.getDiscountAmount() : BigDecimal.ZERO;
        record.setTotalAmount(finalAmount);
        record.setDiscountAmount(discountAmount);
        record.setFinalAmount(finalAmount.subtract(discountAmount));
        record.setBillingStart(session.getStartTime());
        record.setBillingEnd(now);
        record.setIsSettled((byte) 0);
        billingRecordMapper.insert(record);
        log.info("余额不足强制下机计费记录已写入: sessionId={}, recordNo={}, finalAmount={}",
                session.getId(), record.getRecordNo(), finalAmount);

        // 6. 释放机位
        Computer computer = computerMapper.selectById(session.getComputerId());
        if (computer != null) {
            computer.setStatus((byte) 0);
            computerMapper.updateById(computer);
        }

        log.warn("余额不足强制下机完成: sessionId={}, memberId={}, finalAmount={}, deductAmount={}",
                session.getId(), session.getMemberId(), finalAmount, deductAmount);
    }
}
