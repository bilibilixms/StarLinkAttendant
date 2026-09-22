package com.starlink.member.dto.req;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 会员余额消费请求（小程序自助点餐等场景）。
 */
@Data
public class BalanceConsumeRequest {

    @NotNull(message = "消费金额不能为空")
    @DecimalMin(value = "0.01", message = "消费金额必须大于0")
    private BigDecimal amount;

    /** 业务单据ID（如订单号），可为空 */
    private Long bizId;

    /** 备注，可为空 */
    private String remark;
}
