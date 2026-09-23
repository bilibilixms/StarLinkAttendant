package com.starlink.cashier.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.starlink.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("daily_settlement")
public class DailySettlement extends BaseEntity {

    @TableField("settle_date")
    private LocalDate settleDate;

    @TableField("settle_no")
    private String settleNo;

    @TableField("total_revenue")
    private BigDecimal totalRevenue;

    @TableField("online_revenue")
    private BigDecimal onlineRevenue;

    @TableField("product_revenue")
    private BigDecimal productRevenue;

    @TableField("recharge_revenue")
    private BigDecimal rechargeRevenue;

    @TableField("total_recharge")
    private BigDecimal totalRecharge;

    @TableField("total_refund")
    private BigDecimal totalRefund;

    @TableField("total_orders")
    private Integer totalOrders;

    @TableField("total_sessions")
    private Integer totalSessions;

    @TableField("peak_concurrent")
    private Integer peakConcurrent;

    @TableField("avg_occupancy_rate")
    private BigDecimal avgOccupancyRate;

    @TableField("cash_amount")
    private BigDecimal cashAmount;

    @TableField("balance_amount")
    private BigDecimal balanceAmount;

    @TableField("status")
    private Byte status;

    @TableField("confirm_by")
    private Long confirmBy;

    @TableField("confirmed_at")
    private LocalDateTime confirmedAt;
}
