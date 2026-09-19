package com.starlink.marketing.dto.request;

import com.starlink.common.util.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 优惠券核销记录查询请求DTO。
 *
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CouponRecordsQueryRequest extends PageQuery {

    /** 会员ID */
    private Long memberId;

    /** 优惠券状态 */
    private Integer status;

    /** 模板ID */
    private Long templateId;
}
