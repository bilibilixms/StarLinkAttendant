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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailySettlementServiceImpl implements DailySettlementService {

    private final DailySettlementMapper settlementMapper;
    private final OrderMapper orderMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final RefundRecordMapper refundRecordMapper;

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

        DailySettlement existing = settlementMapper.selectByDate(settleDate);
        if (existing != null) {
            return convertToResponse(existing);
        }

        LocalDateTime startOfDay = settleDate.atStartOfDay();
        LocalDateTime endOfDay = settleDate.atTime(LocalTime.MAX);

        // Aggregate paid orders for the day
        LambdaQueryWrapper<Order> orderWrapper = new LambdaQueryWrapper<>();
        orderWrapper.and(w -> w
                .eq(Order::getStatus, CommonConstants.ORDER_STATUS_PAID)
                .or().eq(Order::getStatus, CommonConstants.ORDER_STATUS_PARTIAL_REFUND)
                .or().eq(Order::getStatus, CommonConstants.ORDER_STATUS_REFUNDED));
        orderWrapper.ge(Order::getPaidAt, startOfDay);
        orderWrapper.le(Order::getPaidAt, endOfDay);
        List<Order> orders = orderMapper.selectList(orderWrapper);

        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal productRevenue = BigDecimal.ZERO;
        BigDecimal onlineRevenue = BigDecimal.ZERO;
        BigDecimal rechargeRevenue = BigDecimal.ZERO;
        int totalOrders = orders.size();

        for (Order order : orders) {
            BigDecimal amount = order.getPaidAmount() != null ? order.getPaidAmount() : BigDecimal.ZERO;
            totalRevenue = totalRevenue.add(amount);

            if (order.getOrderType() != null) {
                switch (order.getOrderType()) {
                    case 1, 4 -> productRevenue = productRevenue.add(amount);
                    case 2 -> onlineRevenue = onlineRevenue.add(amount);
                    case 3 -> rechargeRevenue = rechargeRevenue.add(amount);
                    default -> log.warn("Unknown order type {} for order {}", order.getOrderType(), order.getId());
                }
            }
        }

        // Aggregate payments by method
        BigDecimal cashAmount = BigDecimal.ZERO;
        BigDecimal balanceAmount = BigDecimal.ZERO;
        for (Order order : orders) {
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

        // Aggregate refunds for the day
        LambdaQueryWrapper<RefundRecord> refundWrapper = new LambdaQueryWrapper<>();
        refundWrapper.ge(RefundRecord::getCreatedAt, startOfDay);
        refundWrapper.le(RefundRecord::getCreatedAt, endOfDay);
        refundWrapper.ne(RefundRecord::getStatus, CommonConstants.REFUND_STATUS_REJECTED);
        List<RefundRecord> refunds = refundRecordMapper.selectList(refundWrapper);
        BigDecimal totalRefund = refunds.stream()
                .map(r -> r.getRefundAmount() != null ? r.getRefundAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int totalRefundCount = refunds.size();

        DailySettlement settlement = new DailySettlement();
        settlement.setSettleDate(settleDate);
        settlement.setSettleNo(NumberGenerator.generateSettleNo());
        settlement.setTotalRevenue(totalRevenue);
        settlement.setOnlineRevenue(onlineRevenue);
        settlement.setProductRevenue(productRevenue);
        settlement.setRechargeRevenue(rechargeRevenue);
        settlement.setTotalRecharge(rechargeRevenue);
        settlement.setTotalRefund(totalRefund);
        settlement.setTotalOrders(totalOrders);
        settlement.setTotalSessions(0);
        settlement.setPeakConcurrent(0);
        settlement.setAvgOccupancyRate(BigDecimal.ZERO);
        settlement.setCashAmount(cashAmount);
        settlement.setBalanceAmount(balanceAmount);
        settlement.setStatus((byte) CommonConstants.SETTLEMENT_STATUS_PENDING);

        settlementMapper.insert(settlement);
        log.info("generate settlement: date={}, totalRevenue={}", settleDate, totalRevenue);

        return convertToResponse(settlement);
    }

    @Override
    @Transactional
    public SettlementResponse confirmSettlement(Long id, Long confirmBy) {
        DailySettlement settlement = settlementMapper.selectById(id);
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
        settlementMapper.updateById(settlement);
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
