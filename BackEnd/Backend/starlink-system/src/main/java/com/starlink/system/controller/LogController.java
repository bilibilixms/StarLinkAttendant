package com.starlink.system.controller;

import com.starlink.common.result.Result;
import com.starlink.common.util.PageQuery;
import com.starlink.common.util.PageResult;
import com.starlink.system.dto.resp.LogResponse;
import com.starlink.system.service.LogService;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/system/logs")
@RequiredArgsConstructor
public class LogController {

    private final LogService logService;

    @GetMapping
    public Result<PageResult<LogResponse>> listLogs(PageQuery pageQuery,
                                                    @RequestParam(required = false) String operatorName,
                                                    @RequestParam(required = false) String bizType,
                                                    @RequestParam(required = false) String action,
                                                    @RequestParam(required = false) String startTime,
                                                    @RequestParam(required = false) String endTime) {
        return Result.ok(logService.listLogs(pageQuery, operatorName, bizType, action, startTime, endTime));
    }
}
