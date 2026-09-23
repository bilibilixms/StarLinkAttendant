package com.starlink.report.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * REP-06 财务报表 DTO — 利润表 + 资产负债表 + 现金流量表。
 */
@Data
public class FinanceDTO {

    // ========== 利润表（收入-费用=利润） ==========

    /** 营业收入合计 */
    private BigDecimal totalIncome;

    /** 现金收入 */
    private BigDecimal cashIncome;

    /** 余额收入 */
    private BigDecimal balanceIncome;

    /** 退款金额（冲减收入） */
    private BigDecimal refundAmount;

    /** 净收入 */
    private BigDecimal netIncome;

    /** 采购支出 */
    private BigDecimal purchaseExpense;

    /** 净利润 */
    private BigDecimal netProfit;

    // ========== 资产负债表 ==========

    /** 库存总值（流动资产） */
    private BigDecimal inventoryValue;

    /** 现金余额（流动资产） */
    private BigDecimal cashBalance;

    /** 总资产 */
    private BigDecimal totalAssets;

    /** 会员储值余额（负债） */
    private BigDecimal memberBalance;

    /** 净资产（所有者权益） */
    private BigDecimal netAssets;

    // ========== 现金流量表 ==========

    /** 经营现金流入（营业收入中现金部分） */
    private BigDecimal operatingInflow;

    /** 充值现金流入 */
    private BigDecimal rechargeInflow;

    /** 现金总流入 */
    private BigDecimal totalInflow;

    /** 现金采购支出 */
    private BigDecimal cashOutflow;

    /** 现金净流量 */
    private BigDecimal netCashFlow;
}
