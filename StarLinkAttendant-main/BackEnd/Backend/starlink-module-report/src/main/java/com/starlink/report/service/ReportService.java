package com.starlink.report.service;

import com.starlink.report.dto.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 报表服务接口。
 * <p>
 * 提供营业总览、营收分析、上机分析、商品排行、会员分析、财务报表等查询。
 *
 */
public interface ReportService {

    /** REP-01 营业总览仪表盘 */
    DashboardDTO getDashboard(LocalDate date);

    /** REP-02 每日营收趋势（日结表） */
    List<DailyRevenueDTO> getDailyRevenue(LocalDate start, LocalDate end);

    /** REP-02 月度营收趋势（日结表） */
    List<MonthlyRevenueDTO> getMonthlyRevenue(LocalDate start, LocalDate end);

    /** REP-02 按订单类型统计营收 */
    List<RevenueTypeDTO> getRevenueByType(String start, String end);

    /** REP-02 同比月度营收（去年同月） */
    List<MonthlyRevenueDTO> getYoYMonthlyRevenue(LocalDate start, LocalDate end);

    /** REP-03 时段上机分布 */
    List<HourlySessionDTO> getHourlySessions(LocalDate date);

    /** REP-03 平均上机时长（分钟） */
    int getAvgSessionDuration(LocalDate date);

    /** REP-03 高峰时段 */
    List<HourlySessionDTO> getPeakHours(LocalDate date);

    /** REP-03 各时段开机率 */
    Map<String, Object> getComputerUtilization(LocalDate date);

    /** REP-04 商品销量排行 */
    List<ProductRankingDTO> getProductRanking(String start, String end, int limit);

    /** REP-04 商品利润排行 */
    List<ProfitRankingDTO> getProfitRanking(String start, String end, int limit);

    /** REP-05 新增会员趋势 */
    List<MemberTrendDTO> getNewMembers(String start, String end);

    /** REP-05 活跃会员趋势 */
    List<MemberTrendDTO> getActiveMembers(String start, String end);

    /** REP-05 会员汇总（总数 + 充值总额 + 复购率） */
    Map<String, Object> getMemberTotals(String start, String end);

    /** REP-05 充值趋势 */
    List<RechargeTrendDTO> getRechargeTrend(String start, String end);

    /** REP-05 会员 LTV Top N */
    List<MemberLTVDTO> getMemberLTV(int limit);

    /** REP-06 财务报表（利润表 + 资产负债表 + 现金流量表） */
    FinanceDTO getFinance(String start, String end);

    /** REP-07 数据导出（根据类型导出 CSV 或 Excel） */
    byte[] exportReport(int reportType, String start, String end, String format);
}
