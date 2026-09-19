package com.starlink.report.dto;

import lombok.Data;

/**
 * REP-03 时段分析 DTO — 按小时统计上机/并发数据。
 */
@Data
public class HourlySessionDTO {

    /** 小时（0-23） */
    private int hour;

    /** 上机会话数 */
    private int sessionCount;

    /** 活跃并发数（高峰期用） */
    private int activeCount;

    public HourlySessionDTO() {
    }

    public HourlySessionDTO(int hour, int sessionCount) {
        this.hour = hour;
        this.sessionCount = sessionCount;
    }
}
