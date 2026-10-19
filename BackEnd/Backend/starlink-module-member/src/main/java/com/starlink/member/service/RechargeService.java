package com.starlink.member.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.starlink.common.exception.BusinessException;
import com.starlink.common.result.ErrorCode;
import com.starlink.common.util.PageQuery;
import com.starlink.common.util.PageResult;
import com.starlink.member.dto.req.RechargeRequest;
import com.starlink.member.dto.resp.RechargeRecordResponse;
import com.starlink.member.dto.resp.RechargePreviewResponse;
import com.starlink.member.entity.Member;
import com.starlink.member.entity.MemberLevel;
import com.starlink.member.entity.MemberRecharge;
import com.starlink.member.mapper.MemberLevelMapper;
import com.starlink.member.mapper.MemberMapper;
import com.starlink.member.mapper.MemberRechargeMapper;
import com.starlink.member.security.MemberAccessGuard;
import com.starlink.common.constant.CommonConstants;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RechargeService {

    private final MemberRechargeMapper rechargeMapper;
    private final MemberMapper memberMapper;
    private final MemberLevelMapper levelMapper;
    private final GrowthValueService growthValueService;
    private final PointsService pointsService;
    private final BalanceService balanceService;
    private final RechargeBonusCalculator bonusCalculator;
    private final MemberAccessGuard accessGuard;

    @Transactional
    public RechargeRecordResponse recharge(RechargeRequest request) {
        accessGuard.assertRechargeOperator();
        if (request.getMemberId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "会员ID不能为空");
        }
        BigDecimal amount = validateAmount(request.getAmount());
        if (request.getPaymentMethod() == null || request.getPaymentMethod() != CommonConstants.PAY_TYPE_CASH) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(),
                    "仅支持由收银员确认的现金充值；线上支付须等待支付通道确认后才能入账");
        }

        // 同一会员的充值、余额和积分变动从同一把行锁开始，确保重试与并发请求串行。
        Member member = memberMapper.selectByIdForUpdate(request.getMemberId());
        if (member == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "会员不存在");
        }
        if (member.getStatus() == null || member.getStatus() != 1) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED.getCode(), "会员账号不可用");
        }

        String rechargeNo = generateRechargeNo(request.getMemberId(), request.getIdempotentKey());
        if (request.getIdempotentKey() != null && !request.getIdempotentKey().isBlank()) {
            MemberRecharge existing = rechargeMapper.selectOne(new LambdaQueryWrapper<MemberRecharge>()
                    .eq(MemberRecharge::getRechargeNo, rechargeNo));
            if (existing != null) {
                if (existing.getStatus() != null && existing.getStatus() == 1
                        && existing.getMemberId().equals(request.getMemberId())
                        && existing.getRechargeAmount().compareTo(amount) == 0
                        && existing.getPaymentMethod().equals(request.getPaymentMethod())) {
                    return getRechargeById(existing.getId());
                }
                throw new BusinessException(ErrorCode.CONFLICT.getCode(), "幂等键已用于不同的充值请求");
            }
        }

        RechargeBonusCalculator.BonusResult bonus = bonusCalculator.calculate(amount);
        BigDecimal bonusAmount = bonus.bonusAmount();
        BigDecimal totalAmount = amount.add(bonusAmount);

        BigDecimal balanceBefore = member.getBalance() == null ? BigDecimal.ZERO : member.getBalance();
        BigDecimal balanceAfter = balanceBefore.add(totalAmount);

        MemberRecharge recharge = new MemberRecharge();
        recharge.setMemberId(request.getMemberId());
        recharge.setRechargeNo(rechargeNo);
        recharge.setRechargeAmount(amount);
        recharge.setBonusAmount(bonusAmount);
        recharge.setTotalAmount(totalAmount);
        recharge.setBalanceBefore(balanceBefore);
        recharge.setBalanceAfter(balanceAfter);
        recharge.setPaymentMethod(request.getPaymentMethod());
        recharge.setOperatorId(accessGuard.currentStaffId());
        recharge.setCampaignId(bonus.campaignId());
        recharge.setStatus((byte) 1);
        recharge.setPaidAt(LocalDateTime.now());

        rechargeMapper.insert(recharge);

        balanceService.addBalance(request.getMemberId(), totalAmount,
                (byte) CommonConstants.BALANCE_BIZ_RECHARGE, recharge.getId(), "现金充值入账");
        if (memberMapper.incrementTotalRecharge(request.getMemberId(), amount) != 1) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), "累计充值更新失败，已回滚");
        }

        MemberLevel level = member.getLevelId() == null ? null : levelMapper.selectById(member.getLevelId());
        BigDecimal multiple = level == null || level.getPointsMultiple() == null
                ? BigDecimal.ONE : level.getPointsMultiple();
        int giftPoints = amount.multiply(multiple).setScale(0, RoundingMode.DOWN).intValueExact();
        if (giftPoints > 0) {
            pointsService.addPoints(request.getMemberId(), giftPoints,
                    (byte) CommonConstants.POINTS_BIZ_RECHARGE_GIFT, recharge.getId(), "充值赠送积分");
        }

        // 充值获得成长值：按实付金额（1元=1经验，向下取整），赠送金额不计经验
        growthValueService.addGrowth(request.getMemberId(),
                amount.setScale(0, RoundingMode.DOWN).intValue(),
                (byte) CommonConstants.GROWTH_BIZ_RECHARGE, recharge.getId(),
                "余额充值获得成长值（实付" + amount + "元）");

        log.info("会员充值成功: memberId={}, amount={}, bonus={}", 
                request.getMemberId(), amount, bonusAmount);

        return getRechargeById(recharge.getId());
    }

    public PageResult<RechargeRecordResponse> getRechargeRecords(Long memberId, String startTime, 
                                                                 String endTime, Byte status, 
                                                                 PageQuery pageQuery) {
        accessGuard.assertMemberLedgerAccess(memberId);
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
        accessGuard.assertMemberLedgerAccess(response.getMemberId());
        response.setPaymentMethodLabel(getPaymentMethodLabel(response.getPaymentMethod()));
        response.setStatusLabel(getStatusLabel(response.getStatus()));
        return response;
    }

    /**
     * 充值试算（预览）：校验会员后按充值金额计算「实付 / 赠送 / 实际到账」。
     * <p>
     * 只读接口，不做任何写入；campaignId 对应营销活动赠送，当前无活动配置时为 null。
     */
    public RechargePreviewResponse preview(Long memberId, BigDecimal amount) {
        accessGuard.assertMemberLedgerAccess(memberId);
        amount = validateAmount(amount);
        Member member = memberMapper.selectById(memberId);
        if (member == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "会员不存在");
        }
        if (member.getStatus() == null || member.getStatus() != 1) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED.getCode(), "会员账号不可用");
        }
        RechargeBonusCalculator.BonusResult bonus = bonusCalculator.calculate(amount);
        BigDecimal bonusAmount = bonus.bonusAmount();
        RechargePreviewResponse resp = new RechargePreviewResponse();
        resp.setMemberId(memberId);
        resp.setRechargeAmount(amount);
        resp.setBonusAmount(bonusAmount);
        resp.setTotalAmount(amount.add(bonusAmount));
        resp.setCampaignId(bonus.campaignId());
        return resp;
    }

    private BigDecimal validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ONE) < 0
                || amount.compareTo(BigDecimal.valueOf(RechargeRequest.MAX_AMOUNT)) > 0
                || amount.scale() > 2) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "充值金额须在 1 至 1000000 元之间且最多两位小数");
        }
        return amount;
    }

    private String generateRechargeNo(Long memberId, String idempotentKey) {
        if (idempotentKey == null || idempotentKey.isBlank()) {
            return "RCH" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
        if (idempotentKey.length() > 64) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "幂等键过长");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    (memberId + ":" + idempotentKey.trim()).getBytes(StandardCharsets.UTF_8));
            return "RCH" + HexFormat.of().formatHex(digest).substring(0, 29).toUpperCase();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
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
