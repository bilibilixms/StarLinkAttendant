package com.starlink.marketing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.starlink.common.result.ErrorCode;
import com.starlink.common.util.PageResult;
import com.starlink.marketing.dto.request.CampaignCreateRequest;
import com.starlink.marketing.dto.request.CampaignQueryRequest;
import com.starlink.marketing.dto.request.CampaignUpdateRequest;
import com.starlink.marketing.dto.response.CampaignResponse;
import com.starlink.marketing.entity.Campaign;
import com.starlink.marketing.enums.CampaignStatus;
import com.starlink.marketing.enums.CampaignType;
import com.starlink.marketing.mapper.CampaignMapper;
import com.starlink.marketing.service.CampaignService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 活动服务实现类。
 *
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignServiceImpl implements CampaignService {

    private final CampaignMapper campaignMapper;

    private static final String CAMPAIGN_NO_PREFIX = "CAMP";
    private static final DateTimeFormatter NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Override
    public PageResult<CampaignResponse> getCampaignList(CampaignQueryRequest request) {
        Page<Campaign> page = new Page<>(request.getPage(), request.getSize());
        LambdaQueryWrapper<Campaign> wrapper = buildQueryWrapper(request);

        IPage<Campaign> resultPage = campaignMapper.selectPage(page, wrapper);
        return PageResult.from(resultPage).map(CampaignResponse::fromEntity);
    }

    @Override
    public CampaignResponse getCampaignById(Long id) {
        Campaign campaign = campaignMapper.selectById(id);
        if (campaign == null) {
            throw new com.starlink.common.exception.BusinessException(ErrorCode.CAMPAIGN_NOT_FOUND);
        }
        return CampaignResponse.fromEntity(campaign);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CampaignResponse createCampaign(CampaignCreateRequest request) {
        if (request.getStartTime().isAfter(request.getEndTime())) {
            throw new com.starlink.common.exception.BusinessException(ErrorCode.CAMPAIGN_END_TIME_ERROR);
        }

        Campaign campaign = new Campaign();
        campaign.setCampaignNo(generateCampaignNo());
        campaign.setCampaignName(request.getCampaignName());
        campaign.setCampaignType(request.getCampaignType());
        campaign.setStartTime(request.getStartTime());
        campaign.setEndTime(request.getEndTime());
        campaign.setRules(request.getRules());
        campaign.setBudget(request.getBudget());
        campaign.setUsedBudget(BigDecimal.ZERO);
        campaign.setUsageLimit(request.getUsageLimit() != null ? request.getUsageLimit() : 0);
        campaign.setUsedCount(0);
        campaign.setMemberLimit(request.getMemberLimit() != null ? request.getMemberLimit() : 1);
        campaign.setStatus(request.getStatus() != null ? request.getStatus() : 0);

        campaignMapper.insert(campaign);
        log.info("创建活动成功，ID: {}, 编号: {}", campaign.getId(), campaign.getCampaignNo());
        return CampaignResponse.fromEntity(campaign);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CampaignResponse updateCampaign(Long id, CampaignUpdateRequest request) {
        Campaign campaign = campaignMapper.selectById(id);
        if (campaign == null) {
            throw new com.starlink.common.exception.BusinessException(ErrorCode.CAMPAIGN_NOT_FOUND);
        }

        if (StringUtils.hasText(request.getCampaignName())) {
            campaign.setCampaignName(request.getCampaignName());
        }
        if (request.getCampaignType() != null) {
            campaign.setCampaignType(request.getCampaignType());
        }
        if (request.getStartTime() != null) {
            campaign.setStartTime(request.getStartTime());
        }
        if (request.getEndTime() != null) {
            campaign.setEndTime(request.getEndTime());
        }
        if (StringUtils.hasText(request.getRules())) {
            campaign.setRules(request.getRules());
        }
        if (request.getBudget() != null) {
            campaign.setBudget(request.getBudget());
        }
        if (request.getUsageLimit() != null) {
            campaign.setUsageLimit(request.getUsageLimit());
        }
        if (request.getMemberLimit() != null) {
            campaign.setMemberLimit(request.getMemberLimit());
        }
        if (request.getStatus() != null) {
            campaign.setStatus(request.getStatus());
        }

        campaignMapper.updateById(campaign);
        log.info("更新活动成功，ID: {}", id);
        return CampaignResponse.fromEntity(campaign);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCampaign(Long id) {
        Campaign campaign = campaignMapper.selectById(id);
        if (campaign == null) {
            throw new com.starlink.common.exception.BusinessException(ErrorCode.CAMPAIGN_NOT_FOUND);
        }

        if (campaign.getStatus() == null
                || (campaign.getStatus() != CampaignStatus.ENDED.getCode()
                    && campaign.getStatus() != CampaignStatus.REMOVED.getCode())) {
            throw new com.starlink.common.exception.BusinessException(
                    ErrorCode.CAMPAIGN_NOT_ACTIVE, "仅允许删除已结束或已下架的活动");
        }

        campaignMapper.deleteById(id);
        log.info("删除活动成功，ID: {}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CampaignResponse updateCampaignStatus(Long id, Integer status) {
        Campaign campaign = campaignMapper.selectById(id);
        if (campaign == null) {
            throw new com.starlink.common.exception.BusinessException(ErrorCode.CAMPAIGN_NOT_FOUND);
        }

        campaign.setStatus(status);
        campaignMapper.updateById(campaign);
        log.info("修改活动状态成功，ID: {}, 新状态: {}", id, status);
        return CampaignResponse.fromEntity(campaign);
    }

    @Override
    public PageResult<CampaignResponse> getRechargePromoList(CampaignQueryRequest request) {
        Page<Campaign> page = new Page<>(request.getPage(), request.getSize());
        LambdaQueryWrapper<Campaign> wrapper = buildQueryWrapper(request);
        wrapper.eq(Campaign::getCampaignType, CampaignType.RECHARGE_GIFT.getCode());

        IPage<Campaign> resultPage = campaignMapper.selectPage(page, wrapper);
        return PageResult.from(resultPage).map(CampaignResponse::fromEntity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRechargePromo(Long id) {
        Campaign campaign = campaignMapper.selectById(id);
        if (campaign == null) {
            throw new com.starlink.common.exception.BusinessException(ErrorCode.CAMPAIGN_NOT_FOUND);
        }

        if (campaign.getCampaignType() == null
                || campaign.getCampaignType() != CampaignType.RECHARGE_GIFT.getCode()) {
            throw new com.starlink.common.exception.BusinessException(ErrorCode.CAMPAIGN_NOT_FOUND);
        }

        if (campaign.getStatus() == null
                || campaign.getStatus() != CampaignStatus.REMOVED.getCode()) {
            throw new com.starlink.common.exception.BusinessException(
                    ErrorCode.CAMPAIGN_NOT_ACTIVE, "仅允许删除已下架的充值促销活动");
        }

        campaignMapper.deleteById(id);
        log.info("删除充值促销活动成功，ID: {}", id);
    }

    @Override
    @Scheduled(fixedRate = 60000)
    public void processCampaignStatus() {
        LocalDateTime now = LocalDateTime.now();

        LambdaUpdateWrapper<Campaign> wrapper = new LambdaUpdateWrapper<>();
        wrapper.in(Campaign::getStatus, CampaignStatus.PUBLISHED.getCode(), CampaignStatus.ACTIVE.getCode())
                .lt(Campaign::getEndTime, now)
                .set(Campaign::getStatus, CampaignStatus.REMOVED.getCode());

        int count = campaignMapper.update(null, wrapper);
        if (count > 0) {
            log.info("自动下架完成，共处理 {} 个已到结束时间的活动", count);
        }
    }

    private LambdaQueryWrapper<Campaign> buildQueryWrapper(CampaignQueryRequest request) {
        LambdaQueryWrapper<Campaign> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(request.getCampaignName())) {
            wrapper.like(Campaign::getCampaignName, request.getCampaignName());
        }
        if (request.getCampaignType() != null) {
            wrapper.eq(Campaign::getCampaignType, request.getCampaignType());
        }
        if (request.getStatus() != null) {
            wrapper.eq(Campaign::getStatus, request.getStatus());
        }

        wrapper.orderByDesc(Campaign::getCreatedAt);
        return wrapper;
    }

    private String generateCampaignNo() {
        String dateStr = LocalDateTime.now().format(NO_FORMATTER);
        int random = SECURE_RANDOM.nextInt(10000);
        return CAMPAIGN_NO_PREFIX + dateStr + String.format("%04d", random);
    }
}
