package com.starlink.product.dto.resp;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ProductResponse {

    private Long id;

    private Long categoryId;

    /** 分类名称（用于前端展示） */
    private String categoryName;

    private String productCode;

    private String productName;

    private Integer productType;

    /** 商品类型标签（1-食品, 2-饮料, 3-虚拟商品, 4-日用品, 5-网游点卡） */
    private String productTypeLabel;

    private String unit;

    private BigDecimal costPrice;

    private BigDecimal retailPrice;

    private BigDecimal memberPrice;

    private String imageUrl;

    private Integer isVipOnly;

    private Integer isActive;

    /** 上架状态标签（0-下架, 1-上架） */
    private String isActiveLabel;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
