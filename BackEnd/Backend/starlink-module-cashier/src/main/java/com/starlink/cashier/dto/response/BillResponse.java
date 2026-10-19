package com.starlink.cashier.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Three existing transaction records presented through one read-only ledger. */
@Data
public class BillResponse {
    private Long id;
    private String source;
    private String category;
    private String billNo;
    private Long memberId;
    private String memberName;
    private Long sessionId;
    private String computerNo;
    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private BigDecimal payableAmount;
    private BigDecimal paidAmount;
    private BigDecimal refundAmount;
    private BigDecimal netAmount;
    private BigDecimal bonusAmount;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private Integer paymentMethod;
    private Integer status;
    private Integer billedMinutes;
    private LocalDateTime billingStart;
    private LocalDateTime billingEnd;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
    private String remark;
}
