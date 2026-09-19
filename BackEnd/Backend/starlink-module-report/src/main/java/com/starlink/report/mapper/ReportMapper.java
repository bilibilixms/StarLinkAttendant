package com.starlink.report.mapper;

import com.starlink.report.dto.*;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 报表数据查询 Mapper。
 * 直接 SQL 查询各业务表，避免跨模块依赖。
 */
public interface ReportMapper {

    // ==================== REP-01 营业总览 ====================

    /** 今日日结数据 */
    @Select("SELECT total_revenue, online_revenue, product_revenue, recharge_revenue, " +
            "total_orders, total_sessions, peak_concurrent, avg_occupancy_rate, cash_amount, balance_amount " +
            "FROM daily_settlement WHERE settle_date = #{date} AND deleted_at IS NULL LIMIT 1")
    Map<String, Object> selectTodaySettlement(@Param("date") LocalDate date);

    /** 当前在线数（上机中 + 临时下机） */
    @Select("SELECT COUNT(*) FROM session WHERE status IN (0, 1) AND deleted_at IS NULL")
    int selectOnlineCount();

    /** 今日订单数 */
    @Select("SELECT COUNT(*) FROM orders WHERE DATE(paid_at) = #{date} AND status = 1 AND deleted_at IS NULL")
    int selectTodayOrderCount(@Param("date") LocalDate date);

    /** 今日营收 */
    @Select("SELECT COALESCE(SUM(paid_amount), 0) FROM orders WHERE DATE(paid_at) = #{date} AND status = 1 AND deleted_at IS NULL")
    java.math.BigDecimal selectTodayRevenue(@Param("date") LocalDate date);

    /** 客单价（今日平均每单金额） */
    @Select("SELECT COALESCE(AVG(paid_amount), 0) FROM orders WHERE DATE(paid_at) = #{date} AND status = 1 AND deleted_at IS NULL")
    java.math.BigDecimal selectAvgOrderAmount(@Param("date") LocalDate date);

    /** 上机率（使用中+空闲机位数 / 总启用机位数） */
    @Select("SELECT COALESCE(SUM(CASE WHEN status IN (0, 1) THEN 1 ELSE 0 END) * 100.0 / NULLIF(COUNT(*), 0), 0) " +
            "FROM computer WHERE is_active = 1 AND deleted_at IS NULL")
    java.math.BigDecimal selectOccupancyRate();

    // ==================== REP-02 营收分析 ====================

    /** 按天统计营收（日结表） */
    @Select("SELECT settle_date AS date, total_revenue, online_revenue, product_revenue, recharge_revenue, total_orders " +
            "FROM daily_settlement WHERE settle_date BETWEEN #{start} AND #{end} AND deleted_at IS NULL ORDER BY settle_date")
    List<DailyRevenueDTO> selectDailyRevenue(@Param("start") LocalDate start, @Param("end") LocalDate end);

    /** 按月统计营收（日结表） */
    @Select("SELECT DATE_FORMAT(settle_date, '%Y-%m') AS month, " +
            "SUM(total_revenue) AS totalRevenue, SUM(online_revenue) AS onlineRevenue, " +
            "SUM(product_revenue) AS productRevenue, SUM(recharge_revenue) AS rechargeRevenue, " +
            "SUM(total_orders) AS totalOrders " +
            "FROM daily_settlement WHERE settle_date BETWEEN #{start} AND #{end} AND deleted_at IS NULL " +
            "GROUP BY DATE_FORMAT(settle_date, '%Y-%m') ORDER BY month")
    List<MonthlyRevenueDTO> selectMonthlyRevenue(@Param("start") LocalDate start, @Param("end") LocalDate end);

    /** 按日期从 orders 表按类型统计营收（兜底用） */
    @Select("SELECT order_type AS orderType, COALESCE(SUM(paid_amount), 0) AS amount " +
            "FROM orders WHERE DATE(paid_at) = #{date} AND status = 1 AND deleted_at IS NULL " +
            "GROUP BY order_type")
    List<Map<String, Object>> selectRevenueByTypeForDate(@Param("date") LocalDate date);

