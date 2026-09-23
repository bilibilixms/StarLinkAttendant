package com.starlink.marketing.dto.request;

import com.starlink.common.util.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 活动查询请求DTO。
 *
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CampaignQueryRequest extends PageQuery {

    /** 活动名称（模糊查询） */
    private String campaignName;

    /** 活动类型 */
    private Integer campaignType;

    /** 活动状态 */
    private Integer status;
}
