package com.starlink.cashier.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.starlink.cashier.dto.request.OrderCreateRequest;
import com.starlink.cashier.dto.request.OrderItemRequest;
import com.starlink.cashier.dto.request.OrderQueryRequest;
import com.starlink.cashier.dto.request.PaymentRequest;
import com.starlink.cashier.dto.request.RefundQueryRequest;
import com.starlink.cashier.dto.request.RefundRequest;
import com.starlink.cashier.dto.response.OrderDetailResponse;
import com.starlink.cashier.dto.response.OrderItemResponse;
import com.starlink.cashier.dto.response.OrderResponse;
import com.starlink.cashier.dto.response.PaymentRecordResponse;
import com.starlink.cashier.dto.response.RefundResponse;
import com.starlink.cashier.entity.Order;
import com.starlink.cashier.entity.OrderItem;
import com.starlink.cashier.entity.PaymentRecord;
import com.starlink.cashier.entity.RefundRecord;
import com.starlink.cashier.mapper.OrderItemMapper;
import com.starlink.cashier.mapper.OrderMapper;
import com.starlink.cashier.mapper.PaymentRecordMapper;
import com.starlink.cashier.mapper.ProductInfoMapper;
import com.starlink.cashier.mapper.RefundRecordMapper;
import com.starlink.cashier.mapper.CashierShiftMapper;
import com.starlink.cashier.entity.CashierShift;
import com.starlink.cashier.entity.ProductInfo;
import com.starlink.cashier.service.OrderService;
import com.starlink.common.constant.CommonConstants;
import com.starlink.common.exception.BusinessException;
import com.starlink.common.result.ErrorCode;
import com.starlink.common.util.PageResult;
import com.starlink.common.utils.NumberGenerator;
import com.starlink.member.service.BalanceService;
import com.starlink.member.service.GrowthValueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final RefundRecordMapper refundRecordMapper;
    private final ProductInfoMapper productInfoMapper;
    private final CashierShiftMapper shiftMapper;
    private final BalanceService balanceService;
    private final GrowthValueService growthValueService;

    @Override
    public PageResult<OrderResponse> getOrderPage(OrderQueryRequest query) {
        Page<OrderResponse> page = new Page<>(query.getPage(), query.getSize());
        IPage<OrderResponse> result = orderMapper.selectOrderPage(page,
                query.getOrderNo(), query.getOrderType(), query.getStatus(),
                query.getStatusList(),
                query.getMemberId(), query.getStartTime(), query.getEndTime());
        result.getRecords().forEach(this::enrichOrderLabels);
        return PageResult.from(result);
    }

    @Override
    public OrderDetailResponse getOrderDetail(Long id) {
        OrderDetailResponse detail = orderMapper.selectDetailById(id);
        if (detail == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        // Enrich labels
        detail.setOrderTypeLabel(getOrderTypeLabel(
                detail.getOrderType() != null ? detail.getOrderType().byteValue() : null));
        detail.setStatusLabel(getOrderStatusLabel(
                detail.getStatus() != null ? detail.getStatus().byteValue() : null));
        detail.setChangeAmount(BigDecimal.ZERO);

        // Load order items
        List<OrderItem> items = orderItemMapper.selectByOrderId(id);
        detail.setItems(items.stream().map(this::convertItemResponse).collect(Collectors.toList()));
        // Load payment record
        PaymentRecord payment = paymentRecordMapper.selectByOrderId(id);
        if (payment != null) {
            detail.setPayment(convertPaymentResponse(payment));
            if (payment.getPaymentMethod() == CommonConstants.PAY_TYPE_CASH
                    && payment.getCashReceived() != null) {
                detail.setChangeAmount(payment.getCashReceived().subtract(payment.getTotalAmount()));
            }
        }
        // Load refund records
        List<RefundRecord> refunds = refundRecordMapper.selectByOrderId(id);
        if (!CollectionUtils.isEmpty(refunds)) {
            detail.setRefunds(refunds.stream().map(this::convertRefundResponse).collect(Collectors.toList()));
        }
        return detail;
    }

    @Override
    @Transactional
    public OrderDetailResponse createOrder(OrderCreateRequest request, Long operatorId) {
        if (CollectionUtils.isEmpty(request.getItems())) {
            throw new BusinessException(ErrorCode.ORDER_ITEM_EMPTY);
        }

        Order order = new Order();
        order.setOrderNo(NumberGenerator.generateOrderNo());
        order.setOrderType(request.getOrderType() != null
                ? request.getOrderType().byteValue()
                : (byte) CommonConstants.ORDER_TYPE_PRODUCT);
        order.setMemberId(request.getMemberId());
        order.setOperatorId(operatorId);
        order.setRemark(request.getRemark());
        order.setStatus((byte) CommonConstants.ORDER_STATUS_PENDING);
        order.setTotalAmount(BigDecimal.ZERO);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setPayableAmount(BigDecimal.ZERO);
        order.setPaidAmount(BigDecimal.ZERO);

        orderMapper.insert(order);
        log.info("create order: id={}, orderNo={}", order.getId(), order.getOrderNo());

        // Insert order items — query product table for name and price
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;
        for (OrderItemRequest itemReq : request.getItems()) {
            if (itemReq.getQuantity() == null || itemReq.getQuantity() <= 0) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "商品数量必须大于0");
            }
            // Look up product info from product table
            ProductInfo product = productInfoMapper.selectById(itemReq.getProductId());
            if (product == null) {
                throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
            }

            // Determine unit price: use member price if member is bound, otherwise retail price
            BigDecimal unitPrice = (request.getMemberId() != null && product.getMemberPrice() != null)
                    ? product.getMemberPrice()
                    : product.getRetailPrice();
            if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "商品价格不合法");
            }

            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(itemReq.getQuantity()));
            BigDecimal discount = itemReq.getDiscount() != null ? itemReq.getDiscount() : BigDecimal.ZERO;
            if (discount.compareTo(BigDecimal.ZERO) < 0 || discount.compareTo(subtotal) > 0) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "折扣金额必须在0到商品小计之间");
            }

            // Validate product is active
            if (product.getIsActive() == null || product.getIsActive() != 1) {
                throw new BusinessException(ErrorCode.PRODUCT_OFF_SHELF);
            }

            OrderItem item = new OrderItem();
            item.setOrderId(order.getId());
            item.setItemType((byte) CommonConstants.ITEM_TYPE_PRODUCT);
            item.setProductId(itemReq.getProductId());
            item.setProductName(product.getProductName());
            item.setUnitPrice(unitPrice);
            item.setQuantity(itemReq.getQuantity());
            item.setSubtotal(subtotal);
            item.setDiscount(discount);

            totalAmount = totalAmount.add(subtotal);
            totalDiscount = totalDiscount.add(discount);
            orderItemMapper.insert(item);
        }

        order.setTotalAmount(totalAmount);
        order.setDiscountAmount(totalDiscount);
        order.setPayableAmount(totalAmount.subtract(totalDiscount));
        if (order.getPayableAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "订单应付金额必须大于0");
        }
        requireOrderUpdated(order);

        return getOrderDetail(order.getId());
    }

    @Override
    @Transactional
    public OrderDetailResponse payOrder(Long orderId, PaymentRequest request, Long operatorId) {
        Order order = orderMapper.selectByIdForUpdate(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (order.getStatus() == null || order.getStatus().byteValue() != (byte) CommonConstants.ORDER_STATUS_PENDING) {
            if (order.getStatus() != null && order.getStatus().byteValue() == (byte) CommonConstants.ORDER_STATUS_PAID) {
                throw new BusinessException(ErrorCode.ORDER_ALREADY_PAID);
            }
            throw new BusinessException(ErrorCode.ORDER_REFUND_DENIED);
        }

        // Idempotency check
        if (request.getIdempotentKey() != null && !request.getIdempotentKey().isEmpty()) {
            LambdaQueryWrapper<PaymentRecord> w = new LambdaQueryWrapper<>();
            w.eq(PaymentRecord::getIdempotentKey, request.getIdempotentKey());
            if (paymentRecordMapper.selectCount(w) > 0) {
                throw new BusinessException(ErrorCode.PAYMENT_DUPLICATE);
            }
        }

        // 在线支付尚未接入可信回调，不能由请求直接标记为已收款。
        if (request.getPaymentMethod() == null
                || (request.getPaymentMethod() != CommonConstants.PAY_TYPE_CASH
                    && request.getPaymentMethod() != CommonConstants.PAY_TYPE_BALANCE)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "当前仅支持现金或会员余额支付");
        }
        if (order.getPayableAmount() == null || order.getPayableAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "订单应付金额不合法");
        }
        BigDecimal cashReceived = null;
        if (request.getPaymentMethod() == CommonConstants.PAY_TYPE_CASH) {
            cashReceived = request.getPaidAmount() == null ? order.getPayableAmount() : request.getPaidAmount();
            if (cashReceived.compareTo(order.getPayableAmount()) < 0 || cashReceived.scale() > 2) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "现金实收金额不能低于应付金额且最多两位小数");
            }
        } else if (request.getPaidAmount() != null
                && request.getPaidAmount().compareTo(order.getPayableAmount()) != 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "余额支付金额必须等于应付金额");
        }

        PaymentRecord payment = new PaymentRecord();
        payment.setPaymentNo(NumberGenerator.generatePaymentNo());
        payment.setOrderId(orderId);
        payment.setMemberId(order.getMemberId());
        payment.setPaymentMethod(request.getPaymentMethod().byteValue());
        payment.setTotalAmount(order.getPayableAmount());
        payment.setCashReceived(cashReceived);
        payment.setRefundAmount(BigDecimal.ZERO);
        payment.setPaymentStatus((byte) CommonConstants.PAY_STATUS_SUCCESS);
        payment.setPaidAt(LocalDateTime.now());
        payment.setOperatorId(operatorId);
        payment.setIdempotentKey(request.getIdempotentKey() != null
                ? request.getIdempotentKey()
                : NumberGenerator.generateIdempotentKey());

        paymentRecordMapper.insert(payment);
        log.info("payment success: orderId={}, paymentNo={}", orderId, payment.getPaymentNo());

        // 余额支付：扣减会员余额（订单必须已绑定会员）
        if (request.getPaymentMethod() == CommonConstants.PAY_METHOD_BALANCE) {
            if (order.getMemberId() == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "余额支付需先绑定会员");
            }
            balanceService.deductBalance(order.getMemberId(), order.getPayableAmount(),
                    (byte) CommonConstants.BALANCE_BIZ_CONSUME, orderId, "收银消费");
        }

        order.setStatus((byte) CommonConstants.ORDER_STATUS_PAID);
        order.setPaidAmount(order.getPayableAmount());
        order.setPaidAt(LocalDateTime.now());
        requireOrderUpdated(order);

        // 现金/微信/支付宝直接消费获得成长值（1元=1经验，向下取整）；余额支付不获得经验
        if (order.getMemberId() != null
                && request.getPaymentMethod() != null
                && request.getPaymentMethod() != CommonConstants.PAY_METHOD_BALANCE) {
            growthValueService.addGrowth(order.getMemberId(),
                    order.getPayableAmount().setScale(0, RoundingMode.DOWN).intValue(),
                    (byte) CommonConstants.GROWTH_BIZ_CONSUME, orderId, "收银消费获得成长值");
        }

        // Update active shift counters
        updateShiftOnTransaction(operatorId, order.getPayableAmount(),
                request.getPaymentMethod(), true);

        return getOrderDetail(orderId);
    }

    @Override
    @Transactional
    public RefundResponse refundOrder(Long orderId, RefundRequest request, Long operatorId) {
        Order order = orderMapper.selectByIdForUpdate(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (order.getStatus() == null
                || (order.getStatus().byteValue() != (byte) CommonConstants.ORDER_STATUS_PAID
                    && order.getStatus().byteValue() != (byte) CommonConstants.ORDER_STATUS_PARTIAL_REFUND)) {
            throw new BusinessException(ErrorCode.ORDER_REFUND_DENIED);
        }

        PaymentRecord payment = paymentRecordMapper.selectByOrderId(orderId);
        if (payment == null) {
            throw new BusinessException(ErrorCode.ORDER_REFUND_DENIED);
        }
        int refundMethod = payment.getPaymentMethod();
        if (refundMethod != CommonConstants.REFUND_METHOD_CASH
                && refundMethod != CommonConstants.REFUND_METHOD_BALANCE) {
            throw new BusinessException(ErrorCode.ORDER_REFUND_DENIED.getCode(), "线上支付退款尚未接入支付通道");
        }
        if (request.getRefundMethod() != null && request.getRefundMethod() != refundMethod) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "退款必须退回原支付方式");
        }
        if (request.getRefundAmount() == null || request.getRefundAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "退款金额必须大于0");
        }
        BigDecimal alreadyRefunded = payment.getRefundAmount() == null ? BigDecimal.ZERO : payment.getRefundAmount();
        alreadyRefunded = alreadyRefunded.max(getAlreadyRefundedAmount(orderId));
        BigDecimal maxRefundable = payment.getTotalAmount().subtract(alreadyRefunded);
        if (request.getRefundAmount().compareTo(maxRefundable) > 0) {
            throw new BusinessException(ErrorCode.REFUND_AMOUNT_EXCEED);
        }

        BigDecimal cumulativeRefund = alreadyRefunded.add(request.getRefundAmount());
        int refundType = cumulativeRefund.compareTo(payment.getTotalAmount()) == 0
                ? CommonConstants.REFUND_TYPE_FULL
                : CommonConstants.REFUND_TYPE_PARTIAL;

        RefundRecord refund = new RefundRecord();
        refund.setRefundNo(NumberGenerator.generateRefundNo());
        refund.setOrderId(orderId);
        refund.setMemberId(order.getMemberId());
        refund.setRefundAmount(request.getRefundAmount());
        refund.setRefundType((byte) refundType);
        refund.setRefundReason(request.getRefundReason());
        refund.setRefundMethod((byte) refundMethod);
        refund.setStatus((byte) CommonConstants.REFUND_STATUS_COMPLETED);
        refund.setAuditBy(operatorId);
        refund.setAuditAt(LocalDateTime.now());
        refund.setOperatorId(operatorId);

        // Update payment record
        refund.setPaymentId(payment.getId());
        payment.setRefundAmount(cumulativeRefund);
        if (cumulativeRefund.compareTo(payment.getTotalAmount()) == 0) {
            payment.setPaymentStatus((byte) CommonConstants.PAY_STATUS_REFUNDED);
        }
        if (paymentRecordMapper.updateById(payment) != 1) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), "支付记录已变更，请重试");
        }

        refundRecordMapper.insert(refund);
        log.info("refund success: orderId={}, refundNo={}", orderId, refund.getRefundNo());

        // 退回余额：退款金额加回会员账户
        if (refundMethod == CommonConstants.REFUND_METHOD_BALANCE) {
            if (order.getMemberId() == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "退回余额需订单绑定会员");
            }
            balanceService.addBalance(order.getMemberId(), request.getRefundAmount(),
                    (byte) CommonConstants.BALANCE_BIZ_REFUND, orderId, "订单退款");
        }

        if (refundType == CommonConstants.REFUND_TYPE_FULL) {
            order.setStatus((byte) CommonConstants.ORDER_STATUS_REFUNDED);
        } else {
            order.setStatus((byte) CommonConstants.ORDER_STATUS_PARTIAL_REFUND);
        }
        requireOrderUpdated(order);

        // Update active shift counters for refund
        updateShiftOnTransaction(operatorId, request.getRefundAmount(), refundMethod, false);

        return convertRefundResponse(refund);
    }

    @Override
    @Transactional
    public void cancelOrder(Long orderId, Long operatorId) {
        Order order = orderMapper.selectByIdForUpdate(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (order.getStatus() == null || order.getStatus().byteValue() != (byte) CommonConstants.ORDER_STATUS_PENDING) {
            throw new BusinessException(ErrorCode.ORDER_NOT_CANCELABLE);
        }
        order.setStatus((byte) CommonConstants.ORDER_STATUS_CANCELLED);
        requireOrderUpdated(order);
        log.info("cancel order: id={}", orderId);
    }

    @Override
    public PageResult<RefundResponse> getRefundPage(RefundQueryRequest query) {
        Page<RefundResponse> page = new Page<>(query.getPage(), query.getSize());
        IPage<RefundResponse> result = refundRecordMapper.selectRefundPage(page,
                query.getOrderId(), query.getStatus(),
                query.getStartTime(), query.getEndTime());
        result.getRecords().forEach(this::enrichRefundLabels);
        return PageResult.from(result);
    }

    // ==================== Private Helpers ====================

    private void requireOrderUpdated(Order order) {
        if (orderMapper.updateById(order) != 1) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), "订单已变更，请重试");
        }
    }

    private BigDecimal getAlreadyRefundedAmount(Long orderId) {
        List<RefundRecord> existing = refundRecordMapper.selectByOrderId(orderId);
        if (CollectionUtils.isEmpty(existing)) {
            return BigDecimal.ZERO;
        }
        return existing.stream()
                .filter(r -> r.getStatus() != CommonConstants.REFUND_STATUS_REJECTED)
                .map(RefundRecord::getRefundAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Update the active shift's cash counters when a payment or refund occurs.
     * @param operatorId the employee who processed the transaction
     * @param amount the transaction amount
     * @param paymentMethod 1=cash, 2=balance
     * @param isPayment true for payment, false for refund
     */
    private void updateShiftOnTransaction(Long operatorId, BigDecimal amount,
                                          int paymentMethod, boolean isPayment) {
        if (operatorId == null) {
            return;
        }
        CashierShift shift = shiftMapper.selectActiveShiftForUpdate(operatorId);
        if (shift == null) {
            return;
        }
        if (paymentMethod == CommonConstants.PAY_METHOD_CASH) {
            if (isPayment) {
                shift.setCashIncome(shift.getCashIncome().add(amount));
            } else {
                shift.setCashExpenditure(shift.getCashExpenditure().add(amount));
            }
        }
        // totalIncome tracks net income (payments - refunds) regardless of method
        if (isPayment) {
            shift.setTotalIncome(shift.getTotalIncome().add(amount));
        } else {
            shift.setTotalIncome(shift.getTotalIncome().subtract(amount));
        }
        shift.setOrderCount(shift.getOrderCount() + (isPayment ? 1 : 0));
        if (shiftMapper.updateById(shift) != 1) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), "班次数据已变更，请重试");
        }
    }

    private void enrichOrderLabels(OrderResponse r) {
        if (r.getOrderType() != null) {
            r.setOrderTypeLabel(getOrderTypeLabel(r.getOrderType().byteValue()));
        }
        if (r.getStatus() != null) {
            r.setStatusLabel(getOrderStatusLabel(r.getStatus().byteValue()));
        }
    }

    private void enrichRefundLabels(RefundResponse r) {
        if (r.getRefundType() != null) {
            r.setRefundTypeLabel(r.getRefundType() == 1 ? "\u5168\u989d\u9000\u6b3e" : "\u90e8\u5206\u9000\u6b3e");
        }
        if (r.getRefundMethod() != null) r.setRefundMethodLabel(getRefundMethodLabel(r.getRefundMethod().byteValue()));
        if (r.getStatus() != null) {
            String label = switch (r.getStatus()) {
                case 0 -> "\u5f85\u5ba1\u6838";
                case 1 -> "\u5df2\u5ba1\u6838";
                case 2 -> "\u5df2\u5b8c\u6210";
                case 3 -> "\u5df2\u62d2\u7edd";
                default -> "\u672a\u77e5";
            };
            r.setStatusLabel(label);
        }
    }

    private OrderItemResponse convertItemResponse(OrderItem item) {
        OrderItemResponse r = new OrderItemResponse();
        r.setId(item.getId());
        r.setOrderId(item.getOrderId());
        r.setItemType(item.getItemType() != null ? item.getItemType().intValue() : null);
        r.setItemTypeLabel(getItemTypeLabel(item.getItemType()));
        r.setProductId(item.getProductId());
        r.setProductName(item.getProductName());
        r.setUnitPrice(item.getUnitPrice());
        r.setQuantity(item.getQuantity());
        r.setSubtotal(item.getSubtotal());
        r.setDiscount(item.getDiscount());
        return r;
    }

    private PaymentRecordResponse convertPaymentResponse(PaymentRecord p) {
        PaymentRecordResponse r = new PaymentRecordResponse();
        r.setId(p.getId());
        r.setPaymentNo(p.getPaymentNo());
        r.setOrderId(p.getOrderId());
        r.setMemberId(p.getMemberId());
        r.setPaymentMethod(p.getPaymentMethod() != null ? p.getPaymentMethod().intValue() : null);
        r.setPaymentMethodLabel(getPaymentMethodLabel(p.getPaymentMethod()));
        r.setTradeNo(p.getTradeNo());
        r.setTotalAmount(p.getTotalAmount());
        r.setCashReceived(p.getCashReceived());
        r.setRefundAmount(p.getRefundAmount());
        r.setPaymentStatus(p.getPaymentStatus() != null ? p.getPaymentStatus().intValue() : null);
        String sLabel = switch (p.getPaymentStatus() != null ? p.getPaymentStatus() : 0) {
            case 0 -> "\u5f85\u652f\u4ed8";
            case 1 -> "\u652f\u4ed8\u6210\u529f";
            case 2 -> "\u652f\u4ed8\u5931\u8d25";
            case 3 -> "\u5df2\u9000\u6b3e";
            default -> "\u672a\u77e5";
        };
        r.setPaymentStatusLabel(sLabel);
        r.setPaidAt(p.getPaidAt());
        r.setOperatorId(p.getOperatorId());
        r.setCreatedAt(p.getCreatedAt());
        return r;
    }

    private RefundResponse convertRefundResponse(RefundRecord r) {
        RefundResponse resp = new RefundResponse();
        resp.setId(r.getId());
        resp.setRefundNo(r.getRefundNo());
        resp.setOrderId(r.getOrderId());
        resp.setPaymentId(r.getPaymentId());
        resp.setMemberId(r.getMemberId());
        resp.setRefundAmount(r.getRefundAmount());
        resp.setRefundType(r.getRefundType() != null ? r.getRefundType().intValue() : null);
        resp.setRefundTypeLabel(r.getRefundType() != null && r.getRefundType() == 1 ? "\u5168\u989d\u9000\u6b3e" : "\u90e8\u5206\u9000\u6b3e");
        resp.setRefundReason(r.getRefundReason());
        resp.setRefundMethod(r.getRefundMethod() != null ? r.getRefundMethod().intValue() : null);
        resp.setRefundMethodLabel(getRefundMethodLabel(r.getRefundMethod()));
        resp.setStatus(r.getStatus() != null ? r.getStatus().intValue() : null);
        String sLabel = switch (r.getStatus() != null ? r.getStatus() : 0) {
            case 0 -> "\u5f85\u5ba1\u6838";
            case 1 -> "\u5df2\u5ba1\u6838";
            case 2 -> "\u5df2\u5b8c\u6210";
            case 3 -> "\u5df2\u62d2\u7edd";
            default -> "\u672a\u77e5";
        };
        resp.setStatusLabel(sLabel);
        resp.setAuditBy(r.getAuditBy());
        resp.setAuditAt(r.getAuditAt());
        resp.setOperatorId(r.getOperatorId());
        resp.setCreatedAt(r.getCreatedAt());
        return resp;
    }

    private String getOrderTypeLabel(Byte type) {
        if (type == null) return "\u672a\u77e5";
        return switch (type) {
            case 1 -> "\u5546\u54c1\u9500\u552e";
            case 2 -> "\u4e0a\u673a\u7ed3\u7b97";
            case 3 -> "\u5145\u503c";
            case 4 -> "\u5957\u9910";
            default -> "\u672a\u77e5";
        };
    }

    private String getPaymentMethodLabel(Byte method) {
        if (method == null) return "未知";
        return switch (method) {
            case 1 -> "现金";
            case 2 -> "微信";
            case 3 -> "支付宝";
            case 4 -> "会员余额";
            default -> "未知";
        };
    }

    private String getRefundMethodLabel(Byte method) {
        if (method == null) return "未知";
        return switch (method) {
            case 1 -> "现金退回";
            case 2 -> "微信原路退回";
            case 3 -> "支付宝原路退回";
            case 4 -> "退回会员余额";
            default -> "未知";
        };
    }

    private String getOrderStatusLabel(Byte status) {
        if (status == null) return "\u672a\u77e5";
        return switch (status) {
            case 0 -> "\u5f85\u652f\u4ed8";
            case 1 -> "\u5df2\u652f\u4ed8";
            case 2 -> "\u90e8\u5206\u9000\u6b3e";
            case 3 -> "\u5df2\u9000\u6b3e";
            case 4 -> "\u5df2\u53d6\u6d88";
            default -> "\u672a\u77e5";
        };
    }

    private String getItemTypeLabel(Byte type) {
        if (type == null) return "\u672a\u77e5";
        return switch (type) {
            case 1 -> "\u5546\u54c1";
            case 2 -> "\u4e0a\u673a\u65f6\u957f";
            case 3 -> "\u5305\u65f6\u6bb5";
            case 4 -> "\u5957\u9910";
            default -> "\u672a\u77e5";
        };
    }
}
