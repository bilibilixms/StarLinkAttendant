package com.starlink.system.mapper;

import com.starlink.system.dto.resp.DashboardSeatStats;
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

    /**
     * 正常机位：启用且运营状态为 0-空闲或 1-使用中。
     * 使用中以真正处于 0-上机中的会话为准，临时下机不计入使用率；
     * 空闲须没有 0/1 状态的会话，避免机位状态字段滞后造成误判。
     */
    @Select("SELECT COUNT(*) AS normal_seats, " +
            "COALESCE(SUM(CASE WHEN EXISTS (" +
            "  SELECT 1 FROM session s WHERE s.computer_id = c.id " +
            "  AND s.status = 0 AND s.deleted_at IS NULL" +
            ") THEN 1 ELSE 0 END), 0) AS in_use_seats, " +
            "COALESCE(SUM(CASE WHEN NOT EXISTS (" +
            "  SELECT 1 FROM session s WHERE s.computer_id = c.id " +
            "  AND s.status IN (0, 1) AND s.deleted_at IS NULL" +
            ") THEN 1 ELSE 0 END), 0) AS available_seats " +
            "FROM computer c WHERE c.is_active = 1 AND c.status IN (0, 1) " +
            "AND c.deleted_at IS NULL")
    DashboardSeatStats countSeatStats();

    /** 今日新注册会员数 */
    @Select("SELECT COUNT(*) FROM member WHERE deleted_at IS NULL AND DATE(created_at) = CURDATE()")
    Long countTodayNewMembers();

    /** 今日上机人次（今日创建的会话数） */
    @Select("SELECT COUNT(*) FROM session WHERE deleted_at IS NULL AND DATE(start_time) = CURDATE()")
    Long countTodaySessions();
}
