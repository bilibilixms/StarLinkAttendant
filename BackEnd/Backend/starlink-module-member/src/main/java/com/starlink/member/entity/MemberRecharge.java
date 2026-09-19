package com.starlink.member.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.starlink.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("member_recharge")
public class MemberRecharge extends BaseEntity {

    private Long memberId;

    private String rechargeNo;

    private BigDecimal rechargeAmount;

    private BigDecimal bonusAmount;

    private BigDecimal totalAmount;

    private BigDecimal balanceBefore;

    private BigDecimal balanceAfter;

    private Byte paymentMethod;

    private String paymentChannel;

    private String tradeNo;

    private Long operatorId;

    private Long campaignId;

    private Byte status;

    private LocalDateTime paidAt;
}