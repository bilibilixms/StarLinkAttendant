package com.starlink.cashier.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.starlink.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("refund_record")
public class RefundRecord extends BaseEntity {

    @TableField("refund_no")
    private String refundNo;

    @TableField("order_id")
    private Long orderId;

    @TableField("payment_id")
    private Long paymentId;

    @TableField("member_id")
    private Long memberId;

    @TableField("refund_amount")
    private BigDecimal refundAmount;

    @TableField("refund_type")
    private Byte refundType;

    @TableField("refund_reason")
    private String refundReason;

    @TableField("refund_method")
    private Byte refundMethod;

    @TableField("status")
    private Byte status;

    @TableField("audit_by")
    private Long auditBy;

    @TableField("audit_at")
    private LocalDateTime auditAt;

    @TableField("operator_id")
    private Long operatorId;
}
