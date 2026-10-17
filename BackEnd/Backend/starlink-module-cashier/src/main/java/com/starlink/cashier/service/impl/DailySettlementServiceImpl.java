package com.starlink.cashier.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.starlink.cashier.dto.request.SettlementQueryRequest;
import com.starlink.cashier.dto.response.SettlementResponse;
import com.starlink.cashier.entity.DailySettlement;
import com.starlink.cashier.entity.Order;
import com.starlink.cashier.entity.PaymentRecord;
import com.starlink.cashier.entity.RefundRecord;
import com.starlink.cashier.mapper.DailySettlementMapper;
import com.starlink.cashier.mapper.OrderMapper;
import com.starlink.cashier.mapper.PaymentRecordMapper;
import com.starlink.cashier.mapper.RefundRecordMapper;
import com.starlink.cashier.service.DailySettlementService;
import com.starlink.common.constant.CommonConstants;
import com.starlink.common.exception.BusinessException;
import com.starlink.common.result.ErrorCode;
import com.starlink.common.util.PageResult;
import com.starlink.common.utils.NumberGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.TreeMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailySettlementServiceImpl implements DailySettlementService {

    private final DailySettlementMapper settlementMapper;
    private final OrderMapper orderMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final RefundRecordMapper refundRecordMapper;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public PageResult<SettlementResponse> getSettlementPage(SettlementQueryRequest query) {
        Page<SettlementResponse> page = new Page<>(query.getPage(), query.getSize());
        IPage<SettlementResponse> result = settlementMapper.selectSettlementPage(page,
                query.getStartTime(), query.getEndTime(), query.getStatus());
        result.getRecords().forEach(this::enrichSettlementLabels);
        return PageResult.from(result);
    }

    @Override
    public SettlementResponse getSettlementById(Long id) {
        DailySettlement settlement = settlementMapper.selectById(id);
        if (settlement == null) {
            throw new BusinessException(ErrorCode.SETTLEMENT_NOT_FOUND);
        }
        return convertToResponse(settlement);
    }

    @Override
    @Transactional
    public SettlementResponse generateSettlement(String settleDateStr) {
        LocalDate settleDate = LocalDate.parse(settleDateStr);

        DailySettlement existing = settlementMapper.selectByDateForUpdate(settleDate);
        if (existing != null && existing.getStatus() != null
                && existing.getStatus() != CommonConstants.SETTLEMENT_STATUS_PENDING) {
            return convertToResponse(existing);
        }

        LocalDateTime startOfDay = settleDate.atStartOfDay();
        LocalDateTime endOfDay = settleDate.plusDays(1).atStartOfDay();

        // Aggregate paid orders for the day
        LambdaQueryWrapper<Order> orderWrapper = new LambdaQueryWrapper<>();
        orderWrapper.and(w -> w
                .eq(Order::getStatus, CommonConstants.ORDER_STATUS_PAID)
                .or().eq(Order::getStatus, CommonConstants.ORDER_STATUS_PARTIAL_REFUND)
                .or().eq(Order::getStatus, CommonConstants.ORDER_STATUS_REFUNDED));
        orderWrapper.ge(Order::getPaidAt, startOfDay);
        orderWrapper.lt(Order::getPaidAt, endOfDay);
        List<Order> orders = orderMapper.selectList(orderWrapper);

        BigDecimal productRevenue = BigDecimal.ZERO;
        int totalOrders = 0;

        for (Order order : orders) {
            BigDecimal amount = order.getPaidAmount() != null ? order.getPaidAmount() : BigDecimal.ZERO;
            if (order.getOrderType() != null) {
                switch (order.getOrderType()) {
                    case 1, 4 -> {
                        productRevenue = productRevenue.add(amount);
                        totalOrders++;
                    }
                    // 上机、充值有独立业务表，不从订单重复计入。
                    case 2, 3 -> { }
                    default -> log.warn("Unknown order type {} for order {}", order.getOrderType(), order.getId());
                }
            }
        }

        BigDecimal onlineRevenue = sum("SELECT COALESCE(SUM(final_amount), 0) FROM billing_record " +
                "WHERE is_settled = 1 AND deleted_at IS NULL AND settled_at >= ? AND settled_at < ?",
                startOfDay, endOfDay);
        BigDecimal rechargeRevenue = sum("SELECT COALESCE(SUM(recharge_amount), 0) FROM member_recharge " +
                "WHERE status = 1 AND deleted_at IS NULL AND paid_at >= ? AND paid_at < ?",
                startOfDay, endOfDay);
        int totalSessions = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM session " +
                "WHERE deleted_at IS NULL AND end_time >= ? AND end_time < ?", Integer.class,
                startOfDay, endOfDay);
        Usage usage = calculateUsage(startOfDay, endOfDay);

        // Aggregate payments by method
        BigDecimal cashAmount = BigDecimal.ZERO;
        BigDecimal balanceAmount = BigDecimal.ZERO;
        for (Order order : orders) {
            if (order.getOrderType() == null || (order.getOrderType() != 1 && order.getOrderType() != 4)) continue;
            PaymentRecord payment = paymentRecordMapper.selectByOrderId(order.getId());
            if (payment != null && payment.getTotalAmount() != null) {
                if (payment.getPaymentMethod() != null) {
                    if (payment.getPaymentMethod() == CommonConstants.PAY_METHOD_CASH) {
                        cashAmount = cashAmount.add(payment.getTotalAmount());
                    } else if (payment.getPaymentMethod() == CommonConstants.PAY_METHOD_BALANCE) {
                        balanceAmount = balanceAmount.add(payment.getTotalAmount());
                    }
                }
            }
        }
        cashAmount = cashAmount.add(sum("SELECT COALESCE(SUM(recharge_amount), 0) FROM member_recharge " +
                "WHERE status = 1 AND payment_method = 1 AND deleted_at IS NULL " +
                "AND paid_at >= ? AND paid_at < ?", startOfDay, endOfDay));
        balanceAmount = balanceAmount.add(onlineRevenue);

        // Aggregate refunds for the day
        LambdaQueryWrapper<RefundRecord> refundWrapper = new LambdaQueryWrapper<>();
        refundWrapper.ge(RefundRecord::getCreatedAt, startOfDay);
        refundWrapper.lt(RefundRecord::getCreatedAt, endOfDay);
        refundWrapper.eq(RefundRecord::getStatus, CommonConstants.REFUND_STATUS_COMPLETED);
        List<RefundRecord> refunds = refundRecordMapper.selectList(refundWrapper);
        BigDecimal totalRefund = refunds.stream()
                .map(r -> r.getRefundAmount() != null ? r.getRefundAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalRevenue = productRevenue.add(onlineRevenue).add(rechargeRevenue).subtract(totalRefund);

        DailySettlement settlement = existing == null ? new DailySettlement() : existing;
        settlement.setSettleDate(settleDate);
        if (existing == null) settlement.setSettleNo(NumberGenerator.generateSettleNo());
        settlement.setTotalRevenue(totalRevenue);
        settlement.setOnlineRevenue(onlineRevenue);
        settlement.setProductRevenue(productRevenue);
        settlement.setRechargeRevenue(rechargeRevenue);
        settlement.setTotalRecharge(rechargeRevenue);
        settlement.setTotalRefund(totalRefund);
        settlement.setTotalOrders(totalOrders);
        settlement.setTotalSessions(totalSessions);
        settlement.setPeakConcurrent(usage.peak());
        settlement.setAvgOccupancyRate(usage.occupancyRate());
        settlement.setCashAmount(cashAmount);
        settlement.setBalanceAmount(balanceAmount);
        settlement.setStatus((byte) CommonConstants.SETTLEMENT_STATUS_PENDING);

        if (existing == null) {
            settlementMapper.insert(settlement);
        } else if (settlementMapper.updateById(settlement) != 1) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), "日结数据已变更，请重试");
        }
        log.info("generate settlement: date={}, totalRevenue={}", settleDate, totalRevenue);

        return convertToResponse(settlement);
    }

    private BigDecimal sum(String sql, LocalDateTime start, LocalDateTime end) {
        BigDecimal value = jdbcTemplate.queryForObject(sql, BigDecimal.class, start, end);
        return value == null ? BigDecimal.ZERO : value;
    }

    private record Usage(int peak, BigDecimal occupancyRate) { }

    private Usage calculateUsage(LocalDateTime start, LocalDateTime end) {
        Integer computers = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM computer WHERE is_active = 1 AND deleted_at IS NULL", Integer.class);
        if (computers == null || computers == 0) return new Usage(0, BigDecimal.ZERO);

        TreeMap<LocalDateTime, Integer> events = new TreeMap<>();
        jdbcTemplate.query("SELECT start_time, end_time FROM session WHERE deleted_at IS NULL " +
                "AND start_time < ? AND (end_time IS NULL OR end_time > ?)", rs -> {
            LocalDateTime from = rs.getTimestamp("start_time").toLocalDateTime();
            java.sql.Timestamp endTimestamp = rs.getTimestamp("end_time");
            LocalDateTime to = endTimestamp == null ? end : endTimestamp.toLocalDateTime();
            from = from.isBefore(start) ? start : from;
            to = to.isAfter(end) ? end : to;
            if (from.isBefore(to)) {
                events.merge(from, 1, Integer::sum);
                events.merge(to, -1, Integer::sum);
            }
        }, end, start);

        int active = 0;
        int peak = 0;
        long occupiedSeconds = 0;
        LocalDateTime previous = start;
        for (var event : events.entrySet()) {
            occupiedSeconds += Duration.between(previous, event.getKey()).getSeconds() * active;
            active += event.getValue();
            peak = Math.max(peak, active);
            previous = event.getKey();
        }
        long possibleSeconds = Duration.between(start, end).getSeconds() * computers;
        BigDecimal rate = BigDecimal.valueOf(Math.min(occupiedSeconds, possibleSeconds))
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(possibleSeconds), 2, RoundingMode.HALF_UP);
        return new Usage(Math.min(peak, computers), rate);
    }

    @Override
    @Transactional
    public SettlementResponse confirmSettlement(Long id, Long confirmBy) {
        DailySettlement settlement = settlementMapper.selectByIdForUpdate(id);
        if (settlement == null) {
            throw new BusinessException(ErrorCode.SETTLEMENT_NOT_FOUND);
        }
        if (settlement.getStatus() != null
                && (settlement.getStatus().byteValue() == (byte) CommonConstants.SETTLEMENT_STATUS_CONFIRMED
                    || settlement.getStatus().byteValue() == (byte) CommonConstants.SETTLEMENT_STATUS_ARCHIVED)) {
            throw new BusinessException(ErrorCode.SETTLEMENT_ALREADY_CONFIRMED);
        }

        settlement.setStatus((byte) CommonConstants.SETTLEMENT_STATUS_CONFIRMED);
        settlement.setConfirmBy(confirmBy);
        settlement.setConfirmedAt(LocalDateTime.now());
        if (settlementMapper.updateById(settlement) != 1) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), "日结数据已变更，请重试");
        }
        log.info("confirm settlement: id={}, date={}", id, settlement.getSettleDate());

        return convertToResponse(settlement);
    }

    private SettlementResponse convertToResponse(DailySettlement s) {
        SettlementResponse r = new SettlementResponse();
        r.setId(s.getId());
        r.setSettleDate(s.getSettleDate());
        r.setSettleNo(s.getSettleNo());
        r.setTotalRevenue(s.getTotalRevenue());
        r.setOnlineRevenue(s.getOnlineRevenue());
        r.setProductRevenue(s.getProductRevenue());
        r.setRechargeRevenue(s.getRechargeRevenue());
        r.setTotalRecharge(s.getTotalRecharge());
        r.setTotalRefund(s.getTotalRefund());
        r.setTotalOrders(s.getTotalOrders());
        r.setTotalSessions(s.getTotalSessions());
        r.setPeakConcurrent(s.getPeakConcurrent());
        r.setAvgOccupancyRate(s.getAvgOccupancyRate());
        r.setCashAmount(s.getCashAmount());
        r.setBalanceAmount(s.getBalanceAmount());
        r.setStatus(s.getStatus() != null ? s.getStatus().intValue() : null);
        r.setStatusLabel(getSettlementStatusLabel(s.getStatus()));
        r.setConfirmBy(s.getConfirmBy());
        r.setConfirmedAt(s.getConfirmedAt());
        r.setCreatedAt(s.getCreatedAt());
        return r;
    }

    private void enrichSettlementLabels(SettlementResponse r) {
        if (r.getStatus() != null) {
            r.setStatusLabel(getSettlementStatusLabel(r.getStatus().byteValue()));
        }
    }

    private String getSettlementStatusLabel(Byte status) {
        if (status == null) return "\u672a\u77e5";
        return switch (status) {
            case 0 -> "\u5f85\u786e\u8ba4";
            case 1 -> "\u5df2\u786e\u8ba4";
            case 2 -> "\u5df2\u5f52\u6863";
            default -> "\u672a\u77e5";
        };
    }
}
