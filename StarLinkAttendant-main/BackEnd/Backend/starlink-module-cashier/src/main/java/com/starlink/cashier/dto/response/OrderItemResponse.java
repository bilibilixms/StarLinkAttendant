package com.starlink.cashier.dto.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderItemResponse {

    private Long id;
    private Long orderId;
    private Integer itemType;
    private String itemTypeLabel;
    private Long productId;
    private String productName;
    private BigDecimal unitPrice;
    private Integer quantity;
    private BigDecimal subtotal;
    private BigDecimal discount;
}
