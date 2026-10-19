package com.starlink.cashier.controller;

import com.starlink.cashier.dto.request.BillQueryRequest;
import com.starlink.cashier.dto.response.BillResponse;
import com.starlink.cashier.service.BillService;
import com.starlink.common.result.Result;
import com.starlink.common.util.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cashier/bills")
@RequiredArgsConstructor
public class BillController {

    private final BillService billService;

    @GetMapping
    public Result<PageResult<BillResponse>> list(@Valid BillQueryRequest query) {
        return Result.ok(billService.list(query));
    }

    @GetMapping("/{source}/{id}")
    public Result<BillResponse> detail(@PathVariable String source, @PathVariable Long id) {
        return Result.ok(billService.detail(source, id));
    }
}
