package com.starlink.member.dto.resp;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RechargeRecordResponse {

    private Long id;

    private String rechargeNo;

    private Long memberId;

    private String memberName;

    private BigDecimal rechargeAmount;

    private BigDecimal bonusAmount;

    private BigDecimal totalAmount;

    private BigDecimal balanceBefore;

    private BigDecimal balanceAfter;

    private Byte paymentMethod;

    private String paymentMethodLabel;

    private Byte status;

    private String statusLabel;

    private LocalDateTime paidAt;

    private LocalDateTime createdAt;
}