package com.starlink.system.dto.resp;

import lombok.Data;

/** 首页正常机位的实时统计。 */
@Data
public class DashboardSeatStats {
    private Long normalSeats;
    private Long availableSeats;
    private Long inUseSeats;
}
