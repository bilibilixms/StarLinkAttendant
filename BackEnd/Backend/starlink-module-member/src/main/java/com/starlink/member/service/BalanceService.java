package com.starlink.member.service;

import com.starlink.common.exception.BusinessException;
import com.starlink.common.result.ErrorCode;
import com.starlink.member.entity.Member;
import com.starlink.member.entity.MemberBalanceLog;
import com.starlink.member.entity.MemberLevel;
import com.starlink.member.mapper.MemberBalanceLogMapper;
import com.starlink.member.mapper.MemberLevelMapper;
import com.starlink.member.mapper.MemberMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 会员余额服务：<b>余额变动的唯一入口</b>，扣减/增加余额并同步写入流水。
 * <p>
 * 不变量（任何调用方都可依赖）：
 * <ol>
 *   <li>每一次余额变化都在 {@code member_balance_log} 留下一条流水；</li>
 *   <li>流水满足 {@code balance_after = balance_before + amount}；</li>
 *   <li>提交后 {@code member.balance == 该会员最新一条流水的 balance_after}；</li>
 *   <li>流水与余额更新<b>同一事务</b>，要么都成功、要么都回滚。</li>
 * </ol>
 * 并发安全：先以 {@code SELECT ... FOR UPDATE} 锁定会员行，再读→算→写，
 * 因此并发余额变动被串行化：既不丢更新，也不会出现「乐观锁冲突导致假失败」，
 * 且流水的 before/after 精确反映真实余额。
 * <p>
 * 因此<b>任何余额变动都必须经由本类</b>，不得由业务代码自行 {@code member.setBalance(...)}
 * 后 {@code updateById}（那会绕过流水，破坏第 1/3 条不变量）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BalanceService {

    private final MemberMapper memberMapper;
    private final MemberBalanceLogMapper balanceLogMapper;
    private final MemberLevelMapper levelMapper;

    /**
     * 扣减会员余额（消费等场景）。
     * 允许有限透支：余额非负时放行，但扣减后不得低于所属等级的透支额度负值；
     * 已透支（余额为负）则直接拒绝，需先充值回正。
     *
     * @param memberId 会员ID
     * @param amount   扣减金额（正数）
     * @param bizType  业务类型（见 CommonConstants.BALANCE_BIZ_*）
     * @param bizId    业务单据ID
     * @param remark   备注
     * @return 扣减后的余额
     */
    @Transactional
    public BigDecimal deductBalance(Long memberId, BigDecimal amount, Byte bizType, Long bizId, String remark) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "扣减金额必须大于0");
        }
        // 悲观行锁：锁定后本次读到的余额在提交前不会被其他事务改动
        Member member = memberMapper.selectByIdForUpdate(memberId);
        if (member == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "会员不存在");
        }
        BigDecimal balanceBefore = member.getBalance() == null ? BigDecimal.ZERO : member.getBalance();
        if (balanceBefore.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.BALANCE_INSUFFICIENT.getCode(),
                    "余额已透支（" + balanceBefore + " 元），请先充值后再消费");
        }
        BigDecimal creditLimit = resolveCreditLimit(member);
        BigDecimal balanceAfter = balanceBefore.subtract(amount);
        if (balanceAfter.compareTo(creditLimit.negate()) < 0) {
            throw new BusinessException(ErrorCode.BALANCE_INSUFFICIENT.getCode(),
                    "超出透支额度：当前余额 " + balanceBefore + " 元，本笔消费 " + amount
                            + " 元，最低可透支至 -" + creditLimit + " 元，请先充值或改用其他支付方式");
        }

        persistChange(member, amount.negate(), balanceBefore, balanceAfter, bizType, bizId, remark);
        log.info("会员余额扣减: memberId={}, amount={}, before={}, after={}",
                memberId, amount, balanceBefore, balanceAfter);
        return balanceAfter;
    }

    /**
     * 增加会员余额（充值入账、退款回充等场景）。
     *
     * @param amount 增加金额（正数）
     * @return 增加后的余额
     */
    @Transactional
    public BigDecimal addBalance(Long memberId, BigDecimal amount, Byte bizType, Long bizId, String remark) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "增加金额必须大于0");
        }
        // 悲观行锁：与 deductBalance 使用同一把锁，保证同一会员的余额变动严格串行
        Member member = memberMapper.selectByIdForUpdate(memberId);
        if (member == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "会员不存在");
        }
        BigDecimal balanceBefore = member.getBalance() == null ? BigDecimal.ZERO : member.getBalance();
        BigDecimal balanceAfter = balanceBefore.add(amount);

        persistChange(member, amount, balanceBefore, balanceAfter, bizType, bizId, remark);
        log.info("会员余额增加: memberId={}, amount={}, before={}, after={}",
                memberId, amount, balanceBefore, balanceAfter);
        return balanceAfter;
    }

    /**
     * 写流水 + 更新余额（同一事务）。
     * <p>
     * 流水先写、余额后改；两者同事务，任一步失败整体回滚，不会出现「有流水无余额」或反之。
     * 余额更新校验影响行数：为 0 说明发生了预期外的并发修改，直接失败回滚
     * （宁可让调用方重试，也不允许静默丢更新导致账实不一致）。
     */
    private void persistChange(Member member, BigDecimal signedAmount, BigDecimal balanceBefore,
                               BigDecimal balanceAfter, Byte bizType, Long bizId, String remark) {
        MemberBalanceLog logEntry = new MemberBalanceLog();
        logEntry.setMemberId(member.getId());
        logEntry.setAmount(signedAmount);
        logEntry.setBalanceBefore(balanceBefore);
        logEntry.setBalanceAfter(balanceAfter);
        logEntry.setBizType(bizType);
        logEntry.setBizId(bizId);
        logEntry.setRemark(remark);
        balanceLogMapper.insert(logEntry);

        member.setBalance(balanceAfter);
        int rows = memberMapper.updateById(member);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(),
                    "余额更新冲突（会员余额已被并发修改），本次变动已回滚，请重试");
        }
    }

    /**
     * 解析会员所属等级的透支额度；无等级或等级不存在时按 0 处理（不允许透支）。
     */
    private BigDecimal resolveCreditLimit(Member member) {
        if (member.getLevelId() == null) {
            return BigDecimal.ZERO;
        }
        MemberLevel level = levelMapper.selectById(member.getLevelId());
        if (level == null || level.getCreditLimit() == null) {
            return BigDecimal.ZERO;
        }
        return level.getCreditLimit();
    }
}
