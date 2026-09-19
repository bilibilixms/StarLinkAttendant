package com.starlink.cashier.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PaymentRecordResponse {

    private Long id;
    private String paymentNo;
    private Long orderId;
    private Long memberId;
    private Integer paymentMethod;
    private String paymentMethodLabel;
    private String tradeNo;
    private BigDecimal totalAmount;
    private BigDecimal refundAmount;
    private Integer paymentStatus;
    private String paymentStatusLabel;
    private LocalDateTime paidAt;
    private Long operatorId;
    private LocalDateTime createdAt;
}
