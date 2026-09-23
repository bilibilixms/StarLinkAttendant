package com.starlink.marketing.dto.response;

import com.starlink.marketing.entity.Campaign;
import com.starlink.marketing.enums.CampaignStatus;
import com.starlink.marketing.enums.CampaignType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 活动响应DTO。
 *
 */
@Data
public class CampaignResponse {

    /** 活动ID */
    private Long id;

    /** 活动编号 */
    private String campaignNo;

    /** 活动名称 */
    private String campaignName;

    /** 活动类型 */
    private Integer campaignType;

    /** 活动类型名称 */
    private String campaignTypeName;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 活动规则（JSON） */
    private String rules;

    /** 活动预算（分） */
    private BigDecimal budget;

    /** 已用预算（分） */
    private BigDecimal usedBudget;

    /** 使用次数限制 */
    private Integer usageLimit;

    /** 已使用次数 */
    private Integer usedCount;

    /** 每人限参与次数 */
    private Integer memberLimit;

    /** 状态 */
    private Integer status;

    /** 状态名称 */
    private String statusName;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /**
     * 从实体转换为响应DTO。
     */
    public static CampaignResponse fromEntity(Campaign entity) {
        CampaignResponse response = new CampaignResponse();
        response.setId(entity.getId());
        response.setCampaignNo(entity.getCampaignNo());
        response.setCampaignName(entity.getCampaignName());
        response.setCampaignType(entity.getCampaignType());
        response.setCampaignTypeName(CampaignType.getNameByCode(entity.getCampaignType()));
        response.setStartTime(entity.getStartTime());
        response.setEndTime(entity.getEndTime());
        response.setRules(entity.getRules());
        response.setBudget(entity.getBudget());
        response.setUsedBudget(entity.getUsedBudget());
        response.setUsageLimit(entity.getUsageLimit());
        response.setUsedCount(entity.getUsedCount());
        response.setMemberLimit(entity.getMemberLimit());
        response.setStatus(entity.getStatus());
        response.setStatusName(CampaignStatus.getNameByCode(entity.getStatus()));
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }
}
