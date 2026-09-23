package com.starlink.session.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.starlink.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 计费记录实体。
 *
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("billing_record")
public class BillingRecord extends BaseEntity {

    /** 上机会话 ID */
    private Long sessionId;

    /** 会员 ID */
    private Long memberId;

    /** 终端 ID */
    private Long computerId;

    /** 计费流水号 */
    private String recordNo;

    /** 费率方案 ID */
    private Long tariffPlanId;

    /** 时间段内计费时长（分钟） */
    private Integer maintenanceMinutes;

    /** 时间段内均价 */
    private BigDecimal unitPrice;

    /** 费用 */
    private BigDecimal totalAmount;

    /** 优惠金额 */
    private BigDecimal discountAmount;

    /** 实收金额 */
    private BigDecimal finalAmount;

    /** 计费周期开始 */
    private LocalDateTime billingStart;

    /** 计费周期结束 */
    private LocalDateTime billingEnd;

    /** 是否已结算：0-否 1-是 */
    private Byte isSettled;

    /** 结算时间 */
    private LocalDateTime settledAt;
}
