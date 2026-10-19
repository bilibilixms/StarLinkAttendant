package com.starlink.cashier.service;

import com.starlink.cashier.dto.request.BillQueryRequest;
import com.starlink.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BillServiceTest {

    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final BillService service = new BillService(jdbc);

    @Test
    void rejectsReversedDatesAndUnsupportedDetailsBeforeQuerying() {
        BillQueryRequest query = new BillQueryRequest();
        query.setStartDate(LocalDate.of(2026, 10, 20));
        query.setEndDate(LocalDate.of(2026, 10, 19));
        assertThrows(BusinessException.class, () -> service.list(query));
        assertThrows(BusinessException.class, () -> service.detail("UNKNOWN", 1L));
        verifyNoInteractions(jdbc);
    }

    @Test
    void usesBoundFiltersAndIncludesWholeEndDate() {
        BillQueryRequest query = new BillQueryRequest();
        query.setPage(2);
        query.setSize(10);
        query.setSource("RECHARGE");
        query.setCategory("RECHARGE");
        query.setBillNo("R-123");
        query.setMemberId(7L);
        query.setStatus(1);
        query.setStartDate(LocalDate.of(2026, 10, 1));
        query.setEndDate(LocalDate.of(2026, 10, 19));
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(0L);

        var page = service.list(query);
        assertEquals(0, page.getTotal());
        verify(jdbc).query(anyString(), argThat((MapSqlParameterSource params) ->
                "RECHARGE".equals(params.getValue("source"))
                        && "RECHARGE".equals(params.getValue("category"))
                        && "%R-123%".equals(params.getValue("billNo"))
                        && Long.valueOf(7).equals(params.getValue("memberId"))
                        && Integer.valueOf(1).equals(params.getValue("status"))
                        && LocalDate.of(2026, 10, 1).atStartOfDay().equals(params.getValue("startAt"))
                        && LocalDate.of(2026, 10, 20).atStartOfDay().equals(params.getValue("endBefore"))
                        && Integer.valueOf(10).equals(params.getValue("limit"))
                        && Long.valueOf(10).equals(params.getValue("offset"))),
                any(RowMapper.class));
    }
}
