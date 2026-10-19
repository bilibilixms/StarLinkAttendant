package com.starlink.session.service;

import com.starlink.common.exception.BusinessException;
import com.starlink.member.service.BalanceService;
import com.starlink.session.dto.req.ForceEndRequest;
import com.starlink.session.dto.req.SessionEndRequest;
import com.starlink.session.entity.BillingRecord;
import com.starlink.session.entity.Computer;
import com.starlink.session.entity.Session;
import com.starlink.session.entity.SessionTiming;
import com.starlink.session.mapper.BillingRecordMapper;
import com.starlink.session.mapper.ComputerMapper;
import com.starlink.session.mapper.SessionMapper;
import com.starlink.session.mapper.SessionTimingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionSettlementTest {
    @Mock private SessionMapper sessionMapper;
    @Mock private SessionTimingMapper sessionTimingMapper;
    @Mock private ComputerMapper computerMapper;
    @Mock private BillingRecordMapper billingRecordMapper;
    @Mock private BalanceService balanceService;
    @Mock private ReservationService reservationService;
    @Mock private TariffCalculator tariffCalculator;
    @Mock private Clock clock;
    @InjectMocks private SessionService service;

    private Session session;

    @BeforeEach
    void setup() {
        session = new Session();
        session.setId(1L);
        session.setMemberId(3L);
        session.setComputerId(2L);
        session.setTariffPlanId(4L);
        session.setStartTime(LocalDateTime.of(2026, 10, 17, 10, 0));
        session.setStatus((byte) 0);
        session.setDiscountAmount(BigDecimal.ZERO);
        session.setPaidAmount(BigDecimal.ZERO);
        when(clock.instant()).thenReturn(Instant.parse("2026-10-17T11:05:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(sessionMapper.selectByIdForUpdate(1L)).thenReturn(session);
        when(computerMapper.selectByIdForUpdate(2L)).thenReturn(new Computer());
        SessionTiming timing = new SessionTiming();
        timing.setSessionId(1L);
        timing.setTimingType((byte) 1);
        timing.setStartTime(session.getStartTime());
        when(sessionTimingMapper.selectList(any())).thenReturn(List.of(timing));
        when(tariffCalculator.calculate(65, 4L)).thenReturn(new BigDecimal("20.00"));
    }

    @Test
    void normalEndRequiresSuccessfulWalletDebitBeforeSettlement() {
        doThrow(new BusinessException(400, "余额不足"))
                .when(balanceService).deductBalance(eq(3L), eq(new BigDecimal("20.00")), anyByte(), eq(1L), anyString());
        SessionEndRequest request = new SessionEndRequest();
        request.setSessionId(1L);

        assertThrows(BusinessException.class, () -> service.endSession(request));
        verify(sessionMapper, never()).updateById(any());
        verify(billingRecordMapper, never()).insert(any());
    }

    @Test
    void forcedEndRecordsActualPartialPaymentAsUnsettled() {
        when(balanceService.deductAvailableBalance(eq(3L), eq(new BigDecimal("20.00")),
                anyByte(), eq(1L), anyString())).thenReturn(new BigDecimal("5.00"));
        when(sessionMapper.selectById(1L)).thenReturn(session);
        ForceEndRequest request = new ForceEndRequest();
        request.setSessionId(1L);
        request.setReason("余额不足");

        service.forceEndSession(request);

        ArgumentCaptor<BillingRecord> record = ArgumentCaptor.forClass(BillingRecord.class);
        verify(billingRecordMapper).insert(record.capture());
        assertEquals(new BigDecimal("5.00"), session.getPaidAmount());
        assertEquals((byte) 0, record.getValue().getIsSettled());
        assertEquals(new BigDecimal("20.00"), record.getValue().getFinalAmount());
    }
}
