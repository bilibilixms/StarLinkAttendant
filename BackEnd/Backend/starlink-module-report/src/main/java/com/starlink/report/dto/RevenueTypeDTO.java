package com.starlink.report.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * REP-02 按订单类型营收 DTO。
 */
@Data
public class RevenueTypeDTO {

    /** 订单类型：1-商品销售 2-上机结算 3-充值 4-套餐 */
    private int type;

    /** 营收金额 */
    private BigDecimal amount;

    /** 订单数 */
    private int orderCount;
}
