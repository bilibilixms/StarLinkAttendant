package com.starlink.report.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * REP-04 利润排行 DTO（关联 product.cost_price 计算毛利）。
 */
@Data
public class ProfitRankingDTO {

    /** 商品名称 */
    private String productName;

    /** 总销量 */
    private int totalQuantity;

    /** 总销售额 */
    private BigDecimal totalAmount;

    /** 总利润 */
    private BigDecimal totalProfit;

    /** 订单数（去重） */
    private int orderCount;
}
