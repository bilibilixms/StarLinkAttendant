package com.starlink.member.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.starlink.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("member_level")
public class MemberLevel extends BaseEntity {

    private String levelName;

    private Byte levelOrder;

    private Integer minGrowth;

    private Integer maxGrowth;

    private BigDecimal discountRate;

    private BigDecimal hourlyDiscount;

    private BigDecimal rechargeBonusRate;

    private BigDecimal pointsMultiple;

    private Byte autoUpgrade;

    /** 透支额度（元），余额最低可透支至该值的负数 */
    private BigDecimal creditLimit;

    private String iconUrl;
}