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
@TableName("payment_record")
public class PaymentRecord extends BaseEntity {

    @TableField("payment_no")
    private String paymentNo;

    @TableField("order_id")
    private Long orderId;

    @TableField("member_id")
    private Long memberId;

    @TableField("payment_method")
    private Byte paymentMethod;

    @TableField("trade_no")
    private String tradeNo;

    @TableField("total_amount")
    private BigDecimal totalAmount;

    @TableField("refund_amount")
    private BigDecimal refundAmount;

    @TableField("payment_status")
    private Byte paymentStatus;

    @TableField("paid_at")
    private LocalDateTime paidAt;

    @TableField("operator_id")
    private Long operatorId;

    @TableField("idempotent_key")
    private String idempotentKey;
}
