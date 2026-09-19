package com.starlink.system.dto.resp;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 首页仪表盘数据响应。
 *
 */
@Data
public class DashboardResponse {

    /** 当前在线人数（上机中的会话数） */
    private Long onlineCount;

    /** 今日营收（今日已结算会话的实付金额总和） */
    private BigDecimal todayRevenue;

    /** 会员总数 */
    private Long memberCount;

    /** 今日商品销量（今日商品销售订单数） */
    private Long todaySalesCount;

    /** 机位总数 */
    private Long totalSeats;

    /** 空闲机位数 */
    private Long availableSeats;

    /** 今日新注册会员数 */
    private Long todayNewMembers;

    /** 今日上机人次（今日创建的会话总数） */
    private Long todaySessionCount;
}
