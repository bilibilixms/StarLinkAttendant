package com.starlink.member.dto.req;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class RechargeRequest {

    @NotNull(message = "会员ID不能为空")
    private Long memberId;

    @NotNull(message = "充值金额不能为空")
    @Min(value = 1, message = "充值金额最小为1元")
    private BigDecimal amount;

    @NotNull(message = "支付方式不能为空")
    private Byte paymentMethod;

    private Long operatorId;

    private Long campaignId;
}