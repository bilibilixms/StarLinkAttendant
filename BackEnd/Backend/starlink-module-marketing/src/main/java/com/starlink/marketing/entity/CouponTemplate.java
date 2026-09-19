package com.starlink.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.starlink.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 优惠券模板表实体类。
 * <p>
 * 对应数据库表 `coupon_template`，存储优惠券模板信息。
 *
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("coupon_template")
public class CouponTemplate extends BaseEntity {

    /** 模板名称 */
    @TableField("template_name")
    private String templateName;

    /** 关联活动ID */
    @TableField("campaign_id")
    private Long campaignId;

    /**
     * 券类型：
     * 1-满减券 2-折扣券 3-现金券 4-时段券
     */
    @TableField("coupon_type")
    private Integer couponType;

    /** 面值 */
    @TableField("face_value")
    private BigDecimal faceValue;

    /** 最低消费（满减条件） */
    @TableField("min_consume")
    private BigDecimal minConsume;

    /** 折扣率（折扣券专用） */
    @TableField("discount_rate")
    private BigDecimal discountRate;

    /** 有效天数（从发放算起） */
    @TableField("valid_days")
    private Integer validDays;

    /** 固定生效日期 */
    @TableField("valid_start")
    private LocalDate validStart;

    /** 固定失效日期 */
    @TableField("valid_end")
    private LocalDate validEnd;

    /** 适用商品 ID 列表（JSON） */
    @TableField("applicable_products")
    private String applicableProducts;

    /** 发行总量（0=不限量） */
    @TableField("total_quantity")
    private Integer totalQuantity;

    /** 会员等级限制 */
    @TableField("member_level_limit")
    private Long memberLevelLimit;

    /** 是否启用 */
    @TableField("is_active")
    private Integer isActive;
}
