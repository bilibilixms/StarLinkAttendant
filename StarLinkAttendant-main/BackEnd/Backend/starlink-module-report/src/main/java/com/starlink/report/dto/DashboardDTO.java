package com.starlink.report.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * REP-01 营业总览 DTO — 仪表盘核心 KPI 卡片数据。
 */
@Data
public class DashboardDTO {

    /** 今日总营收 */
    private BigDecimal totalRevenue;

    /** 上机营收 */
    private BigDecimal onlineRevenue;

    /** 商品营收 */
    private BigDecimal productRevenue;

    /** 充值营收 */
    private BigDecimal rechargeRevenue;

    /** 当前在线人数 */
    private int onlineCount;

    /** 今日订单数 */
    private int todayOrderCount;

    /** 今日上机会话数 */
    private int totalSessions;

    /** 峰值并发数 */
    private int peakConcurrent;

    /** 平均上座率 */
    private BigDecimal avgOccupancyRate;

    /** 现金收款 */
    private BigDecimal cashAmount;

    /** 余额支付 */
    private BigDecimal balanceAmount;

    /** 今日营收（订单维度） */
    private BigDecimal todayRevenue;

    /** 客单价（平均每单金额） */
    private BigDecimal avgOrderAmount;

    /** 上机率（使用中+空闲 / 总启用机器，百分比） */
    private BigDecimal occupancyRate;
}
