package com.starlink.session.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 机位实时状态响应（轮询接口使用）。
 *
 */
@Data
public class SeatStatusResponse {

    /** 机位 ID */
    private Long id;

    /** 运营状态：0-空闲 1-使用中 2-锁定 3-维修 4-关机 */
    private Byte status;

    /** 当前使用者姓名（使用中时） */
    private String memberName;

    /** 上机开始时间（使用中时） */
    private LocalDateTime startTime;
}
