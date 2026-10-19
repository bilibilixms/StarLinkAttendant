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

    /** 可省略；服务端始终按原支付方式退款，传入不一致时拒绝。 */
    private Integer refundMethod;
}
