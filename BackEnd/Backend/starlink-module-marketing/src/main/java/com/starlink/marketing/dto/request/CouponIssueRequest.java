package com.starlink.marketing.dto.request;

import lombok.Data;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;

/**
 * 发放优惠券请求DTO。
 *
 */
@Data
public class CouponIssueRequest {

    /** 优惠券模板ID（由控制器从路径参数注入） */
    private Long templateId;

    /** 目标会员ID（为空则批量发放） */
    @NotNull(message = "目标会员不能为空")
    private Long memberId;

    /** 发放数量（批量发放时使用，默认为1） */
    @Min(value = 1, message = "发放数量至少为1")
    @Max(value = 100, message = "单次最多发放100张")
    private Integer quantity;

    /** 来源活动ID */
    private Long campaignId;
}
