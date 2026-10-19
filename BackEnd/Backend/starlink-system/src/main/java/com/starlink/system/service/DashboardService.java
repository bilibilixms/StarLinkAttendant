package com.starlink.system.service;

import com.starlink.system.dto.resp.DashboardResponse;
import com.starlink.system.dto.resp.DashboardSeatStats;
import com.starlink.system.mapper.DashboardMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 仪表盘统计服务。
 *
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DashboardMapper dashboardMapper;

    public DashboardResponse getDashboardData() {
        DashboardResponse resp = new DashboardResponse();
        resp.setOnlineCount(dashboardMapper.countOnlineSessions());
        resp.setTodayRevenue(dashboardMapper.sumTodayRevenue());
        resp.setMemberCount(dashboardMapper.countMembers());
        resp.setTodaySalesCount(dashboardMapper.countTodaySales());
        DashboardSeatStats seatStats = dashboardMapper.countSeatStats();
        resp.setTotalSeats(seatStats.getNormalSeats());
        resp.setAvailableSeats(seatStats.getAvailableSeats());
        resp.setInUseSeats(seatStats.getInUseSeats());
        resp.setTodayNewMembers(dashboardMapper.countTodayNewMembers());
        resp.setTodaySessionCount(dashboardMapper.countTodaySessions());
        return resp;
    }
}
