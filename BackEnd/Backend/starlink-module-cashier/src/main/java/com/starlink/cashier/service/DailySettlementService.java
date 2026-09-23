package com.starlink.cashier.service;

import com.starlink.cashier.dto.request.SettlementQueryRequest;
import com.starlink.cashier.dto.response.SettlementResponse;
import com.starlink.common.util.PageResult;

public interface DailySettlementService {

    PageResult<SettlementResponse> getSettlementPage(SettlementQueryRequest query);

    SettlementResponse getSettlementById(Long id);

    SettlementResponse generateSettlement(String settleDate);

    SettlementResponse confirmSettlement(Long id, Long confirmBy);
}
