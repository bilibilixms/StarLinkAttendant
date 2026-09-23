package com.starlink.marketing.dto.request;

import lombok.Data;

/**
 * 发放优惠券请求DTO。
 *
 */
@Data
public class CouponIssueRequest {

    /** 优惠券模板ID（由控制器从路径参数注入） */
    private Long templateId;

    /** 目标会员ID（为空则批量发放） */
    private Long memberId;

    /** 发放数量（批量发放时使用，默认为1） */
    private Integer quantity;

    /** 来源活动ID */
    private Long campaignId;
}
