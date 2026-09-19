package com.starlink.cashier.controller;

import com.starlink.cashier.dto.request.SettlementQueryRequest;
import com.starlink.cashier.dto.response.SettlementResponse;
import com.starlink.cashier.service.DailySettlementService;
import com.starlink.common.result.Result;
import com.starlink.common.util.PageResult;
import com.starlink.common.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/cashier/settlement")
@RequiredArgsConstructor
public class DailySettlementController {

    private final DailySettlementService settlementService;

    @GetMapping("/daily")
    public Result<PageResult<SettlementResponse>> listSettlements(SettlementQueryRequest query) {
        return Result.ok(settlementService.getSettlementPage(query));
    }

    @GetMapping("/{id}")
    public Result<SettlementResponse> getSettlementById(@PathVariable Long id) {
        return Result.ok(settlementService.getSettlementById(id));
    }

    @PostMapping("/generate")
    public Result<SettlementResponse> generateSettlement(@RequestParam String settleDate) {
        return Result.ok(settlementService.generateSettlement(settleDate));
    }

    @PostMapping("/{id}/confirm")
    public Result<SettlementResponse> confirmSettlement(@PathVariable Long id) {
        Long confirmBy = SecurityUtils.getCurrentUserId();
        return Result.ok(settlementService.confirmSettlement(id, confirmBy));
    }
}
