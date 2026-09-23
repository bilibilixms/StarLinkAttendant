package com.starlink.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.starlink.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 活动表实体类。
 * <p>
 * 对应数据库表 `campaign`，存储营销活动信息，包括充值促销、限时活动等。
 *
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("campaign")
public class Campaign extends BaseEntity {

    /** 活动编号 */
    @TableField("campaign_no")
    private String campaignNo;

    /** 活动名称 */
    @TableField("campaign_name")
    private String campaignName;

    /**
     * 活动类型：
     * 1-充值赠送 2-满减优惠 3-限时折扣 4-新人专享 5-生日活动 6-积分兑换
     */
    @TableField("campaign_type")
    private Integer campaignType;

    /** 开始时间 */
    @TableField("start_time")
    private LocalDateTime startTime;

    /** 结束时间 */
    @TableField("end_time")
    private LocalDateTime endTime;

    /** 活动规则（JSON） */
    @TableField("rules")
    private String rules;

    /** 活动预算 */
    @TableField("budget")
    private BigDecimal budget;

    /** 已用预算 */
    @TableField("used_budget")
    private BigDecimal usedBudget;

    /** 使用次数限制 */
    @TableField("usage_limit")
    private Integer usageLimit;

    /** 已使用次数 */
    @TableField("used_count")
    private Integer usedCount;

    /** 每人限参与次数 */
    @TableField("member_limit")
    private Integer memberLimit;

    /**
     * 状态：
     * 0-草稿 1-已发布 2-已生效 3-已结束 4-已下架
     */
    @TableField("status")
    private Integer status;
}
