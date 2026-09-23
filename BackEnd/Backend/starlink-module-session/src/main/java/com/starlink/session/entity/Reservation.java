package com.starlink.session.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.starlink.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 预约记录实体。
 *
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reservation")
public class Reservation extends BaseEntity {

    /** 会员 ID */
    private Long memberId;

    /** 指定机位 ID（NULL 表示到店分配） */
    private Long computerId;

    /** 预约编号 */
    private String reservationNo;

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

    /** 取消原因 */
    private String cancelReason;

    /** 实际到店时间 */
    private LocalDateTime checkedInAt;

    /** 关联上机会话 ID */
    private Long sessionId;
}
