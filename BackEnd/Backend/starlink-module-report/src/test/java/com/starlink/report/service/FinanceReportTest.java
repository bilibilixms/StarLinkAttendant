package com.starlink.report.service;

import com.starlink.report.dto.FinanceDTO;
import com.starlink.report.mapper.ReportMapper;
import com.starlink.report.service.impl.ReportServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FinanceReportTest {
    @Test
    void paymentIncomeSeparatesMethodsAndSubtractsRefundOnce() {
        ReportMapper mapper = mock(ReportMapper.class);
        when(mapper.selectIncomeBreakdown(any(), any())).thenReturn(Map.of(
                "cashIncome", new BigDecimal("10"),
                "balanceIncome", new BigDecimal("20"),
                "onlineIncome", new BigDecimal("5"),
                "totalIncome", new BigDecimal("35")));
        when(mapper.selectTotalRefund(any(), any())).thenReturn(new BigDecimal("7"));

        FinanceDTO result = new ReportServiceImpl(mapper).getFinance(
                "2026-10-17 00:00:00", "2026-10-17 23:59:59");

        assertEquals(new BigDecimal("35"), result.getTotalIncome());
        assertEquals(new BigDecimal("20"), result.getBalanceIncome());
        assertEquals(new BigDecimal("5"), result.getOnlineIncome());
        assertEquals(new BigDecimal("28"), result.getNetIncome());
        verify(mapper, never()).selectDailySettlementSummary(any(), any());
    }

    @Test
    void settlementFallbackDoesNotGuessPaymentMethodsOrDoubleCountRechargeCash() {
        ReportMapper mapper = mock(ReportMapper.class);
        when(mapper.selectIncomeBreakdown(any(), any())).thenReturn(Map.of("totalIncome", BigDecimal.ZERO));
        when(mapper.selectDailySettlementSummary(any(), any())).thenReturn(Map.of(
                "onlineRevenue", new BigDecimal("20"),
                "productRevenue", new BigDecimal("30"),
                "rechargeRevenue", new BigDecimal("10"),
                "cashAmount", new BigDecimal("40"),
                "balanceAmount", new BigDecimal("10")));
        when(mapper.selectRechargeInflow(any(), any())).thenReturn(new BigDecimal("10"));
        when(mapper.selectTotalRefund(any(), any())).thenReturn(new BigDecimal("5"));

        FinanceDTO result = new ReportServiceImpl(mapper).getFinance(
                "2026-10-17 00:00:00", "2026-10-17 23:59:59");

        assertEquals(new BigDecimal("60"), result.getTotalIncome());
        assertEquals(new BigDecimal("10"), result.getOnlineIncome());
        assertEquals(new BigDecimal("55"), result.getNetIncome());
        assertEquals(new BigDecimal("30"), result.getOperatingInflow());
        assertEquals(new BigDecimal("40"), result.getTotalInflow());
    }
}
