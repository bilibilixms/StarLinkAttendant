package com.starlink.system.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

/**
 * 仪表盘统计 Mapper（跨模块只读查询）。
 *
 */
@Mapper
public interface DashboardMapper {

    /** 当前在线人数：status=0（上机中）的会话数 */
    @Select("SELECT COUNT(*) FROM session WHERE status = 0 AND deleted_at IS NULL")
    Long countOnlineSessions();

    /** 今日营收：今日已下机/强制下机会话的 paid_amount 总和 */
    @Select("SELECT COALESCE(SUM(paid_amount), 0) FROM session " +
            "WHERE status IN (2, 3) AND deleted_at IS NULL " +
            "AND DATE(end_time) = CURDATE()")
    BigDecimal sumTodayRevenue();

    /** 会员总数 */
    @Select("SELECT COUNT(*) FROM member WHERE deleted_at IS NULL")
    Long countMembers();

    /** 今日商品销量：order_type=1 且今日已支付的订单数 */
    @Select("SELECT COUNT(*) FROM orders WHERE order_type = 1 AND status = 1 " +
            "AND deleted_at IS NULL AND DATE(paid_at) = CURDATE()")
    Long countTodaySales();

    /** 机位总数（启用状态） */
    @Select("SELECT COUNT(*) FROM computer WHERE is_active = 1 AND deleted_at IS NULL")
    Long countTotalSeats();

    /** 空闲机位数（status=0 且启用） */
    @Select("SELECT COUNT(*) FROM computer WHERE status = 0 AND is_active = 1 AND deleted_at IS NULL")
    Long countAvailableSeats();

    /** 今日新注册会员数 */
    @Select("SELECT COUNT(*) FROM member WHERE deleted_at IS NULL AND DATE(created_at) = CURDATE()")
    Long countTodayNewMembers();

    /** 今日上机人次（今日创建的会话数） */
    @Select("SELECT COUNT(*) FROM session WHERE deleted_at IS NULL AND DATE(start_time) = CURDATE()")
    Long countTodaySessions();
}
