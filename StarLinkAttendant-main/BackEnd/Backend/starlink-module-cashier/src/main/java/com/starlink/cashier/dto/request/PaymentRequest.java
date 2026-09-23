package com.starlink.cashier.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaymentRequest {

    /** 支付方式：1-现金 2-会员余额 */
    @NotNull(message = "支付方式不能为空")
    private Integer paymentMethod;

    /** 实付金额（现金支付时用于找零计算） */
    private BigDecimal paidAmount;

    /** 幂等键（可选） */
    private String idempotentKey;
}
