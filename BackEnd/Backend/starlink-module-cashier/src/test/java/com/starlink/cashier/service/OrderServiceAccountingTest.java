package com.starlink.cashier.service;

import com.starlink.cashier.dto.request.PaymentRequest;
import com.starlink.cashier.dto.request.RefundRequest;
import com.starlink.cashier.entity.Order;
import com.starlink.cashier.entity.PaymentRecord;
import com.starlink.cashier.entity.RefundRecord;
import com.starlink.cashier.mapper.*;
import com.starlink.cashier.service.impl.OrderServiceImpl;
import com.starlink.common.exception.BusinessException;
import com.starlink.member.service.BalanceService;
import com.starlink.member.service.GrowthValueService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderServiceAccountingTest {

    private final OrderMapper orders = mock(OrderMapper.class);
    private final PaymentRecordMapper payments = mock(PaymentRecordMapper.class);
    private final RefundRecordMapper refunds = mock(RefundRecordMapper.class);
    private final BalanceService balances = mock(BalanceService.class);
    private final OrderServiceImpl service = new OrderServiceImpl(orders, mock(OrderItemMapper.class),
            payments, refunds, mock(ProductInfoMapper.class), mock(CashierShiftMapper.class),
            balances, mock(GrowthValueService.class));

    @Test
    void balancePaymentRefundReturnsToBalanceEvenWithoutClientMethod() {
        Order order = order((byte) 1);
        PaymentRecord payment = new PaymentRecord();
        payment.setId(9L);
        payment.setPaymentMethod((byte) 4);
        payment.setTotalAmount(new BigDecimal("100"));
        payment.setRefundAmount(BigDecimal.ZERO);
        payment.setPaymentStatus((byte) 1);
        when(orders.selectByIdForUpdate(1L)).thenReturn(order);
        when(payments.selectByOrderId(1L)).thenReturn(payment);
        when(refunds.selectByOrderId(1L)).thenReturn(List.of());
        when(payments.updateById(payment)).thenReturn(1);
        when(orders.updateById(order)).thenReturn(1);

        RefundRequest request = new RefundRequest();
        request.setRefundAmount(new BigDecimal("30"));
        service.refundOrder(1L, request, 3L);

        verify(balances).addBalance(eq(7L), eq(new BigDecimal("30")), eq((byte) 2), eq(1L), anyString());
        var saved = org.mockito.ArgumentCaptor.forClass(RefundRecord.class);
        verify(refunds).insert(saved.capture());
        assertEquals((byte) 4, saved.getValue().getRefundMethod());
        assertEquals(new BigDecimal("30"), payment.getRefundAmount());
    }

    @Test
    void refundRejectsDifferentChannel() {
        when(orders.selectByIdForUpdate(1L)).thenReturn(order((byte) 1));
        PaymentRecord payment = new PaymentRecord();
        payment.setPaymentMethod((byte) 4);
        when(payments.selectByOrderId(1L)).thenReturn(payment);
        RefundRequest request = new RefundRequest();
        request.setRefundAmount(new BigDecimal("10"));
        request.setRefundMethod(1);

        assertThrows(BusinessException.class, () -> service.refundOrder(1L, request, 3L));
        verifyNoInteractions(refunds, balances);
    }

    @Test
    void unverifiedOnlinePaymentAndInsufficientCashAreRejected() {
        when(orders.selectByIdForUpdate(1L)).thenReturn(order((byte) 0));
        PaymentRequest online = new PaymentRequest();
        online.setPaymentMethod(2);
        assertThrows(BusinessException.class, () -> service.payOrder(1L, online, 3L));

        PaymentRequest cash = new PaymentRequest();
        cash.setPaymentMethod(1);
        cash.setPaidAmount(new BigDecimal("99"));
        assertThrows(BusinessException.class, () -> service.payOrder(1L, cash, 3L));
        verify(payments, never()).insert(any());
    }

    private Order order(byte status) {
        Order order = new Order();
        order.setId(1L);
        order.setMemberId(7L);
        order.setStatus(status);
        order.setPaidAmount(new BigDecimal("100"));
        order.setPayableAmount(new BigDecimal("100"));
        return order;
    }
}
