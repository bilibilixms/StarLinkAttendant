package com.starlink.report.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * REP-04 商品销量排行 DTO。
 */
@Data
public class ProductRankingDTO {

    /** 商品名称 */
    private String productName;

    /** 总销量 */
    private int totalQuantity;

    /** 总销售额 */
    private BigDecimal totalAmount;

    /** 订单数（去重） */
    private int orderCount;
}
