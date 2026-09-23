package com.starlink.marketing.dto.response;

import com.starlink.marketing.entity.Coupon;
import com.starlink.marketing.enums.CouponStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券响应DTO。
 *
 */
@Data
public class CouponResponse {

    /** 优惠券ID */
    private Long id;

    /** 持有会员ID */
    private Long memberId;

    /** 会员名称 */
    private String memberName;

    /** 所属模板ID */
    private Long templateId;

    /** 模板名称 */
    private String templateName;

    /** 来源活动ID */
    private Long campaignId;

    /** 活动名称 */
    private String campaignName;

    /** 券码 */
    private String couponCode;

    /** 面值（分） */
    private BigDecimal faceValue;

    /** 状态 */
    private Integer status;

    /** 状态名称 */
    private String statusName;

    /** 使用时间 */
    private LocalDateTime usedAt;

    /** 使用订单ID */
    private Long usedOrderId;

    /** 过期时间 */
    private LocalDateTime expireAt;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /**
     * 从实体转换为响应DTO。
     */
    public static CouponResponse fromEntity(Coupon entity) {
        CouponResponse response = new CouponResponse();
        response.setId(entity.getId());
        response.setMemberId(entity.getMemberId());
        response.setTemplateId(entity.getTemplateId());
        response.setCampaignId(entity.getCampaignId());
        response.setCouponCode(entity.getCouponCode());
        response.setFaceValue(entity.getFaceValue());
        response.setStatus(entity.getStatus());
        response.setStatusName(CouponStatus.getNameByCode(entity.getStatus()));
        response.setUsedAt(entity.getUsedAt());
        response.setUsedOrderId(entity.getUsedOrderId());
        response.setExpireAt(entity.getExpireAt());
        response.setCreatedAt(entity.getCreatedAt());
        return response;
    }
}
