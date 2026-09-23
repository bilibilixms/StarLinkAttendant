package com.starlink.member.controller;

import com.starlink.common.result.Result;
import com.starlink.common.util.PageQuery;
import com.starlink.common.util.PageResult;
import com.starlink.member.dto.resp.PointsRecordResponse;
import com.starlink.member.service.PointsService;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/member")
@RequiredArgsConstructor
public class PointsController {

    private final PointsService pointsService;

    @GetMapping("/{id}/points-records")
    public Result<PageResult<PointsRecordResponse>> getPointsRecords(@PathVariable Long id,
                                                                     @RequestParam(required = false) Byte bizType,
                                                                     @RequestParam(required = false) String startTime,
                                                                     @RequestParam(required = false) String endTime,
                                                                     PageQuery pageQuery) {
        return Result.ok(pointsService.getPointsRecords(id, bizType, startTime, endTime, pageQuery));
    }
}