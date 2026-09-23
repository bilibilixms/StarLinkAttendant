package com.starlink.marketing.dto.request;

import com.starlink.common.util.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 优惠券模板查询请求DTO。
 *
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CouponTemplateQueryRequest extends PageQuery {

    /** 模板名称（模糊查询） */
    private String templateName;

    /** 券类型 */
    private Integer couponType;

    /** 是否启用 */
    private Integer isActive;
}
