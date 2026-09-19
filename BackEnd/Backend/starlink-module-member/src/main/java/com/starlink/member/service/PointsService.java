package com.starlink.member.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.starlink.common.exception.BusinessException;
import com.starlink.common.result.ErrorCode;
import com.starlink.common.util.PageQuery;
import com.starlink.common.util.PageResult;
import com.starlink.member.dto.resp.PointsRecordResponse;
import com.starlink.member.entity.Member;
import com.starlink.member.entity.MemberLevel;
import com.starlink.member.entity.MemberPointsLog;
import com.starlink.member.mapper.MemberLevelMapper;
import com.starlink.member.mapper.MemberMapper;
import com.starlink.member.mapper.MemberPointsLogMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PointsService {

    private final MemberPointsLogMapper pointsLogMapper;
    private final MemberMapper memberMapper;
    private final MemberLevelMapper levelMapper;

    @Transactional
    public void addPoints(Long memberId, Integer points, Byte bizType, Long bizId, String remark) {
        Member member = memberMapper.selectById(memberId);
        if (member == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "会员不存在");
        }

        int balanceBefore = member.getAvailablePoints().intValue();
        int balanceAfter = balanceBefore + points;

        MemberPointsLog logEntry = new MemberPointsLog();
        logEntry.setMemberId(memberId);
        logEntry.setPoints(points);
        logEntry.setBalanceBefore(balanceBefore);
        logEntry.setBalanceAfter(balanceAfter);
        logEntry.setBizType(bizType);
        logEntry.setBizId(bizId);
        logEntry.setRemark(remark);

        pointsLogMapper.insert(logEntry);

        member.setAvailablePoints((long) balanceAfter);
        member.setTotalPoints(member.getTotalPoints() + points);
        memberMapper.updateById(member);

        log.info("会员积分增加: memberId={}, points={}", memberId, points);
    }

    @Transactional
    public void deductPoints(Long memberId, Integer points, Byte bizType, Long bizId, String remark) {
        Member member = memberMapper.selectById(memberId);
        if (member == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "会员不存在");
        }

        if (member.getAvailablePoints() < points) {
            throw new BusinessException(ErrorCode.BALANCE_INSUFFICIENT.getCode(), "积分不足");
        }

        int balanceBefore = member.getAvailablePoints().intValue();
        int balanceAfter = balanceBefore - points;

        MemberPointsLog logEntry = new MemberPointsLog();
        logEntry.setMemberId(memberId);
        logEntry.setPoints(-points);
        logEntry.setBalanceBefore(balanceBefore);
        logEntry.setBalanceAfter(balanceAfter);
        logEntry.setBizType(bizType);
        logEntry.setBizId(bizId);
        logEntry.setRemark(remark);

        pointsLogMapper.insert(logEntry);

        member.setAvailablePoints((long) balanceAfter);
        memberMapper.updateById(member);

        log.info("会员积分扣除: memberId={}, points={}", memberId, points);
    }

    public PageResult<PointsRecordResponse> getPointsRecords(Long memberId, Byte bizType, 
                                                             String startTime, String endTime,
                                                             PageQuery pageQuery) {
        Page<PointsRecordResponse> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());
        IPage<PointsRecordResponse> result = pointsLogMapper.selectPointsPage(
                page, memberId, bizType, startTime, endTime);

        for (PointsRecordResponse item : result.getRecords()) {
            item.setBizTypeLabel(getBizTypeLabel(item.getBizType()));
        }

        return PageResult.of(result);
    }

    private String getBizTypeLabel(Byte type) {
        if (type == null) return "";
        return switch (type) {
            case 1 -> "消费获得";
            case 2 -> "充值赠送";
            case 3 -> "兑换消耗";
            case 4 -> "活动奖励";
            case 5 -> "手动调整";
            case 6 -> "过期扣除";
            default -> "未知";
        };
    }
}