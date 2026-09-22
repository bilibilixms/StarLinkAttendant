package com.starlink.member.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.starlink.common.exception.BusinessException;
import com.starlink.common.result.ErrorCode;
import com.starlink.common.util.PageQuery;
import com.starlink.common.util.PageResult;
import com.starlink.member.dto.req.RechargeRequest;
import com.starlink.member.dto.resp.RechargeRecordResponse;
import com.starlink.member.entity.Member;
import com.starlink.member.entity.MemberLevel;
import com.starlink.member.entity.MemberRecharge;
import com.starlink.member.mapper.MemberLevelMapper;
import com.starlink.member.mapper.MemberMapper;
import com.starlink.member.mapper.MemberRechargeMapper;
import com.starlink.common.constant.CommonConstants;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RechargeService {

    private final MemberRechargeMapper rechargeMapper;
    private final MemberMapper memberMapper;
    private final MemberLevelMapper levelMapper;
    private final GrowthValueService growthValueService;

    @Transactional
    public RechargeRecordResponse recharge(RechargeRequest request) {
        Member member = memberMapper.selectById(request.getMemberId());
        if (member == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "会员不存在");
        }

        if (member.getStatus() == 3) {
            throw new BusinessException(ErrorCode.MEMBER_BLACKLISTED);
        }

        // 充值不允许用会员余额支付（余额只能通过充值获得），防止凭空生钱
        if (request.getPaymentMethod() == null || request.getPaymentMethod() < 1 || request.getPaymentMethod() > 3) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "充值支付方式仅支持：1-现金 2-微信 3-支付宝");
        }

        BigDecimal bonusAmount = calculateBonus(request.getMemberId(), request.getAmount());
        BigDecimal totalAmount = request.getAmount().add(bonusAmount);

        BigDecimal balanceBefore = member.getBalance();
        BigDecimal balanceAfter = balanceBefore.add(totalAmount);

        MemberRecharge recharge = new MemberRecharge();
        recharge.setMemberId(request.getMemberId());
        recharge.setRechargeNo(generateRechargeNo());
        recharge.setRechargeAmount(request.getAmount());
        recharge.setBonusAmount(bonusAmount);
        recharge.setTotalAmount(totalAmount);
        recharge.setBalanceBefore(balanceBefore);
        recharge.setBalanceAfter(balanceAfter);
        recharge.setPaymentMethod(request.getPaymentMethod());
        recharge.setOperatorId(request.getOperatorId());
        recharge.setCampaignId(request.getCampaignId());
        recharge.setStatus((byte) 1);
        recharge.setPaidAt(LocalDateTime.now());

        rechargeMapper.insert(recharge);

        member.setBalance(balanceAfter);
        member.setTotalRecharge(member.getTotalRecharge().add(request.getAmount()));
        memberMapper.updateById(member);

        // 充值获得成长值：按实付金额（1元=1经验，向下取整），赠送金额不计经验
        growthValueService.addGrowth(request.getMemberId(),
                request.getAmount().setScale(0, RoundingMode.DOWN).intValue(),
                (byte) CommonConstants.GROWTH_BIZ_RECHARGE, recharge.getId(),
                "余额充值获得成长值（实付" + request.getAmount() + "元）");

        log.info("会员充值成功: memberId={}, amount={}, bonus={}", 
                request.getMemberId(), request.getAmount(), bonusAmount);

        return getRechargeById(recharge.getId());
    }

    public PageResult<RechargeRecordResponse> getRechargeRecords(Long memberId, String startTime, 
                                                                 String endTime, Byte status, 
                                                                 PageQuery pageQuery) {
        Page<RechargeRecordResponse> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());
        IPage<RechargeRecordResponse> result = rechargeMapper.selectRechargePage(
                page, memberId, startTime, endTime, status);

        for (RechargeRecordResponse item : result.getRecords()) {
            item.setPaymentMethodLabel(getPaymentMethodLabel(item.getPaymentMethod()));
            item.setStatusLabel(getStatusLabel(item.getStatus()));
        }

        return PageResult.of(result);
    }

    public RechargeRecordResponse getRechargeById(Long id) {
        RechargeRecordResponse response = rechargeMapper.selectRechargeDetail(id);
        if (response == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        response.setPaymentMethodLabel(getPaymentMethodLabel(response.getPaymentMethod()));
        response.setStatusLabel(getStatusLabel(response.getStatus()));
        return response;
    }

    private BigDecimal calculateBonus(Long memberId, BigDecimal amount) {
        Member member = memberMapper.selectById(memberId);
        if (member.getLevelId() == null) {
            return BigDecimal.ZERO;
        }

        MemberLevel level = levelMapper.selectById(member.getLevelId());
        if (level == null || level.getRechargeBonusRate() == null) {
            return BigDecimal.ZERO;
        }

        return amount.multiply(level.getRechargeBonusRate()).divide(new BigDecimal("100"), 2, BigDecimal.ROUND_HALF_UP);
    }

    private String generateRechargeNo() {
        return "RCH" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }

    private String getPaymentMethodLabel(Byte method) {
        if (method == null) return "";
        return switch (method) {
            case 1 -> "现金";
            case 2 -> "微信";
            case 3 -> "支付宝";
            default -> "未知";
        };
    }

    private String getStatusLabel(Byte status) {
        if (status == null) return "";
        return switch (status) {
            case 0 -> "待支付";
            case 1 -> "成功";
            case 2 -> "失败";
            case 3 -> "已退款";
            default -> "未知";
        };
    }
}