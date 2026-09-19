package com.starlink.marketing.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 创建活动请求DTO。
 *
 */
@Data
public class CampaignCreateRequest {

    /** 活动名称 */
    @NotBlank(message = "活动名称不能为空")
    private String campaignName;

    /**
     * 活动类型：
     * 1-充值赠送 2-满减优惠 3-限时折扣 4-新人专享 5-生日活动 6-积分兑换
     */
    @NotNull(message = "活动类型不能为空")
    @Min(value = 1, message = "活动类型无效")
    @Max(value = 6, message = "活动类型无效")
    private Integer campaignType;

    /** 开始时间 */
    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startTime;

    /** 结束时间 */
    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endTime;

    /** 活动规则（JSON） */
    @NotBlank(message = "活动规则不能为空")
    private String rules;

    /** 活动预算（分） */
    @DecimalMin(value = "0", message = "活动预算不能为负数")
    private BigDecimal budget;

    /** 使用次数限制（0=不限） */
    @Min(value = 0, message = "使用次数限制不能为负数")
    private Integer usageLimit;

    /** 每人限参与次数 */
    @Min(value = 1, message = "每人限参与次数至少为1")
    private Integer memberLimit;

    /**
     * 状态：
     * 0-草稿 1-已发布 2-已生效 3-已结束 4-已下架
     */
    private Integer status;
}
