package com.starlink.cashier.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class RefundRequest {

    /** 退款金额 */
    @NotNull(message = "退款金额不能为空")
    @Positive(message = "退款金额必须大于0")
    private BigDecimal refundAmount;

    /** 退款原因 */
    private String refundReason;

    /** 退款方式：1-现金退 2-退余额 */
    private Integer refundMethod;
}