    /** 按订单类型统计营收 */
    @Select("SELECT order_type AS type, COALESCE(SUM(paid_amount), 0) AS amount, COUNT(*) AS orderCount " +
            "FROM orders WHERE paid_at BETWEEN #{start} AND #{end} AND status = 1 AND deleted_at IS NULL " +
            "GROUP BY order_type ORDER BY order_type")
    List<RevenueTypeDTO> selectRevenueByType(@Param("start") String start, @Param("end") String end);

    /** 同比月度营收（去年同期） */
    @Select("SELECT DATE_FORMAT(settle_date, '%Y-%m') AS month, " +
            "SUM(total_revenue) AS totalRevenue " +
            "FROM daily_settlement WHERE settle_date BETWEEN #{start} AND #{end} AND deleted_at IS NULL " +
            "GROUP BY DATE_FORMAT(settle_date, '%Y-%m') ORDER BY month")
    List<MonthlyRevenueDTO> selectYoYMonthlyRevenue(@Param("start") LocalDate start, @Param("end") LocalDate end);

    // ==================== REP-03 上机分析 ====================

    /** 按小时统计上机次数 */
    @Select("SELECT HOUR(created_at) AS hour, COUNT(*) AS sessionCount " +
            "FROM session WHERE DATE(created_at) = #{date} AND deleted_at IS NULL " +
            "GROUP BY HOUR(created_at) ORDER BY hour")
    List<HourlySessionDTO> selectHourlySessions(@Param("date") LocalDate date);

    /** 平均上机时长（分钟） */
    @Select("SELECT COALESCE(AVG(TIMESTAMPDIFF(MINUTE, start_time, end_time)), 0) " +
            "FROM session WHERE DATE(created_at) = #{date} AND status IN (2, 3) AND end_time IS NOT NULL AND deleted_at IS NULL")
    int selectAvgSessionDuration(@Param("date") LocalDate date);

    /** 高峰时段（按小时统计并发数） */
    @Select("SELECT HOUR(start_time) AS hour, COUNT(*) AS activeCount " +
            "FROM session WHERE DATE(start_time) = #{date} AND deleted_at IS NULL " +
            "GROUP BY HOUR(start_time) ORDER BY activeCount DESC LIMIT 24")
    List<HourlySessionDTO> selectPeakHours(@Param("date") LocalDate date);

    /** 总启用机位数 */
    @Select("SELECT COUNT(*) FROM computer WHERE is_active = 1 AND deleted_at IS NULL")
    int selectTotalActiveComputers();

    /** 各时段开机率（使用中机位数/总机位数） */
    @Select("SELECT HOUR(s.start_time) AS hour, COUNT(DISTINCT s.computer_id) AS activeCount " +
            "FROM session s WHERE DATE(s.start_time) = #{date} AND s.status = 0 AND s.deleted_at IS NULL " +
            "GROUP BY HOUR(s.start_time) ORDER BY hour")
    List<HourlySessionDTO> selectHourlyComputerUsage(@Param("date") LocalDate date);

    // ==================== REP-04 商品排行 ====================

    /** 商品销量排行 */
    @Select("SELECT oi.product_name AS productName, SUM(oi.quantity) AS totalQuantity, " +
            "SUM(oi.subtotal) AS totalAmount, COUNT(DISTINCT oi.order_id) AS orderCount " +
            "FROM order_item oi JOIN orders o ON oi.order_id = o.id " +
            "WHERE oi.item_type = 1 AND oi.deleted_at IS NULL AND o.deleted_at IS NULL " +
            "AND o.paid_at BETWEEN #{start} AND #{end} " +
            "GROUP BY oi.product_name ORDER BY totalAmount DESC LIMIT #{limit}")
    List<ProductRankingDTO> selectProductRanking(@Param("start") String start, @Param("end") String end, @Param("limit") int limit);

