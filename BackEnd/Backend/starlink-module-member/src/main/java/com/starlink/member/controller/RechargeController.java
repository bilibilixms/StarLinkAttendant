package com.starlink.member.controller;

import com.starlink.common.result.Result;
import com.starlink.common.util.PageQuery;
import com.starlink.common.util.PageResult;
import com.starlink.member.dto.req.RechargeRequest;
import com.starlink.member.dto.resp.RechargePreviewResponse;
import com.starlink.member.dto.resp.RechargeRecordResponse;
import com.starlink.member.service.RechargeService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/member")
@RequiredArgsConstructor
public class RechargeController {

    private final RechargeService rechargeService;

    @PostMapping("/recharge")
    public Result<RechargeRecordResponse> recharge(@Valid @RequestBody RechargeRequest request) {
        return Result.ok(rechargeService.recharge(request));
    }

    /**
     * 充值试算（预览）：返回「实付 / 活动赠送 / 实际到账」，供前端展示，不产生任何写入。
     * <p>
     * 只读接口，故用 GET；金额范围与资源归属校验与真实充值一致。
     * 最终入账金额仍由 {@link #recharge} 在服务端重新计算。
     */
    @GetMapping("/recharge/preview")
    public Result<RechargePreviewResponse> preview(@RequestParam Long memberId,
                                                   @RequestParam BigDecimal amount) {
        return Result.ok(rechargeService.preview(memberId, amount));
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