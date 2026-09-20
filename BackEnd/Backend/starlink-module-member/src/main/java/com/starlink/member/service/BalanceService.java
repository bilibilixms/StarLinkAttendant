package com.starlink.member.service;

import com.starlink.common.exception.BusinessException;
import com.starlink.common.result.ErrorCode;
import com.starlink.member.entity.Member;
import com.starlink.member.entity.MemberBalanceLog;
import com.starlink.member.mapper.MemberBalanceLogMapper;
import com.starlink.member.mapper.MemberMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 会员余额服务：扣减/增加余额并记流水。
 * 供收银等模块调用，保证余额变动可审计、账实一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BalanceService {

    private final MemberMapper memberMapper;
    private final MemberBalanceLogMapper balanceLogMapper;

    /**
     * 扣减会员余额（消费等场景）。
     *
     * @param memberId 会员ID
     * @param amount   扣减金额（正数）
     * @param bizType  业务类型（见 CommonConstants.BALANCE_BIZ_*）
     * @param bizId    业务单据ID
     * @param remark   备注
     */
    @Transactional
    public void deductBalance(Long memberId, BigDecimal amount, Byte bizType, Long bizId, String remark) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "扣减金额必须大于0");
        }
        Member member = memberMapper.selectById(memberId);
        if (member == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "会员不存在");
        }
        BigDecimal balanceBefore = member.getBalance() == null ? BigDecimal.ZERO : member.getBalance();
        if (balanceBefore.compareTo(amount) < 0) {
            throw new BusinessException(ErrorCode.BALANCE_INSUFFICIENT);
        }
        BigDecimal balanceAfter = balanceBefore.subtract(amount);

        MemberBalanceLog logEntry = new MemberBalanceLog();
        logEntry.setMemberId(memberId);
        logEntry.setAmount(amount.negate());
        logEntry.setBalanceBefore(balanceBefore);
        logEntry.setBalanceAfter(balanceAfter);
        logEntry.setBizType(bizType);
        logEntry.setBizId(bizId);
        logEntry.setRemark(remark);
        balanceLogMapper.insert(logEntry);

        member.setBalance(balanceAfter);
        memberMapper.updateById(member);

        log.info("会员余额扣减: memberId={}, amount={}, after={}", memberId, amount, balanceAfter);
    }

    /**
     * 增加会员余额（退款回充等场景）。
     *
     * @param amount 增加金额（正数）
     */
    @Transactional
    public void addBalance(Long memberId, BigDecimal amount, Byte bizType, Long bizId, String remark) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "增加金额必须大于0");
        }
        Member member = memberMapper.selectById(memberId);
        if (member == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "会员不存在");
        }
        BigDecimal balanceBefore = member.getBalance() == null ? BigDecimal.ZERO : member.getBalance();
        BigDecimal balanceAfter = balanceBefore.add(amount);

        MemberBalanceLog logEntry = new MemberBalanceLog();
        logEntry.setMemberId(memberId);
        logEntry.setAmount(amount);
        logEntry.setBalanceBefore(balanceBefore);
        logEntry.setBalanceAfter(balanceAfter);
        logEntry.setBizType(bizType);
        logEntry.setBizId(bizId);
        logEntry.setRemark(remark);
        balanceLogMapper.insert(logEntry);

        member.setBalance(balanceAfter);
        memberMapper.updateById(member);

        log.info("会员余额增加: memberId={}, amount={}, after={}", memberId, amount, balanceAfter);
    }
}
