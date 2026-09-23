package com.starlink.report.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * REP-05 会员生命周期价值（LTV）DTO。
 */
@Data
public class MemberLTVDTO {

    /** 会员ID */
    private Long memberId;

    /** 会员编号 */
    private String memberNo;

    /** 会员姓名 */
    private String memberName;

    /** 当前余额 */
    private BigDecimal balance;

    /** 累计消费 */
    private BigDecimal totalConsumption;

    /** 累计充值 */
    private BigDecimal totalRecharge;

    /** 累计上机时长（分钟） */
    private int totalOnlineHours;
}