    /** 商品利润排行（关联 product 表获取成本价） */
    @Select("SELECT oi.product_name AS productName, SUM(oi.quantity) AS totalQuantity, " +
            "SUM(oi.subtotal) AS totalAmount, SUM((oi.unit_price - COALESCE(p.cost_price, 0)) * oi.quantity) AS totalProfit, " +
            "COUNT(DISTINCT oi.order_id) AS orderCount " +
            "FROM order_item oi " +
            "JOIN orders o ON oi.order_id = o.id " +
            "LEFT JOIN product p ON oi.product_id = p.id " +
            "WHERE oi.item_type = 1 AND oi.deleted_at IS NULL AND o.deleted_at IS NULL " +
            "AND o.paid_at BETWEEN #{start} AND #{end} " +
            "GROUP BY oi.product_name ORDER BY totalProfit DESC LIMIT #{limit}")
    List<ProfitRankingDTO> selectProfitRanking(@Param("start") String start, @Param("end") String end, @Param("limit") int limit);

    // ==================== REP-05 会员分析 ====================

    /** 新增会员统计 */
    @Select("SELECT DATE(created_at) AS date, COUNT(*) AS newCount " +
            "FROM member WHERE created_at BETWEEN #{start} AND #{end} AND deleted_at IS NULL " +
            "GROUP BY DATE(created_at) ORDER BY date")
    List<MemberTrendDTO> selectNewMembers(@Param("start") String start, @Param("end") String end);

    /** 活跃会员统计（有消费的会员数） */
    @Select("SELECT DATE(o.paid_at) AS date, COUNT(DISTINCT o.member_id) AS activeCount " +
            "FROM orders o WHERE o.paid_at BETWEEN #{start} AND #{end} AND o.member_id IS NOT NULL AND o.deleted_at IS NULL " +
            "GROUP BY DATE(o.paid_at) ORDER BY date")
    List<MemberTrendDTO> selectActiveMembers(@Param("start") String start, @Param("end") String end);

    /** 会员总数 */
    @Select("SELECT COUNT(*) FROM member WHERE deleted_at IS NULL")
    int selectTotalMembers();

    /** 会员充值总额 */
    @Select("SELECT COALESCE(SUM(recharge_amount), 0) FROM member_recharge " +
            "WHERE paid_at BETWEEN #{start} AND #{end} AND status = 1 AND deleted_at IS NULL")
    java.math.BigDecimal selectTotalRecharge(@Param("start") String start, @Param("end") String end);

    /** 复购会员数（周期内 >= 2 次消费的会员） */
    @Select("SELECT COUNT(*) FROM (" +
            "SELECT o.member_id FROM orders o " +
            "WHERE o.paid_at BETWEEN #{start} AND #{end} AND o.member_id IS NOT NULL AND o.status = 1 AND o.deleted_at IS NULL " +
            "GROUP BY o.member_id HAVING COUNT(*) >= 2) t")
    int selectRepurchaseMembers(@Param("start") String start, @Param("end") String end);

    /** 总消费会员数 */
    @Select("SELECT COUNT(DISTINCT member_id) FROM orders " +
            "WHERE paid_at BETWEEN #{start} AND #{end} AND member_id IS NOT NULL AND status = 1 AND deleted_at IS NULL")
    int selectTotalActiveMembersRange(@Param("start") String start, @Param("end") String end);

    /** 充值行为趋势（按日） */
    @Select("SELECT DATE(paid_at) AS date, COUNT(*) AS count, COALESCE(SUM(recharge_amount), 0) AS amount " +
            "FROM member_recharge WHERE paid_at BETWEEN #{start} AND #{end} AND status = 1 AND deleted_at IS NULL " +
            "GROUP BY DATE(paid_at) ORDER BY date")
    List<RechargeTrendDTO> selectRechargeTrend(@Param("start") String start, @Param("end") String end);

