package com.starlink.product.dto.resp;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ComboItemResponse {

    private Long id;

    private Long comboId;

    private Long productId;

    private String productName;

    private BigDecimal unitPrice;

    private Integer quantity;

    private BigDecimal subtotal;
}
