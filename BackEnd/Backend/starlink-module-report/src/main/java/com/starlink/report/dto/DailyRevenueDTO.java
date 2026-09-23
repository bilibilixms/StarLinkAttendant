package com.starlink.report.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * REP-02 每日营收 DTO。
 */
@Data
public class DailyRevenueDTO {

    /** 日期 */
    private String date;

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
