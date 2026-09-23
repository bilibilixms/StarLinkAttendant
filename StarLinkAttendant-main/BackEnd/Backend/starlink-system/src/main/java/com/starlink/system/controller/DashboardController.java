package com.starlink.system.controller;

import com.starlink.common.result.Result;
import com.starlink.system.dto.resp.DashboardResponse;
import com.starlink.system.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 首页仪表盘控制器。
 *
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * 获取首页仪表盘数据。
     */
    @GetMapping
    public Result<DashboardResponse> getDashboard() {
        return Result.ok(dashboardService.getDashboardData());
    }
}
