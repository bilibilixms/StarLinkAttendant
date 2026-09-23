package com.starlink.report.service.impl;

import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import com.starlink.report.dto.*;
import com.starlink.report.mapper.ReportMapper;
import com.starlink.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 报表服务实现。
 *
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ReportMapper reportMapper;

    @Override
    public DashboardDTO getDashboard(LocalDate date) {
        DashboardDTO dto = new DashboardDTO();

        Map<String, Object> settlement = reportMapper.selectTodaySettlement(date);
        if (settlement != null) {
            dto.setTotalRevenue(toBigDecimal(settlement.get("total_revenue")));
            dto.setOnlineRevenue(nvl(toBigDecimal(settlement.get("online_revenue"))));
            dto.setProductRevenue(nvl(toBigDecimal(settlement.get("product_revenue"))));
            dto.setRechargeRevenue(nvl(toBigDecimal(settlement.get("recharge_revenue"))));
            dto.setTotalSessions(toInt(settlement.get("total_sessions")));
            dto.setPeakConcurrent(toInt(settlement.get("peak_concurrent")));
            dto.setAvgOccupancyRate(toBigDecimal(settlement.get("avg_occupancy_rate")));
            dto.setCashAmount(toBigDecimal(settlement.get("cash_amount")));
            dto.setBalanceAmount(toBigDecimal(settlement.get("balance_amount")));
        }

        dto.setOnlineCount(reportMapper.selectOnlineCount());
        dto.setTodayOrderCount(reportMapper.selectTodayOrderCount(date));
        BigDecimal orderRevenue = nvl(reportMapper.selectTodayRevenue(date));
        dto.setTodayRevenue(orderRevenue);
        dto.setAvgOrderAmount(nvl(reportMapper.selectAvgOrderAmount(date)));
        dto.setOccupancyRate(nvl(reportMapper.selectOccupancyRate()));

        // 日结数据不存在时，用订单数据兜底
        if (settlement == null) {
            dto.setTotalRevenue(orderRevenue);
            // 按订单类型统计分类营收作为兜底
            dto.setOnlineRevenue(BigDecimal.ZERO);
            dto.setProductRevenue(BigDecimal.ZERO);
            dto.setRechargeRevenue(BigDecimal.ZERO);
            List<Map<String, Object>> typeRevenue = reportMapper.selectRevenueByTypeForDate(date);
            for (Map<String, Object> row : typeRevenue) {
                int orderType = toInt(row.get("orderType"));
                BigDecimal amount = toBigDecimal(row.get("amount"));
                switch (orderType) {
                    case 2 -> dto.setOnlineRevenue(amount);   // 上机结算
                    case 1, 4 -> dto.setProductRevenue(       // 商品销售 + 套餐归入商品
                            dto.getProductRevenue().add(amount));
                    case 3 -> dto.setRechargeRevenue(amount);  // 充值
                }
            }
        }

        return dto;
    }

    @Override
    public List<DailyRevenueDTO> getDailyRevenue(LocalDate start, LocalDate end) {
        return reportMapper.selectDailyRevenue(start, end);
    }

    @Override
    public List<MonthlyRevenueDTO> getMonthlyRevenue(LocalDate start, LocalDate end) {
        return reportMapper.selectMonthlyRevenue(start, end);
    }

    @Override
    public List<RevenueTypeDTO> getRevenueByType(String start, String end) {
        return reportMapper.selectRevenueByType(start, end);
    }

    @Override
    public List<MonthlyRevenueDTO> getYoYMonthlyRevenue(LocalDate start, LocalDate end) {
        return reportMapper.selectYoYMonthlyRevenue(start, end);
    }

    @Override
    public List<HourlySessionDTO> getHourlySessions(LocalDate date) {
        return reportMapper.selectHourlySessions(date);
    }

    @Override
    public int getAvgSessionDuration(LocalDate date) {
        return reportMapper.selectAvgSessionDuration(date);
    }

    @Override
    public List<HourlySessionDTO> getPeakHours(LocalDate date) {
        return reportMapper.selectPeakHours(date);
    }

    @Override
    public Map<String, Object> getComputerUtilization(LocalDate date) {
        int totalComputers = reportMapper.selectTotalActiveComputers();
        List<HourlySessionDTO> hourlyUsage = reportMapper.selectHourlyComputerUsage(date);

        Map<String, Object> result = new HashMap<>();
        result.put("totalComputers", totalComputers);
        result.put("hourlyUsage", hourlyUsage);
        return result;
    }

    @Override
    public List<ProductRankingDTO> getProductRanking(String start, String end, int limit) {
        return reportMapper.selectProductRanking(start, end, limit);
    }

    @Override
    public List<ProfitRankingDTO> getProfitRanking(String start, String end, int limit) {
        return reportMapper.selectProfitRanking(start, end, limit);
    }

    @Override
    public List<MemberTrendDTO> getNewMembers(String start, String end) {
        return reportMapper.selectNewMembers(start, end);
    }

    @Override
    public List<MemberTrendDTO> getActiveMembers(String start, String end) {
        return reportMapper.selectActiveMembers(start, end);
    }

    @Override
    public Map<String, Object> getMemberTotals(String start, String end) {
        Map<String, Object> result = new HashMap<>();
        result.put("totalMembers", reportMapper.selectTotalMembers());
        result.put("totalRecharge", nvl(reportMapper.selectTotalRecharge(start, end)));

        int activeCount = reportMapper.selectTotalActiveMembersRange(start, end);
        int repurchaseCount = reportMapper.selectRepurchaseMembers(start, end);
        result.put("totalActiveMembers", activeCount);
        result.put("repurchaseMembers", repurchaseCount);
        result.put("repurchaseRate", activeCount > 0
                ? BigDecimal.valueOf(repurchaseCount).divide(BigDecimal.valueOf(activeCount), 4, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO);

        return result;
    }

    @Override
    public List<RechargeTrendDTO> getRechargeTrend(String start, String end) {
        return reportMapper.selectRechargeTrend(start, end);
    }

    @Override
    public List<MemberLTVDTO> getMemberLTV(int limit) {
        return reportMapper.selectMemberLTV(limit);
    }

    @Override
    public FinanceDTO getFinance(String start, String end) {
        FinanceDTO dto = new FinanceDTO();

        // 收入（优先使用 payment_record，补充使用 daily_settlement）
        Map<String, Object> income = reportMapper.selectIncomeBreakdown(start, end);
        BigDecimal cashIncome = toBigDecimal(income.get("cashIncome"));
        BigDecimal balanceIncome = toBigDecimal(income.get("balanceIncome"));
        BigDecimal totalIncome = toBigDecimal(income.get("totalIncome"));

        // 如果 payment_record 无数据，回退到日结表
        if (totalIncome.compareTo(BigDecimal.ZERO) == 0) {
            LocalDate startDate = LocalDate.parse(start.substring(0, 10));
            LocalDate endDate = LocalDate.parse(end.substring(0, 10));
            Map<String, Object> settlement = reportMapper.selectDailySettlementSummary(startDate, endDate);
            if (settlement != null) {
                totalIncome = toBigDecimal(settlement.get("totalRevenue"));
                cashIncome = totalIncome.subtract(balanceIncome); // approximate
            }
        }

        dto.setCashIncome(cashIncome);
        dto.setBalanceIncome(balanceIncome);
        dto.setTotalIncome(totalIncome);

        // 退款
        BigDecimal refund = nvl(reportMapper.selectTotalRefund(start, end));
        dto.setRefundAmount(refund);
        dto.setNetIncome(totalIncome.subtract(refund));

        // 采购支出
        BigDecimal purchase = nvl(reportMapper.selectTotalPurchase(start, end));
        dto.setPurchaseExpense(purchase);
        dto.setNetProfit(dto.getNetIncome().subtract(purchase));

        // 资产负债表
        BigDecimal inventoryValue = nvl(reportMapper.selectTotalInventoryValue());
        BigDecimal cashBalance = nvl(reportMapper.selectLatestCashBalance());
        dto.setInventoryValue(inventoryValue);
        dto.setCashBalance(cashBalance);
        dto.setTotalAssets(inventoryValue.add(cashBalance));

        BigDecimal memberBalance = nvl(reportMapper.selectTotalMemberBalance());
        dto.setMemberBalance(memberBalance);
        dto.setNetAssets(dto.getTotalAssets().subtract(memberBalance));

        // 现金流量表
        BigDecimal rechargeInflow = nvl(reportMapper.selectRechargeInflow(start, end));
        dto.setOperatingInflow(cashIncome);
        dto.setRechargeInflow(rechargeInflow);
        dto.setTotalInflow(cashIncome.add(rechargeInflow));

        BigDecimal cashOutflow = nvl(reportMapper.selectCashOutflow(start, end));
        dto.setCashOutflow(cashOutflow);
        dto.setNetCashFlow(dto.getTotalInflow().subtract(cashOutflow.add(purchase)));

        return dto;
    }

    @Override
    public byte[] exportReport(int reportType, String start, String end, String format) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ExcelWriter writer = ExcelUtil.getWriter(true);
        switch (reportType) {
            case 1 -> writeDashboardExcel(writer, start);
            case 2 -> writeDailyRevenueExcel(writer, start, end);
            case 3 -> writeHourlySessionExcel(writer, start);
            case 4 -> writeProductRankingExcel(writer, start, end);
            case 5 -> writeMemberLTVExcel(writer);
            case 6 -> writeFinanceExcel(writer, start, end);
        }
        writer.flush(bos, true);
        writer.close();
        return bos.toByteArray();
    }

    // ========== Excel writers ==========

    private void writeDashboardExcel(ExcelWriter w, String date) {
        DashboardDTO d = getDashboard(LocalDate.parse(date));
        w.addHeaderAlias("metric", "指标");
        w.addHeaderAlias("value", "数值");
        w.write(List.of(
                mapOf("metric", "总营收", "value", d.getTotalRevenue()),
                mapOf("metric", "上机营收", "value", d.getOnlineRevenue()),
                mapOf("metric", "商品营收", "value", d.getProductRevenue()),
                mapOf("metric", "充值营收", "value", d.getRechargeRevenue()),
                mapOf("metric", "在线人数", "value", d.getOnlineCount()),
                mapOf("metric", "今日订单数", "value", d.getTodayOrderCount()),
                mapOf("metric", "总上机次数", "value", d.getTotalSessions()),
                mapOf("metric", "峰值并发", "value", d.getPeakConcurrent()),
                mapOf("metric", "上机率", "value", d.getOccupancyRate()),
                mapOf("metric", "客单价", "value", d.getAvgOrderAmount())
        ));
    }

    private void writeDailyRevenueExcel(ExcelWriter w, String start, String end) {
        List<DailyRevenueDTO> list = getDailyRevenue(LocalDate.parse(start), LocalDate.parse(end));
        w.addHeaderAlias("date", "日期");
        w.addHeaderAlias("totalRevenue", "总营收");
        w.addHeaderAlias("onlineRevenue", "上机营收");
        w.addHeaderAlias("productRevenue", "商品营收");
        w.addHeaderAlias("rechargeRevenue", "充值营收");
        w.addHeaderAlias("totalOrders", "订单数");
        w.write(list);
    }

    private void writeHourlySessionExcel(ExcelWriter w, String date) {
        List<HourlySessionDTO> list = getHourlySessions(LocalDate.parse(date));
        w.addHeaderAlias("hour", "时段");
        w.addHeaderAlias("sessionCount", "上机次数");
        w.addHeaderAlias("activeCount", "活跃数量");
        w.write(list);
    }

    private void writeProductRankingExcel(ExcelWriter w, String start, String end) {
        List<ProductRankingDTO> list = getProductRanking(start, end, 50);
        w.addHeaderAlias("productName", "商品名称");
        w.addHeaderAlias("totalQuantity", "销量");
        w.addHeaderAlias("totalAmount", "销售额");
        w.addHeaderAlias("orderCount", "订单数");
        w.write(list);
    }

    private void writeMemberLTVExcel(ExcelWriter w) {
        List<MemberLTVDTO> list = getMemberLTV(50);
        w.addHeaderAlias("memberNo", "会员编号");
        w.addHeaderAlias("memberName", "会员姓名");
        w.addHeaderAlias("balance", "余额");
        w.addHeaderAlias("totalConsumption", "累计消费");
        w.addHeaderAlias("totalRecharge", "累计充值");
        w.addHeaderAlias("totalOnlineHours", "上网时长(小时)");
        w.write(list);
    }

    private void writeFinanceExcel(ExcelWriter w, String start, String end) {
        FinanceDTO d = getFinance(start, end);
        w.addHeaderAlias("metric", "科目");
        w.addHeaderAlias("value", "金额");
        w.write(List.of(
                mapOf("metric", "总收入", "value", d.getTotalIncome()),
                mapOf("metric", "现金收入", "value", d.getCashIncome()),
                mapOf("metric", "余额收入", "value", d.getBalanceIncome()),
                mapOf("metric", "退款金额", "value", d.getRefundAmount()),
                mapOf("metric", "净收入", "value", d.getNetIncome()),
                mapOf("metric", "采购支出", "value", d.getPurchaseExpense()),
                mapOf("metric", "净利润", "value", d.getNetProfit()),
                mapOf("metric", "库存价值", "value", d.getInventoryValue()),
                mapOf("metric", "现金余额", "value", d.getCashBalance()),
                mapOf("metric", "总资产", "value", d.getTotalAssets()),
                mapOf("metric", "会员余额", "value", d.getMemberBalance()),
                mapOf("metric", "净资产", "value", d.getNetAssets()),
                mapOf("metric", "经营流入", "value", d.getOperatingInflow()),
                mapOf("metric", "充值流入", "value", d.getRechargeInflow()),
                mapOf("metric", "总流入", "value", d.getTotalInflow()),
                mapOf("metric", "现金流出", "value", d.getCashOutflow()),
                mapOf("metric", "净现金流", "value", d.getNetCashFlow())
        ));
    }

    // ========== helpers ==========

    private Map<String, Object> mapOf(String k1, Object v1, String k2, Object v2) {
        Map<String, Object> m = new HashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        return m;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        return new BigDecimal(value.toString());
    }

    private int toInt(Object value) {
        if (value == null) return 0;
        if (value instanceof Number n) return n.intValue();
        return Integer.parseInt(value.toString());
    }

    private BigDecimal nvl(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
