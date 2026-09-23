package com.starlink.marketing.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 修改活动状态请求DTO。
 * <p>
 * 只包含状态字段，用于发布、结束、下架等操作。
 *
 */
@Data
public class CampaignStatusRequest {

    /**
     * 状态：0-草稿 1-已发布 2-已生效 3-已结束 4-已下架
     */
    @NotNull(message = "状态不能为空")
    @Min(value = 0, message = "状态值无效")
    @Max(value = 4, message = "状态值无效")
    private Integer status;
}
