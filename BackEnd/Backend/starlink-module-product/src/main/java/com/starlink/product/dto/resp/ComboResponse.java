package com.starlink.product.dto.resp;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ComboResponse {

    private Long id;

    private String comboName;

    private String comboCode;

    private String description;

    private BigDecimal originalPrice;

    private BigDecimal comboPrice;

    private String imageUrl;

    private Integer isActive;

    /** 上架状态标签（0-下架, 1-上架） */
    private String isActiveLabel;

    private Integer sortOrder;

    /** 套餐包含的商品列表 */
    private List<ComboItemResponse> items;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
