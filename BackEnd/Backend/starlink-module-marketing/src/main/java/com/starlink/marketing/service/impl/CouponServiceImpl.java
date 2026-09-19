package com.starlink.marketing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.starlink.common.exception.BusinessException;
import com.starlink.common.result.ErrorCode;
import com.starlink.common.util.PageResult;
import com.starlink.marketing.dto.request.CouponIssueRequest;
import com.starlink.marketing.dto.request.CouponRecordsQueryRequest;
import com.starlink.marketing.dto.request.CouponTemplateCreateRequest;
import com.starlink.marketing.dto.request.CouponTemplateQueryRequest;
import com.starlink.marketing.dto.request.CouponTemplateUpdateRequest;
import com.starlink.marketing.dto.response.CouponResponse;
import com.starlink.marketing.dto.response.CouponTemplateResponse;
import com.starlink.marketing.entity.Campaign;
import com.starlink.marketing.entity.Coupon;
import com.starlink.marketing.entity.CouponTemplate;
import com.starlink.marketing.enums.CampaignStatus;
import com.starlink.marketing.enums.CouponStatus;
import com.starlink.marketing.mapper.CampaignMapper;
import com.starlink.marketing.mapper.CouponMapper;
import com.starlink.marketing.mapper.CouponTemplateMapper;
import com.starlink.marketing.service.CouponService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * 优惠券服务实现类。
 *
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {

    private final CouponTemplateMapper couponTemplateMapper;
    private final CouponMapper couponMapper;
    private final CampaignMapper campaignMapper;

    private static final String COUPON_CODE_PREFIX = "CP";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int MAX_CODE_RETRY = 10;

    @Override
    public PageResult<CouponTemplateResponse> getCouponTemplateList(CouponTemplateQueryRequest request) {
        Page<CouponTemplate> page = new Page<>(request.getPage(), request.getSize());
        LambdaQueryWrapper<CouponTemplate> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(request.getTemplateName())) {
            wrapper.like(CouponTemplate::getTemplateName, request.getTemplateName());
        }
        if (request.getCouponType() != null) {
            wrapper.eq(CouponTemplate::getCouponType, request.getCouponType());
        }
        if (request.getIsActive() != null) {
            wrapper.eq(CouponTemplate::getIsActive, request.getIsActive());
        }

        wrapper.orderByDesc(CouponTemplate::getCreatedAt);

        IPage<CouponTemplate> resultPage = couponTemplateMapper.selectPage(page, wrapper);
        return PageResult.from(resultPage).map(CouponTemplateResponse::fromEntity);
    }

    @Override
    public CouponTemplateResponse getCouponTemplateById(Long id) {
        CouponTemplate template = couponTemplateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException(ErrorCode.COUPON_TEMPLATE_NOT_FOUND);
        }
        return CouponTemplateResponse.fromEntity(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CouponTemplateResponse createCouponTemplate(CouponTemplateCreateRequest request) {
        CouponTemplate template = new CouponTemplate();
        template.setTemplateName(request.getTemplateName());
        template.setCampaignId(request.getCampaignId());
        template.setCouponType(request.getCouponType());
        template.setFaceValue(request.getFaceValue());
        template.setMinConsume(request.getMinConsume());
        template.setDiscountRate(request.getDiscountRate());
        template.setValidDays(request.getValidDays());
        template.setValidStart(request.getValidStart());
        template.setValidEnd(request.getValidEnd());
        template.setApplicableProducts(request.getApplicableProducts());
        template.setTotalQuantity(request.getTotalQuantity() != null ? request.getTotalQuantity() : 0);
        template.setMemberLevelLimit(request.getMemberLevelLimit());
        template.setIsActive(request.getIsActive() != null ? request.getIsActive() : 1);

        couponTemplateMapper.insert(template);
        log.info("创建优惠券模板成功，ID: {}", template.getId());
        return CouponTemplateResponse.fromEntity(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CouponTemplateResponse updateCouponTemplate(Long id, CouponTemplateUpdateRequest request) {
        CouponTemplate template = couponTemplateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException(ErrorCode.COUPON_TEMPLATE_NOT_FOUND);
        }

        if (StringUtils.hasText(request.getTemplateName())) {
            template.setTemplateName(request.getTemplateName());
        }
        if (request.getCampaignId() != null) {
            template.setCampaignId(request.getCampaignId());
        }
        if (request.getCouponType() != null) {
            template.setCouponType(request.getCouponType());
        }
        if (request.getFaceValue() != null) {
            template.setFaceValue(request.getFaceValue());
        }
        if (request.getMinConsume() != null) {
            template.setMinConsume(request.getMinConsume());
        }
        if (request.getDiscountRate() != null) {
            template.setDiscountRate(request.getDiscountRate());
        }
        if (request.getValidDays() != null) {
            template.setValidDays(request.getValidDays());
        }
        if (request.getValidStart() != null) {
            template.setValidStart(request.getValidStart());
        }
        if (request.getValidEnd() != null) {
            template.setValidEnd(request.getValidEnd());
        }
        if (request.getApplicableProducts() != null) {
            template.setApplicableProducts(request.getApplicableProducts());
        }
        if (request.getTotalQuantity() != null) {
            template.setTotalQuantity(request.getTotalQuantity());
        }
        if (request.getMemberLevelLimit() != null) {
            template.setMemberLevelLimit(request.getMemberLevelLimit());
        }
        if (request.getIsActive() != null) {
            template.setIsActive(request.getIsActive());
        }

        couponTemplateMapper.updateById(template);
        log.info("更新优惠券模板成功，ID: {}", id);
        return CouponTemplateResponse.fromEntity(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCouponTemplate(Long id) {
        CouponTemplate template = couponTemplateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException(ErrorCode.COUPON_TEMPLATE_NOT_FOUND);
        }

        couponTemplateMapper.deleteById(id);
        log.info("删除优惠券模板成功，ID: {}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer issueCoupon(CouponIssueRequest request) {
        // 1. 校验模板存在且启用
        CouponTemplate template = couponTemplateMapper.selectById(request.getTemplateId());
        if (template == null) {
            throw new BusinessException(ErrorCode.COUPON_TEMPLATE_NOT_FOUND);
        }
        if (template.getIsActive() != null && template.getIsActive() == 0) {
            throw new BusinessException(ErrorCode.COUPON_TEMPLATE_DISABLED);
        }

        int quantity = request.getQuantity() != null ? request.getQuantity() : 1;

        // 2. 校验发行总量限制
        if (template.getTotalQuantity() != null && template.getTotalQuantity() > 0) {
            Long issuedCount = couponMapper.selectCount(
                    new LambdaQueryWrapper<Coupon>().eq(Coupon::getTemplateId, template.getId()));
            if (issuedCount + quantity > template.getTotalQuantity()) {
                throw new BusinessException(ErrorCode.COUPON_INSUFFICIENT_QUANTITY);
            }
        }

        // 3. 校验关联活动状态（如果有 campaignId）
        if (request.getCampaignId() != null) {
            Campaign campaign = campaignMapper.selectById(request.getCampaignId());
            if (campaign == null) {
                throw new BusinessException(ErrorCode.CAMPAIGN_NOT_FOUND);
            }
            // 活动必须是已发布或已生效状态
            if (campaign.getStatus() != CampaignStatus.PUBLISHED.getCode()
                    && campaign.getStatus() != CampaignStatus.ACTIVE.getCode()) {
                throw new BusinessException(ErrorCode.CAMPAIGN_NOT_ACTIVE);
            }
            // 校验活动时间
            LocalDateTime now = LocalDateTime.now();
            if (campaign.getStartTime() != null && now.isBefore(campaign.getStartTime())) {
                throw new BusinessException(ErrorCode.CAMPAIGN_NOT_ACTIVE);
            }
            if (campaign.getEndTime() != null && now.isAfter(campaign.getEndTime())) {
                throw new BusinessException(ErrorCode.CAMPAIGN_NOT_ACTIVE);
            }
            // 校验活动总次数限制
            if (campaign.getUsageLimit() != null && campaign.getUsageLimit() > 0) {
                int currentUsed = campaign.getUsedCount() != null ? campaign.getUsedCount() : 0;
                if (currentUsed + quantity > campaign.getUsageLimit()) {
                    throw new BusinessException(ErrorCode.CAMPAIGN_MEMBER_LIMIT);
                }
            }
            // 校验每人参与次数限制
            if (campaign.getMemberLimit() != null && campaign.getMemberLimit() > 0) {
                Long memberUsedCount = couponMapper.selectCount(
                        new LambdaQueryWrapper<Coupon>()
                                .eq(Coupon::getMemberId, request.getMemberId())
                                .eq(Coupon::getCampaignId, request.getCampaignId()));
                if (memberUsedCount + quantity > campaign.getMemberLimit()) {
                    throw new BusinessException(ErrorCode.CAMPAIGN_MEMBER_LIMIT);
                }
            }
        }

        // 4. 批量发放优惠券
        Set<String> usedCodes = new HashSet<>();
        for (int i = 0; i < quantity; i++) {
            Coupon coupon = new Coupon();
            coupon.setMemberId(request.getMemberId());
            coupon.setTemplateId(request.getTemplateId());
            coupon.setCampaignId(request.getCampaignId());
            coupon.setCouponCode(generateUniqueCouponCode(usedCodes));
            coupon.setFaceValue(template.getFaceValue());
            coupon.setStatus(CouponStatus.UNUSED.getCode());

            // 计算过期时间
            LocalDateTime expireAt;
            if (template.getValidEnd() != null) {
                expireAt = template.getValidEnd().atStartOfDay();
            } else if (template.getValidDays() != null && template.getValidDays() > 0) {
                expireAt = LocalDateTime.now().plusDays(template.getValidDays());
            } else {
                expireAt = LocalDateTime.now().plusDays(30);
            }
            coupon.setExpireAt(expireAt);

            couponMapper.insert(coupon);
            usedCodes.add(coupon.getCouponCode());
        }

        // 5. 更新活动已使用次数
        if (request.getCampaignId() != null) {
            Campaign campaign = campaignMapper.selectById(request.getCampaignId());
            if (campaign != null) {
                int currentUsed = campaign.getUsedCount() != null ? campaign.getUsedCount() : 0;
                campaign.setUsedCount(currentUsed + quantity);
                campaignMapper.updateById(campaign);
            }
        }

        log.info("发放优惠券成功，模板ID: {}, 数量: {}", request.getTemplateId(), quantity);
        return quantity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CouponResponse redeemCoupon(Long couponId, Long orderId) {
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null) {
            throw new BusinessException(ErrorCode.COUPON_NOT_FOUND);
        }

        // 校验状态：只有未使用的券才能核销
        if (coupon.getStatus() != CouponStatus.UNUSED.getCode()) {
            if (coupon.getStatus() == CouponStatus.USED.getCode()) {
                throw new BusinessException(ErrorCode.COUPON_ALREADY_USED);
            }
            if (coupon.getStatus() == CouponStatus.EXPIRED.getCode()) {
                throw new BusinessException(ErrorCode.COUPON_EXPIRED);
            }
            throw new BusinessException(ErrorCode.COUPON_NOT_FOUND);
        }

        // 校验是否已过期
        if (coupon.getExpireAt() != null && LocalDateTime.now().isAfter(coupon.getExpireAt())) {
            // 自动标记为过期
            coupon.setStatus(CouponStatus.EXPIRED.getCode());
            couponMapper.updateById(coupon);
            throw new BusinessException(ErrorCode.COUPON_EXPIRED);
        }

        // 更新为已使用
        coupon.setStatus(CouponStatus.USED.getCode());
        coupon.setUsedAt(LocalDateTime.now());
        coupon.setUsedOrderId(orderId);
        couponMapper.updateById(coupon);

        log.info("优惠券核销成功，券ID: {}, 订单ID: {}", couponId, orderId);
        return CouponResponse.fromEntity(coupon);
    }

    @Override
    @Scheduled(cron = "0 0 1 * * ?")
    public int processExpiredCoupons() {
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<Coupon> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Coupon::getStatus, CouponStatus.UNUSED.getCode())
                .lt(Coupon::getExpireAt, now)
                .set(Coupon::getStatus, CouponStatus.EXPIRED.getCode());

        int updated = couponMapper.update(null, wrapper);
        if (updated > 0) {
            log.info("自动过期处理完成，共标记 {} 张优惠券为已过期", updated);
        }
        return updated;
    }

    @Override
    public PageResult<CouponResponse> getCouponRecords(CouponRecordsQueryRequest request) {
        Page<Coupon> page = new Page<>(request.getPage(), request.getSize());
        LambdaQueryWrapper<Coupon> wrapper = new LambdaQueryWrapper<>();

        if (request.getMemberId() != null) {
            wrapper.eq(Coupon::getMemberId, request.getMemberId());
        }
        if (request.getStatus() != null) {
            wrapper.eq(Coupon::getStatus, request.getStatus());
        }
        if (request.getTemplateId() != null) {
            wrapper.eq(Coupon::getTemplateId, request.getTemplateId());
        }

        wrapper.orderByDesc(Coupon::getCreatedAt);

        IPage<Coupon> resultPage = couponMapper.selectPage(page, wrapper);
        return PageResult.from(resultPage).map(CouponResponse::fromEntity);
    }

    /**
     * 生成唯一券码（带重试机制）。
     */
    private String generateUniqueCouponCode(Set<String> existingCodes) {
        for (int retry = 0; retry < MAX_CODE_RETRY; retry++) {
            String code = generateCouponCode();
            if (existingCodes.contains(code)) {
                continue;
            }
            // 检查数据库中是否已存在
            Long count = couponMapper.selectCount(
                    new LambdaQueryWrapper<Coupon>().eq(Coupon::getCouponCode, code));
            if (count == 0) {
                return code;
            }
        }
        // 极端情况下使用时间戳 + 纳秒保证唯一
        return COUPON_CODE_PREFIX + System.currentTimeMillis() + SECURE_RANDOM.nextInt(100000);
    }

    private String generateCouponCode() {
        String timestamp = String.valueOf(System.currentTimeMillis()).substring(5);
        String random = String.format("%06d", SECURE_RANDOM.nextInt(1000000));
        return COUPON_CODE_PREFIX + timestamp + random;
    }
}
