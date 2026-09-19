package com.starlink.marketing.dto.response;

import com.starlink.marketing.entity.CouponTemplate;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 优惠券模板响应DTO。
 *
 */
@Data
public class CouponTemplateResponse {

    /** 模板ID */
    private Long id;

    /** 模板名称 */
    private String templateName;

    /** 关联活动ID */
    private Long campaignId;

    /** 券类型 */
    private Integer couponType;

    /** 券类型名称 */
    private String couponTypeName;

    /** 面值（分） */
    private BigDecimal faceValue;

    /** 最低消费（分） */
    private BigDecimal minConsume;

    /** 折扣率（%） */
    private BigDecimal discountRate;

    /** 有效天数 */
    private Integer validDays;

    /** 固定生效日期 */
    private LocalDate validStart;

    /** 固定失效日期 */
    private LocalDate validEnd;

    /** 适用商品 ID 列表（JSON） */
    private String applicableProducts;

    /** 发行总量 */
    private Integer totalQuantity;

    /** 会员等级限制 */
    private Long memberLevelLimit;

    /** 是否启用 */
    private Integer isActive;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /**
     * 从实体转换为响应DTO。
     */
    public static CouponTemplateResponse fromEntity(CouponTemplate entity) {
        CouponTemplateResponse response = new CouponTemplateResponse();
        response.setId(entity.getId());
        response.setTemplateName(entity.getTemplateName());
        response.setCampaignId(entity.getCampaignId());
        response.setCouponType(entity.getCouponType());
        response.setCouponTypeName(getCouponTypeName(entity.getCouponType()));
        response.setFaceValue(entity.getFaceValue());
        response.setMinConsume(entity.getMinConsume());
        response.setDiscountRate(entity.getDiscountRate());
        response.setValidDays(entity.getValidDays());
        response.setValidStart(entity.getValidStart());
        response.setValidEnd(entity.getValidEnd());
        response.setApplicableProducts(entity.getApplicableProducts());
        response.setTotalQuantity(entity.getTotalQuantity());
        response.setMemberLevelLimit(entity.getMemberLevelLimit());
        response.setIsActive(entity.getIsActive());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private static String getCouponTypeName(Integer couponType) {
        if (couponType == null) {
            return "";
        }
        return switch (couponType) {
            case 1 -> "满减券";
            case 2 -> "折扣券";
            case 3 -> "现金券";
            case 4 -> "时段券";
            default -> "未知";
        };
    }
}
