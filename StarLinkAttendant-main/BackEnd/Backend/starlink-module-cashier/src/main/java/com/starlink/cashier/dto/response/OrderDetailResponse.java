package com.starlink.cashier.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderDetailResponse {

    private Long id;
    private String orderNo;
    private Integer orderType;
    private String orderTypeLabel;
    private Long memberId;
    private String memberName;
    private Long sessionId;
    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private BigDecimal payableAmount;
    private BigDecimal paidAmount;
    private BigDecimal changeAmount;
    private Integer status;
    private String statusLabel;
    private String remark;
    private Long operatorId;
    private String operatorName;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private List<OrderItemResponse> items;
    private PaymentRecordResponse payment;
    private List<RefundResponse> refunds;
}