    /** 会员生命周期价值 Top N */
    @Select("SELECT m.id AS memberId, m.member_no AS memberNo, m.real_name AS memberName, " +
            "m.balance, m.total_consumption AS totalConsumption, m.total_recharge AS totalRecharge, " +
            "m.total_online_hours AS totalOnlineHours " +
            "FROM member m WHERE m.deleted_at IS NULL " +
            "ORDER BY m.total_consumption DESC LIMIT #{limit}")
    List<MemberLTVDTO> selectMemberLTV(@Param("limit") int limit);

    // ==================== REP-06 财务报表 ====================

    /** 营业收入汇总（按支付方式，使用 payment_record） */
    @Select("SELECT COALESCE(SUM(CASE WHEN payment_method = 1 THEN total_amount ELSE 0 END), 0) AS cashIncome, " +
            "COALESCE(SUM(CASE WHEN payment_method = 2 THEN total_amount ELSE 0 END), 0) AS balanceIncome, " +
            "COALESCE(SUM(total_amount), 0) AS totalIncome " +
            "FROM payment_record WHERE payment_status = 1 AND paid_at BETWEEN #{start} AND #{end} AND deleted_at IS NULL")
    Map<String, Object> selectIncomeBreakdown(@Param("start") String start, @Param("end") String end);

    /** 退款总额 */
    @Select("SELECT COALESCE(SUM(refund_amount), 0) FROM refund_record " +
            "WHERE status = 2 AND created_at BETWEEN #{start} AND #{end} AND deleted_at IS NULL")
    java.math.BigDecimal selectTotalRefund(@Param("start") String start, @Param("end") String end);

    /** 采购支出 */
    @Select("SELECT COALESCE(SUM(total_amount), 0) FROM purchase_order " +
            "WHERE status = 2 AND created_at BETWEEN #{start} AND #{end} AND deleted_at IS NULL")
    java.math.BigDecimal selectTotalPurchase(@Param("start") String start, @Param("end") String end);

    /** 会员储值余额（负债） */
    @Select("SELECT COALESCE(SUM(balance), 0) FROM member WHERE deleted_at IS NULL")
    java.math.BigDecimal selectTotalMemberBalance();

    /** 库存总值 */
    @Select("SELECT COALESCE(SUM(i.quantity * p.cost_price), 0) " +
            "FROM inventory i JOIN product p ON i.product_id = p.id " +
            "WHERE i.deleted_at IS NULL AND p.deleted_at IS NULL")
    java.math.BigDecimal selectTotalInventoryValue();

    /** 最新收银班次余额 */
    @Select("SELECT cash_actual FROM cashier_shift WHERE status = 1 AND deleted_at IS NULL ORDER BY close_at DESC LIMIT 1")
    java.math.BigDecimal selectLatestCashBalance();

    /** 会员充值流入 */
    @Select("SELECT COALESCE(SUM(recharge_amount), 0) FROM member_recharge " +
            "WHERE status = 1 AND paid_at BETWEEN #{start} AND #{end} AND deleted_at IS NULL")
    java.math.BigDecimal selectRechargeInflow(@Param("start") String start, @Param("end") String end);

    /** 现金支出（退款） */
    @Select("SELECT COALESCE(SUM(refund_amount), 0) FROM refund_record " +
            "WHERE refund_method = 1 AND status = 2 AND created_at BETWEEN #{start} AND #{end} AND deleted_at IS NULL")
    java.math.BigDecimal selectCashOutflow(@Param("start") String start, @Param("end") String end);

    /** 日结表营收汇总（利润表用） */
    @Select("SELECT COALESCE(SUM(total_revenue), 0) AS totalRevenue, " +
            "COALESCE(SUM(online_revenue), 0) AS onlineRevenue, " +
            "COALESCE(SUM(product_revenue), 0) AS productRevenue, " +
            "COALESCE(SUM(recharge_revenue), 0) AS rechargeRevenue, " +
            "COALESCE(SUM(total_refund), 0) AS totalRefund " +
            "FROM daily_settlement WHERE settle_date BETWEEN #{start} AND #{end} AND deleted_at IS NULL")
    Map<String, Object> selectDailySettlementSummary(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
