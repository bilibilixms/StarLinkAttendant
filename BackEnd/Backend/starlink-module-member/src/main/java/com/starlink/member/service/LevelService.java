package com.starlink.member.service;

import com.starlink.common.exception.BusinessException;
import com.starlink.common.result.ErrorCode;
import com.starlink.member.dto.req.LevelCreateRequest;
import com.starlink.member.dto.req.LevelUpdateRequest;
import com.starlink.member.dto.resp.LevelResponse;
import com.starlink.member.entity.MemberLevel;
import com.starlink.member.mapper.MemberLevelMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LevelService {

    private final MemberLevelMapper levelMapper;

    public List<LevelResponse> listLevels() {
        return levelMapper.selectAllOrderByLevel().stream()
                .map(this::convertToResponse)
                .toList();
    }

    @Transactional
    public LevelResponse createLevel(LevelCreateRequest request) {
        MemberLevel level = new MemberLevel();
        level.setLevelName(request.getLevelName());
        level.setLevelOrder(request.getLevelOrder());
        level.setMinGrowth(request.getMinGrowth());
        level.setMaxGrowth(request.getMaxGrowth());
        level.setDiscountRate(request.getDiscountRate());
        level.setHourlyDiscount(request.getHourlyDiscount());
        level.setRechargeBonusRate(request.getRechargeBonusRate());
        level.setPointsMultiple(request.getPointsMultiple());
        level.setAutoUpgrade(request.getAutoUpgrade());
        level.setIconUrl(request.getIconUrl());

        levelMapper.insert(level);
        log.info("创建会员等级: {}", level.getLevelName());

        return convertToResponse(level);
    }

    public LevelResponse getLevelById(Long id) {
        MemberLevel level = levelMapper.selectById(id);
        if (level == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return convertToResponse(level);
    }

    @Transactional
    public LevelResponse updateLevel(Long id, LevelUpdateRequest request) {
        MemberLevel level = levelMapper.selectById(id);
        if (level == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        if (request.getLevelName() != null) {
            level.setLevelName(request.getLevelName());
        }
        if (request.getLevelOrder() != null) {
            level.setLevelOrder(request.getLevelOrder());
        }
        if (request.getMinGrowth() != null) {
            level.setMinGrowth(request.getMinGrowth());
        }
        if (request.getMaxGrowth() != null) {
            level.setMaxGrowth(request.getMaxGrowth());
        }
        if (request.getDiscountRate() != null) {
            level.setDiscountRate(request.getDiscountRate());
        }
        if (request.getHourlyDiscount() != null) {
            level.setHourlyDiscount(request.getHourlyDiscount());
        }
        if (request.getRechargeBonusRate() != null) {
            level.setRechargeBonusRate(request.getRechargeBonusRate());
        }
        if (request.getPointsMultiple() != null) {
            level.setPointsMultiple(request.getPointsMultiple());
        }
        if (request.getAutoUpgrade() != null) {
            level.setAutoUpgrade(request.getAutoUpgrade());
        }
        if (request.getIconUrl() != null) {
            level.setIconUrl(request.getIconUrl());
        }

        levelMapper.updateById(level);
        log.info("更新会员等级: id={}", id);

        return convertToResponse(level);
    }

    @Transactional
    public void deleteLevel(Long id) {
        MemberLevel level = levelMapper.selectById(id);
        if (level == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        levelMapper.deleteById(id);
        log.info("删除会员等级: id={}", id);
    }

    private LevelResponse convertToResponse(MemberLevel level) {
        LevelResponse response = new LevelResponse();
        response.setId(level.getId());
        response.setLevelName(level.getLevelName());
        response.setLevelOrder(level.getLevelOrder());
        response.setMinGrowth(level.getMinGrowth());
        response.setMaxGrowth(level.getMaxGrowth());
        response.setDiscountRate(level.getDiscountRate());
        response.setHourlyDiscount(level.getHourlyDiscount());
        response.setRechargeBonusRate(level.getRechargeBonusRate());
        response.setPointsMultiple(level.getPointsMultiple());
        response.setAutoUpgrade(level.getAutoUpgrade());
        response.setIconUrl(level.getIconUrl());
        response.setCreatedAt(level.getCreatedAt());
        response.setUpdatedAt(level.getUpdatedAt());
        return response;
    }
}