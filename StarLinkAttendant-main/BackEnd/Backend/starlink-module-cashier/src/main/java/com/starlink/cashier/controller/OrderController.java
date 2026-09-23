package com.starlink.cashier.controller;

import com.starlink.cashier.dto.request.OrderCreateRequest;
import com.starlink.cashier.dto.request.OrderQueryRequest;
import com.starlink.cashier.dto.request.PaymentRequest;
import com.starlink.cashier.dto.request.RefundQueryRequest;
import com.starlink.cashier.dto.request.RefundRequest;
import com.starlink.cashier.dto.response.OrderDetailResponse;
import com.starlink.cashier.dto.response.OrderResponse;
import com.starlink.cashier.dto.response.RefundResponse;
import com.starlink.cashier.service.OrderService;
import com.starlink.common.result.Result;
import com.starlink.common.util.PageResult;
import com.starlink.common.utils.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/cashier/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping
    public Result<PageResult<OrderResponse>> listOrders(OrderQueryRequest query) {
        return Result.ok(orderService.getOrderPage(query));
    }

    @GetMapping("/{id}")
    public Result<OrderDetailResponse> getOrderDetail(@PathVariable Long id) {
        return Result.ok(orderService.getOrderDetail(id));
    }

    @PostMapping
    public Result<OrderDetailResponse> createOrder(@Valid @RequestBody OrderCreateRequest request) {
        Long operatorId = SecurityUtils.getCurrentUserId();
        return Result.ok(orderService.createOrder(request, operatorId));
    }

    @PostMapping("/{id}/pay")
    public Result<OrderDetailResponse> payOrder(@PathVariable Long id,
                                                 @Valid @RequestBody PaymentRequest request) {
        Long operatorId = SecurityUtils.getCurrentUserId();
        return Result.ok(orderService.payOrder(id, request, operatorId));
    }

    @PostMapping("/{id}/refund")
    public Result<RefundResponse> refundOrder(@PathVariable Long id,
                                               @Valid @RequestBody RefundRequest request) {
        Long operatorId = SecurityUtils.getCurrentUserId();
        return Result.ok(orderService.refundOrder(id, request, operatorId));
    }

    @PostMapping("/{id}/cancel")
    public Result<Void> cancelOrder(@PathVariable Long id) {
        Long operatorId = SecurityUtils.getCurrentUserId();
        orderService.cancelOrder(id, operatorId);
        return Result.ok();
    }

    @GetMapping("/refunds")
    public Result<PageResult<RefundResponse>> listRefunds(RefundQueryRequest query) {
        return Result.ok(orderService.getRefundPage(query));
    }
}
