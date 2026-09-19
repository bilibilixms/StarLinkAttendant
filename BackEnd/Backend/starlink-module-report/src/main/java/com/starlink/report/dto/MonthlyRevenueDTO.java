package com.starlink.report.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * REP-02 月度营收 DTO。
 */
@Data
public class MonthlyRevenueDTO {

    /** 月份（yyyy-MM） */
    private String month;

    /** 总营收 */
    private BigDecimal totalRevenue;

    /** 上机营收 */
    private BigDecimal onlineRevenue;

    /** 商品营收 */
    private BigDecimal productRevenue;

    /** 充值营收 */
    private BigDecimal rechargeRevenue;

    /** 订单数 */
    private int totalOrders;
}
