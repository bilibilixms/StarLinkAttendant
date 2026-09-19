package com.starlink.cashier.service;

import com.starlink.cashier.dto.request.OrderCreateRequest;
import com.starlink.cashier.dto.request.OrderQueryRequest;
import com.starlink.cashier.dto.request.PaymentRequest;
import com.starlink.cashier.dto.request.RefundRequest;
import com.starlink.cashier.dto.request.RefundQueryRequest;
import com.starlink.cashier.dto.response.OrderDetailResponse;
import com.starlink.cashier.dto.response.OrderResponse;
import com.starlink.cashier.dto.response.RefundResponse;
import com.starlink.common.util.PageResult;

public interface OrderService {

    PageResult<OrderResponse> getOrderPage(OrderQueryRequest query);

    OrderDetailResponse getOrderDetail(Long id);

    OrderDetailResponse createOrder(OrderCreateRequest request, Long operatorId);

    OrderDetailResponse payOrder(Long orderId, PaymentRequest request, Long operatorId);

    RefundResponse refundOrder(Long orderId, RefundRequest request, Long operatorId);

    void cancelOrder(Long orderId, Long operatorId);

    PageResult<RefundResponse> getRefundPage(RefundQueryRequest query);
}
