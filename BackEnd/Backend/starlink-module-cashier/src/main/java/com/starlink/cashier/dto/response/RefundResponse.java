package com.starlink.cashier.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RefundResponse {

    private Long id;
    private String refundNo;
    private Long orderId;
    private String orderNo;
    private Long paymentId;
    private Long memberId;
    private BigDecimal refundAmount;
    private Integer refundType;
    private String refundTypeLabel;
    private String refundReason;
    private Integer refundMethod;
    private String refundMethodLabel;
    private Integer status;
    private String statusLabel;
    private Long auditBy;
    private LocalDateTime auditAt;
    private Long operatorId;
    private LocalDateTime createdAt;
}
