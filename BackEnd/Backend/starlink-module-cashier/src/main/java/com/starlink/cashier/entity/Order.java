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
@TableName("orders")
public class Order extends BaseEntity {

    @TableField("order_no")
    private String orderNo;

    @TableField("order_type")
    private Byte orderType;

    @TableField("member_id")
    private Long memberId;

    @TableField("session_id")
    private Long sessionId;

    @TableField("total_amount")
    private BigDecimal totalAmount;

    @TableField("discount_amount")
    private BigDecimal discountAmount;

    @TableField("payable_amount")
    private BigDecimal payableAmount;

    @TableField("paid_amount")
    private BigDecimal paidAmount;

    @TableField("status")
    private Byte status;

    @TableField("remark")
    private String remark;

    @TableField("operator_id")
    private Long operatorId;

    @TableField("paid_at")
    private LocalDateTime paidAt;
}
