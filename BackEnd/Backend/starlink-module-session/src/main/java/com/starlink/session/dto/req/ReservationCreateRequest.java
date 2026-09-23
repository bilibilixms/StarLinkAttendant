package com.starlink.session.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 创建预约请求。
 *
 */
@Data
public class ReservationCreateRequest {

    /** 会员 ID */
    @NotNull(message = "会员 ID 不能为空")
    private Long memberId;

    /** 指定机位 ID（NULL 表示到店分配） */
    private Long computerId;

    /** 预约日期 */
    @NotNull(message = "预约日期不能为空")
    private LocalDate reservationDate;

    /** 预约开始时间 */
    @NotNull(message = "预约开始时间不能为空")
    private LocalDateTime startTime;

    /** 预约结束时间 */
    @NotNull(message = "预约结束时间不能为空")
    private LocalDateTime endTime;

    /** 预约保证金 */
    private BigDecimal depositAmount;
}
