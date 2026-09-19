package com.starlink.cashier.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class OrderCreateRequest {

    /** 会员ID（NULL=散客） */
    private Long memberId;

    /** 订单类型：1-商品销售 4-套餐 */
    private Integer orderType;

    /** 备注 */
    private String remark;

    /** 订单明细 */
    @NotEmpty(message = "订单明细不能为空")
    @Valid
    private List<OrderItemRequest> items;
}
