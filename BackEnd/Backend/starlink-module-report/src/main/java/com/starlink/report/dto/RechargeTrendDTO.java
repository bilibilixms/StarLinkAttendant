package com.starlink.report.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * REP-05 充值趋势 DTO。
 */
@Data
public class RechargeTrendDTO {

    /** 日期 */
    private String date;

    /** 充值笔数 */
    private int count;

    /** 充值金额 */
    private BigDecimal amount;
}
