package com.starlink.session.dto.resp;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 预约响应。
 *
 */
@Data
public class ReservationResponse {

    private Long id;

    /** 预约编号 */
    private String reservationNo;

    /** 会员 ID */
    private Long memberId;

    /** 会员姓名 */
    private String memberName;

    /** 会员手机号 */
    private String memberPhone;

    /** 机位 ID */
    private Long computerId;

    /** 机位编号 */
    private String computerNo;

    /** 预约日期 */
    private LocalDate reservationDate;

    /** 预约开始时间 */
    private LocalDateTime startTime;

    /** 预约结束时间 */
    private LocalDateTime endTime;

    /** 预约保证金 */
    private BigDecimal depositAmount;

    /** 状态：0-待确认 1-已确认 2-已上机 3-已取消 4-超时未到 */
    private Byte status;

    /** 状态标签 */
    private String statusLabel;

    /** 取消原因 */
    private String cancelReason;

    /** 实际到店时间 */
    private LocalDateTime checkedInAt;

    /** 关联上机会话 ID */
    private Long sessionId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
