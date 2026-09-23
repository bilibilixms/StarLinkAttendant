package com.starlink.cashier.service;

import com.starlink.cashier.dto.request.ShiftEndRequest;
import com.starlink.cashier.dto.request.ShiftQueryRequest;
import com.starlink.cashier.dto.request.ShiftStartRequest;
import com.starlink.cashier.dto.response.ShiftResponse;
import com.starlink.common.util.PageResult;

public interface CashierShiftService {

    ShiftResponse startShift(ShiftStartRequest request, Long employeeId);

    ShiftResponse endShift(ShiftEndRequest request, Long employeeId);

    PageResult<ShiftResponse> getShiftPage(ShiftQueryRequest query);

    ShiftResponse getShiftById(Long id);
}
