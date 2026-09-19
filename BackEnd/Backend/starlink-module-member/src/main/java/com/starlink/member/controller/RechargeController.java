package com.starlink.member.controller;

import com.starlink.common.result.Result;
import com.starlink.common.util.PageQuery;
import com.starlink.common.util.PageResult;
import com.starlink.member.dto.req.RechargeRequest;
import com.starlink.member.dto.resp.RechargeRecordResponse;
import com.starlink.member.service.RechargeService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/member")
@RequiredArgsConstructor
public class RechargeController {

    private final RechargeService rechargeService;

    @PostMapping("/recharge")
    public Result<RechargeRecordResponse> recharge(@Valid @RequestBody RechargeRequest request) {
        return Result.ok(rechargeService.recharge(request));
    }

    @GetMapping("/{id}/recharge-records")
    public Result<PageResult<RechargeRecordResponse>> getRechargeRecords(@PathVariable Long id,
                                                                         @RequestParam(required = false) String startTime,
                                                                         @RequestParam(required = false) String endTime,
                                                                         @RequestParam(required = false) Byte status,
                                                                         PageQuery pageQuery) {
        return Result.ok(rechargeService.getRechargeRecords(id, startTime, endTime, status, pageQuery));
    }

    @GetMapping("/recharge-records/{id}")
    public Result<RechargeRecordResponse> getRechargeById(@PathVariable Long id) {
        return Result.ok(rechargeService.getRechargeById(id));
    }
}