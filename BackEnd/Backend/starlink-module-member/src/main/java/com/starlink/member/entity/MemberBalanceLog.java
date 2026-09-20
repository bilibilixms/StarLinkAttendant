package com.starlink.member.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.starlink.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 会员余额变动流水。
 * 每次余额扣减/增加都记一条，amount 正数表示增加、负数表示扣减。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("member_balance_log")
public class MemberBalanceLog extends BaseEntity {

    private Long memberId;

    /** 变动金额：正数=增加（充值/退款回充），负数=扣减（消费） */
    private BigDecimal amount;

    private BigDecimal balanceBefore;

    private BigDecimal balanceAfter;

    /** 业务类型：1-消费 2-退款回充 3-充值 4-手动调整 */
    private Byte bizType;

    private Long bizId;

    private String remark;
}
