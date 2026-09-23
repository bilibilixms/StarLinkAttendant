package com.starlink.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.starlink.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券实例表实体类。
 * <p>
 * 对应数据库表 `coupon`，存储用户持有的优惠券实例。
 *
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("coupon")
public class Coupon extends BaseEntity {

    /** 持有会员 */
    @TableField("member_id")
    private Long memberId;

    /** 所属模板 */
    @TableField("template_id")
    private Long templateId;

    /** 来源活动 */
    @TableField("campaign_id")
    private Long campaignId;

    /** 券码 */
    @TableField("coupon_code")
    private String couponCode;

    /** 面值 */
    @TableField("face_value")
    private BigDecimal faceValue;

    /**
     * 状态：
     * 0-未使用 1-已使用 2-已过期 3-已作废
     */
    @TableField("status")
    private Integer status;

    /** 使用时间 */
    @TableField("used_at")
    private LocalDateTime usedAt;

    /** 使用订单 */
    @TableField("used_order_id")
    private Long usedOrderId;

    /** 过期时间 */
    @TableField("expire_at")
    private LocalDateTime expireAt;
}
