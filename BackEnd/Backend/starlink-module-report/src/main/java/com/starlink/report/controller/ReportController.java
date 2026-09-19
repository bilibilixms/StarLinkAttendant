package com.starlink.report.controller;

import com.starlink.common.result.Result;
import com.starlink.report.dto.*;
import com.starlink.report.service.ReportService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 经营报表控制器。
 * <p>
 * 提供营业总览、营收分析、上机分析、商品排行、会员分析、财务报表等 REST API。
 *
 */
@Slf4j
@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    // ==================== REP-01 营业总览 ====================

    @GetMapping("/dashboard")
    public Result<DashboardDTO> getDashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate queryDate = date != null ? date : LocalDate.now();
        log.info("查询营业总览，日期: {}", queryDate);
        DashboardDTO result = reportService.getDashboard(queryDate);
        return Result.ok(result, "查询成功");
    }

    // ==================== REP-02 营收分析 ====================

    @GetMapping("/revenue/daily")
    public Result<List<DailyRevenueDTO>> getDailyRevenue(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        log.info("查询每日营收趋势，范围: {} ~ {}", start, end);
        List<DailyRevenueDTO> result = reportService.getDailyRevenue(start, end);
        return Result.ok(result, "查询成功");
    }

    @GetMapping("/revenue/monthly")
    public Result<List<MonthlyRevenueDTO>> getMonthlyRevenue(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        log.info("查询月度营收趋势，范围: {} ~ {}", start, end);
        List<MonthlyRevenueDTO> result = reportService.getMonthlyRevenue(start, end);
        return Result.ok(result, "查询成功");
    }

    @GetMapping("/revenue/by-type")
    public Result<List<RevenueTypeDTO>> getRevenueByType(
            @RequestParam String start,
            @RequestParam String end) {
        log.info("查询按类型营收，范围: {} ~ {}", start, end);
        List<RevenueTypeDTO> result = reportService.getRevenueByType(start, end);
        return Result.ok(result, "查询成功");
    }

    @GetMapping("/revenue/yoy")
    public Result<List<MonthlyRevenueDTO>> getYoYMonthlyRevenue(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        log.info("查询同比月度营收，范围: {} ~ {}", start, end);
        List<MonthlyRevenueDTO> result = reportService.getYoYMonthlyRevenue(start, end);
        return Result.ok(result, "查询成功");
    }

    // ==================== REP-03 上机分析 ====================

    @GetMapping("/session/hourly")
    public Result<List<HourlySessionDTO>> getHourlySessions(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        log.info("查询时段上机分布，日期: {}", date);
        List<HourlySessionDTO> result = reportService.getHourlySessions(date);
        return Result.ok(result, "查询成功");
    }

    @GetMapping("/session/avg-duration")
    public Result<Map<String, Object>> getAvgSessionDuration(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        log.info("查询平均上机时长，日期: {}", date);
        int duration = reportService.getAvgSessionDuration(date);
        return Result.ok(Map.of("date", date.toString(), "avgDurationMinutes", duration), "查询成功");
    }

    @GetMapping("/session/peak")
    public Result<List<HourlySessionDTO>> getPeakHours(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        log.info("查询高峰时段，日期: {}", date);
        List<HourlySessionDTO> result = reportService.getPeakHours(date);
        return Result.ok(result, "查询成功");
    }

    @GetMapping("/session/computer-utilization")
    public Result<Map<String, Object>> getComputerUtilization(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        log.info("查询各时段开机率，日期: {}", date);
        Map<String, Object> result = reportService.getComputerUtilization(date);
        return Result.ok(result, "查询成功");
    }

    // ==================== REP-04 商品排行 ====================

    @GetMapping("/product-ranking")
    public Result<List<ProductRankingDTO>> getProductRanking(
            @RequestParam String start,
            @RequestParam String end,
            @RequestParam(defaultValue = "10") int limit) {
        log.info("查询商品销量排行，范围: {} ~ {}, Top: {}", start, end, limit);
        List<ProductRankingDTO> result = reportService.getProductRanking(start, end, limit);
        return Result.ok(result, "查询成功");
    }

    @GetMapping("/product-ranking/profit")
    public Result<List<ProfitRankingDTO>> getProfitRanking(
            @RequestParam String start,
            @RequestParam String end,
            @RequestParam(defaultValue = "10") int limit) {
        log.info("查询商品利润排行，范围: {} ~ {}, Top: {}", start, end, limit);
        List<ProfitRankingDTO> result = reportService.getProfitRanking(start, end, limit);
        return Result.ok(result, "查询成功");
    }

    // ==================== REP-05 会员分析 ====================

    @GetMapping("/member/new")
    public Result<List<MemberTrendDTO>> getNewMembers(
            @RequestParam String start,
            @RequestParam String end) {
        log.info("查询新增会员趋势，范围: {} ~ {}", start, end);
        List<MemberTrendDTO> result = reportService.getNewMembers(start, end);
        return Result.ok(result, "查询成功");
    }

    @GetMapping("/member/active")
    public Result<List<MemberTrendDTO>> getActiveMembers(
            @RequestParam String start,
            @RequestParam String end) {
        log.info("查询活跃会员趋势，范围: {} ~ {}", start, end);
        List<MemberTrendDTO> result = reportService.getActiveMembers(start, end);
        return Result.ok(result, "查询成功");
    }

    @GetMapping("/member/totals")
    public Result<Map<String, Object>> getMemberTotals(
            @RequestParam String start,
            @RequestParam String end) {
        log.info("查询会员汇总，范围: {} ~ {}", start, end);
        Map<String, Object> result = reportService.getMemberTotals(start, end);
        return Result.ok(result, "查询成功");
    }

    @GetMapping("/member/recharge-trend")
    public Result<List<RechargeTrendDTO>> getRechargeTrend(
            @RequestParam String start,
            @RequestParam String end) {
        log.info("查询充值趋势，范围: {} ~ {}", start, end);
        List<RechargeTrendDTO> result = reportService.getRechargeTrend(start, end);
        return Result.ok(result, "查询成功");
    }

    @GetMapping("/member/ltv")
    public Result<List<MemberLTVDTO>> getMemberLTV(
            @RequestParam(defaultValue = "20") int limit) {
        log.info("查询会员 LTV Top {}", limit);
        List<MemberLTVDTO> result = reportService.getMemberLTV(limit);
        return Result.ok(result, "查询成功");
    }

    // ==================== REP-06 财务报表 ====================

    @GetMapping("/finance")
    public Result<FinanceDTO> getFinance(
            @RequestParam String start,
            @RequestParam String end) {
        log.info("查询财务报表，范围: {} ~ {}", start, end);
        FinanceDTO result = reportService.getFinance(start, end);
        return Result.ok(result, "查询成功");
    }

    // ==================== REP-07 数据导出 ====================

    @GetMapping("/export")
    public void exportReport(
            @RequestParam int reportType,
            @RequestParam String start,
            @RequestParam String end,
            HttpServletResponse response) throws IOException {
        log.info("导出报表，类型: {}, 范围: {} ~ {}", reportType, start, end);

        byte[] data = reportService.exportReport(reportType, start, end, "xlsx");
        String typeName = switch (reportType) {
            case 1 -> "营业总览";
            case 2 -> "每日营收";
            case 3 -> "上机分析";
            case 4 -> "商品排行";
            case 5 -> "会员分析";
            case 6 -> "财务报表";
            default -> "报表";
        };
        String fileName = URLEncoder.encode(typeName + ".xlsx", StandardCharsets.UTF_8);

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName);
        response.setContentLength(data.length);
        response.getOutputStream().write(data);
    }
}
