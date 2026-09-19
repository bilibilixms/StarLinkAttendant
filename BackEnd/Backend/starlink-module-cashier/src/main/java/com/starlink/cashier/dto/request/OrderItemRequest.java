package com.starlink.cashier.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderItemRequest {

    /** 商品ID */
    @NotNull(message = "商品ID不能为空")
    private Long productId;

    /** 数量 */
    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量至少为1")
    private Integer quantity;

    /** 行级优惠金额 */
    @DecimalMin(value = "0", message = "折扣金额不能为负数")
    private BigDecimal discount;
}
