package com.starlink.report.dto;

import lombok.Data;

/**
 * REP-05 会员趋势 DTO — 新增会员/活跃会员 按日统计。
 */
@Data
public class MemberTrendDTO {

    /** 日期 */
    private String date;

    /** 新增会员数 */
    private int newCount;

    /** 活跃会员数 */
    private int activeCount;
}
