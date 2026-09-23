package com.starlink.cashier.controller;

import com.starlink.cashier.dto.request.ShiftEndRequest;
import com.starlink.cashier.dto.request.ShiftQueryRequest;
import com.starlink.cashier.dto.request.ShiftStartRequest;
import com.starlink.cashier.dto.response.ShiftResponse;
import com.starlink.cashier.service.CashierShiftService;
import com.starlink.common.result.Result;
import com.starlink.common.util.PageResult;
import com.starlink.common.utils.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/cashier/shift")
@RequiredArgsConstructor
public class CashierShiftController {

    private final CashierShiftService shiftService;

    @PostMapping("/start")
    public Result<ShiftResponse> startShift(@Valid @RequestBody ShiftStartRequest request) {
        Long employeeId = SecurityUtils.getCurrentUserId();
        return Result.ok(shiftService.startShift(request, employeeId));
    }

    @PostMapping("/end")
    public Result<ShiftResponse> endShift(@Valid @RequestBody ShiftEndRequest request) {
        Long employeeId = SecurityUtils.getCurrentUserId();
        return Result.ok(shiftService.endShift(request, employeeId));
    }

    @GetMapping("/records")
    public Result<PageResult<ShiftResponse>> listShifts(ShiftQueryRequest query) {
        return Result.ok(shiftService.getShiftPage(query));
    }

    @GetMapping("/{id}")
    public Result<ShiftResponse> getShiftById(@PathVariable Long id) {
        return Result.ok(shiftService.getShiftById(id));
    }
}
