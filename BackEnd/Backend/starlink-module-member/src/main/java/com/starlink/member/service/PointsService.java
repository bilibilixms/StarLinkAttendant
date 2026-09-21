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
import com.starlink.member.security.MemberAccessGuard;

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
    /** P0-4：会员账务资源归属校验 */
    private final MemberAccessGuard accessGuard;

    @Transactional
    public void addPoints(Long memberId, Integer points, Byte bizType, Long bizId, String remark) {
        if (points == null || points <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "增加积分必须大于0");
        }
        // 悲观行锁：与 BalanceService 一致，保证同一会员的积分/余额变动串行且不丢更新
        Member member = memberMapper.selectByIdForUpdate(memberId);
        if (member == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "会员不存在");
        }

        long pointsBefore = member.getAvailablePoints() == null ? 0L : member.getAvailablePoints();
        long pointsAfter = pointsBefore + points;
        long totalBefore = member.getTotalPoints() == null ? 0L : member.getTotalPoints();

        // 积分流水表 member_points_log 的 points/balance_before/balance_after 为 INT，
        // 必须显式拒绝越界，否则强转会静默变成负数，导致流水与会员积分不一致。
        if (pointsAfter > Integer.MAX_VALUE) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(),
                    "积分超出系统可记录上限（" + Integer.MAX_VALUE + "），已拒绝本次积分变动");
        }

        MemberPointsLog logEntry = new MemberPointsLog();
        logEntry.setMemberId(memberId);
        logEntry.setPoints(points);
        logEntry.setBalanceBefore((int) pointsBefore);
        logEntry.setBalanceAfter((int) pointsAfter);
        logEntry.setBizType(bizType);
        logEntry.setBizId(bizId);
        logEntry.setRemark(remark);
        pointsLogMapper.insert(logEntry);

        member.setAvailablePoints(pointsAfter);
        member.setTotalPoints(totalBefore + points);
        int rows = memberMapper.updateById(member);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(),
                    "积分更新冲突（会员资产已被并发修改），本次变动已回滚，请重试");
        }

        log.info("会员积分增加: memberId={}, points={}, before={}, after={}",
                memberId, points, pointsBefore, pointsAfter);
    }

    @Transactional
    public void deductPoints(Long memberId, Integer points, Byte bizType, Long bizId, String remark) {
        if (points == null || points <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "扣减积分必须大于0");
        }
        Member member = memberMapper.selectByIdForUpdate(memberId);
        if (member == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "会员不存在");
        }

        long pointsBefore = member.getAvailablePoints() == null ? 0L : member.getAvailablePoints();
        if (pointsBefore < points) {
            throw new BusinessException(ErrorCode.BALANCE_INSUFFICIENT.getCode(), "积分不足");
        }
        long pointsAfter = pointsBefore - points;

        MemberPointsLog logEntry = new MemberPointsLog();
        logEntry.setMemberId(memberId);
        logEntry.setPoints(-points);
        logEntry.setBalanceBefore((int) pointsBefore);
        logEntry.setBalanceAfter((int) pointsAfter);
        logEntry.setBizType(bizType);
        logEntry.setBizId(bizId);
        logEntry.setRemark(remark);
        pointsLogMapper.insert(logEntry);

        member.setAvailablePoints(pointsAfter);
        int rows = memberMapper.updateById(member);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(),
                    "积分更新冲突（会员资产已被并发修改），本次变动已回滚，请重试");
        }

        log.info("会员积分扣除: memberId={}, points={}, before={}, after={}",
                memberId, points, pointsBefore, pointsAfter);
    }

    public PageResult<PointsRecordResponse> getPointsRecords(Long memberId, Byte bizType,
                                                             String startTime, String endTime,
                                                             PageQuery pageQuery) {
        // P0-4 资源归属：员工须有充值操作权限；会员只能查询自己的积分流水
        accessGuard.assertMemberLedgerAccess(memberId);

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