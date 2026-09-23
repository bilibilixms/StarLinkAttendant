package com.starlink.cashier.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class SettlementResponse {

    private Long id;
    private LocalDate settleDate;
    private String settleNo;
    private BigDecimal totalRevenue;
    private BigDecimal onlineRevenue;
    private BigDecimal productRevenue;
    private BigDecimal rechargeRevenue;
    private BigDecimal totalRecharge;
    private BigDecimal totalRefund;
    private Integer totalOrders;
    private Integer totalSessions;
    private Integer peakConcurrent;
    private BigDecimal avgOccupancyRate;
    private BigDecimal cashAmount;
    private BigDecimal balanceAmount;
    private Integer status;
    private String statusLabel;
    private Long confirmBy;
    private LocalDateTime confirmedAt;
    private LocalDateTime createdAt;
}
