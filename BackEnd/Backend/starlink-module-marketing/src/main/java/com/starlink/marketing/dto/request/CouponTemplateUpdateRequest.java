package com.starlink.marketing.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 更新优惠券模板请求DTO。
 *
 */
@Data
public class CouponTemplateUpdateRequest {

    /** 模板名称 */
    @NotBlank(message = "模板名称不能为空")
    private String templateName;

    /** 关联活动ID */
    private Long campaignId;

    /**
     * 券类型：
     * 1-满减券 2-折扣券 3-现金券 4-时段券
     */
    @Min(value = 1, message = "券类型无效")
    @Max(value = 4, message = "券类型无效")
    private Integer couponType;

    /** 面值（分） */
    @DecimalMin(value = "0", message = "面值不能为负数")
    private BigDecimal faceValue;

    /** 最低消费（分） */
    private BigDecimal minConsume;

    /** 折扣率（%） */
    private BigDecimal discountRate;

    /** 有效天数（从发放算起） */
    private Integer validDays;

    /** 固定生效日期 */
    private LocalDate validStart;

    /** 固定失效日期 */
    private LocalDate validEnd;

    /** 适用商品 ID 列表（JSON） */
    private String applicableProducts;

    /** 发行总量（0=不限量） */
    @Min(value = 0, message = "发行总量不能为负数")
    private Integer totalQuantity;

    /** 会员等级限制 */
    private Long memberLevelLimit;

    /** 是否启用 */
    private Integer isActive;
}
