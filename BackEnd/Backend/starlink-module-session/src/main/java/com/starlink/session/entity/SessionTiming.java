package com.starlink.session.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.starlink.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 上机计时明细实体。
 *
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("session_timing")
public class SessionTiming extends BaseEntity {

    /** 所属会话 ID */
    private Long sessionId;

    /** 计时类型：1-正常计费 2-临时下机 3-免计费时段 */
    private Byte timingType;

    /** 费率类型：1-按时 2-包时 */
    private Byte rateType;

    /** 当前费率单价（元/小时） */
    private BigDecimal ratePrice;

    /** 段开始时间 */
    private LocalDateTime startTime;

    /** 段结束时间 */
    private LocalDateTime endTime;

    /** 段时长（分钟） */
    private Integer durationMinutes;

    /** 段费用 */
    private BigDecimal amount;
}
