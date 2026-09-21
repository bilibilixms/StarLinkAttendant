package com.starlink.member.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.starlink.common.constant.CommonConstants;
import com.starlink.common.exception.BusinessException;
import com.starlink.common.result.ErrorCode;
import com.starlink.common.util.PageQuery;
import com.starlink.common.util.PageResult;
import com.starlink.member.dto.req.RechargeRequest;
import com.starlink.member.dto.resp.RechargePreviewResponse;
import com.starlink.member.dto.resp.RechargeRecordResponse;
import com.starlink.member.entity.Member;
import com.starlink.member.entity.MemberLevel;
import com.starlink.member.entity.MemberRecharge;
import com.starlink.member.mapper.MemberLevelMapper;
import com.starlink.member.mapper.MemberMapper;
import com.starlink.member.mapper.MemberRechargeMapper;
import com.starlink.member.security.MemberAccessGuard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.UUID;

/**
 * 充值服务。
 *
 * <h3>金额语义（唯一口径，与 member_recharge 列注释一致）</h3>
 * <ul>
 *   <li>{@code recharge_amount} —— <b>实付充值金额</b>（客户端传入，服务端校验 ≥1）；</li>
 *   <li>{@code bonus_amount} —— <b>赠送金额</b>，由服务端按充值活动阶梯规则计算
 *       （{@link RechargeBonusCalculator}），客户端不可传入；</li>
 *   <li>{@code total_amount} —— <b>实际到账 = recharge_amount + bonus_amount</b>，记入余额；</li>
 *   <li>{@code member.total_recharge} —— 只累计<b>实付</b>部分（不含赠送）。</li>
 * </ul>
 *
 * <h3>赠送规则（本轮统一）</h3>
 * 只认「生效中的充值赠送活动（campaign_type=1）阶梯」。
 * 原先按 {@code member_level.recharge_bonus_rate} 的固定比例赠送（无门槛，导致充 100 也送 15%）
 * 已<b>不再参与</b>充值计算，两套规则合并为一套。
 *
 * <h3>幂等</h3>
 * 客户端携带 {@code idempotentKey} 时，服务端据此生成<b>确定性充值单号</b>，
 * 由 {@code member_recharge.uk_recharge_no} 唯一索引保证「同一请求只入账一次」：
 * <ul>
 *   <li>重复提交（串行）→ 命中已有单据，直接返回原结果，<b>不重复入账、不重复写流水</b>；</li>
 *   <li>并发提交（同一幂等键）→ 两条插入争抢同一单号，败者抛 {@link ErrorCode#RECHARGE_DUPLICATE}
 *       并整体回滚，同样不会重复入账。</li>
 * </ul>
 * 不依赖前端按钮防重复点击。幂等键为空时退化为「每次调用都是新充值」。
 *
 * <h3>账实一致</h3>
 * 余额变动一律委托 {@link BalanceService}（行锁 + 写 member_balance_log），
 * 本类<b>不</b>自行 {@code setBalance}；因此每次充值都有对应流水，
 * 且 {@code member.balance == 该会员最新流水的 balance_after}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RechargeService {

    private static final DateTimeFormatter DATE_PART = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final MemberRechargeMapper rechargeMapper;
    private final MemberMapper memberMapper;
    private final MemberLevelMapper levelMapper;
    /** P0-4：会员账务资源归属校验 */
    private final MemberAccessGuard accessGuard;
    /** 充值赠送规则的唯一权威实现 */
    private final RechargeBonusCalculator bonusCalculator;
    /** 余额变动的唯一入口（写流水） */
    private final BalanceService balanceService;
    /** 充值赠送积分 */
    private final PointsService pointsService;

    /**
     * 会员充值（服务端全权计算赠送与到账金额）。
     */
    @Transactional
    public RechargeRecordResponse recharge(RechargeRequest request) {
        // P0-4 资源归属：员工须有充值操作权限；会员只能给自己充值
        //（身份取自 SecurityContext，不信任 request.memberId 作为身份）
        accessGuard.assertMemberLedgerAccess(request.getMemberId());

        // 充值不允许用会员余额支付（余额只能通过充值获得），防止凭空生钱
        if (request.getPaymentMethod() == null || request.getPaymentMethod() < 1 || request.getPaymentMethod() > 3) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "充值支付方式仅支持：1-现金 2-微信 3-支付宝");
        }

        // ==================== 加锁顺序（务必先锁父行，再插子行） ====================
        // member_recharge / member_balance_log / member_points_log 均有指向 member 的外键，
        // 向子表插入会先在父行 member 上加【共享锁】；而余额更新需要【排他锁】。
        // 若先插子表再加锁，两个并发充值会各自持有 S 锁、又都想升级为 X 锁 —— InnoDB 判定死锁
        // （实测：DeadlockLoserDataAccessException）。
        // 因此这里【先】对会员行加排他锁，使同一会员的并发充值串行化，之后再插任何子表。
        Member member = memberMapper.selectByIdForUpdate(request.getMemberId());
        if (member == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "会员不存在");
        }
        if (member.getStatus() != null && member.getStatus() == 3) {
            throw new BusinessException(ErrorCode.MEMBER_BLACKLISTED);
        }

        BigDecimal rechargeAmount = request.getAmount().setScale(2, RoundingMode.HALF_UP);
        String idempotentKey = StringUtils.hasText(request.getIdempotentKey())
                ? request.getIdempotentKey().trim() : null;

        // ---- 幂等（串行重复提交）：命中已有单据直接返回原结果，不重复入账 ----
        if (idempotentKey != null) {
            String deterministicNo = buildDeterministicRechargeNo(request.getMemberId(), idempotentKey);
            MemberRecharge existing = findByRechargeNo(deterministicNo);
            if (existing != null) {
                log.info("充值幂等命中，返回原单据（不重复入账）: memberId={}, rechargeNo={}, idempotentKey={}",
                        request.getMemberId(), deterministicNo, idempotentKey);
                return getRechargeById(existing.getId());
            }
        }

        // ---- 服务端计算赠送与到账金额 ----
        RechargeBonusCalculator.BonusResult bonusResult = bonusCalculator.calculate(rechargeAmount);
        BigDecimal bonusAmount = bonusResult.bonusAmount();
        BigDecimal totalAmount = rechargeAmount.add(bonusAmount);

        // 先落充值单（取得 rechargeId，用作余额流水的 bizId）。
        // balance_before/after 稍后由 BalanceService 的实际结果回填，确保与流水完全一致。
        BigDecimal provisionalBefore = member.getBalance() == null ? BigDecimal.ZERO : member.getBalance();
        MemberRecharge recharge = new MemberRecharge();
        recharge.setMemberId(request.getMemberId());
        recharge.setRechargeNo(idempotentKey != null
                ? buildDeterministicRechargeNo(request.getMemberId(), idempotentKey)
                : generateRandomRechargeNo());
        recharge.setRechargeAmount(rechargeAmount);
        recharge.setBonusAmount(bonusAmount);
        recharge.setTotalAmount(totalAmount);
        recharge.setBalanceBefore(provisionalBefore);
        recharge.setBalanceAfter(provisionalBefore.add(totalAmount));
        recharge.setPaymentMethod(request.getPaymentMethod());
        recharge.setOperatorId(request.getOperatorId());
        recharge.setCampaignId(bonusResult.campaignId());
        recharge.setStatus((byte) 1);
        recharge.setPaidAt(LocalDateTime.now());

        try {
            rechargeMapper.insert(recharge);
        } catch (DuplicateKeyException e) {
            // 并发下的同一幂等键：唯一索引兜底，整体回滚，绝不重复入账
            log.warn("充值并发重复提交被唯一索引拦截: memberId={}, rechargeNo={}",
                    request.getMemberId(), recharge.getRechargeNo());
            throw new BusinessException(ErrorCode.RECHARGE_DUPLICATE);
        }

        // ---- 余额入账（行锁 + 写余额流水），回填权威 before/after 到充值单 ----
        BigDecimal balanceAfter = balanceService.addBalance(request.getMemberId(), totalAmount,
                (byte) CommonConstants.BALANCE_BIZ_RECHARGE, recharge.getId(), "会员充值");
        recharge.setBalanceBefore(balanceAfter.subtract(totalAmount));
        recharge.setBalanceAfter(balanceAfter);
        rechargeMapper.updateById(recharge);

        // ---- 累计充值（只落实付，原子自增，不触碰 balance） ----
        memberMapper.incrementTotalRecharge(request.getMemberId(), rechargeAmount);

        // ---- 充值赠送积分（按等级积分倍数） ----
        int bonusPoints = calcBonusPoints(member, rechargeAmount);
        if (bonusPoints > 0) {
            pointsService.addPoints(request.getMemberId(), bonusPoints,
                    (byte) CommonConstants.POINTS_BIZ_RECHARGE_GIFT, recharge.getId(), "充值赠送积分");
        }

        log.info("会员充值成功: memberId={}, 实付={}, 赠送={}, 到账={}, 赠送积分={}, 活动={}, balanceAfter={}",
                request.getMemberId(), rechargeAmount, bonusAmount, totalAmount, bonusPoints,
                bonusResult.campaignId(), balanceAfter);

        return getRechargeById(recharge.getId());
    }

    /**
     * 充值试算（预览）：按当前生效的充值活动阶梯，计算「实付 / 赠送 / 实际到账」。
     * <p>
     * <b>仅用于前端展示</b>，不做任何写入。最终入账仍由 {@link #recharge} 在服务端重新计算，
     * 前端持有的预览值不参与入账 —— 因此不存在「前端改金额/赠送」的风险。
     * <p>
     * 校验与真实充值保持一致（金额范围、会员存在、非黑名单、资源归属），
     * 避免预览显示一个真实充值会被拒绝的结果。
     */
    public RechargePreviewResponse preview(Long memberId, BigDecimal amount) {
        // P0-4 资源归属：与真实充值同一套校验
        accessGuard.assertMemberLedgerAccess(memberId);

        if (amount == null || amount.compareTo(BigDecimal.ONE) < 0
                || amount.compareTo(BigDecimal.valueOf(RechargeRequest.MAX_AMOUNT)) > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(),
                    "充值金额需在 1 ~ " + RechargeRequest.MAX_AMOUNT + " 元之间");
        }
        Member member = memberMapper.selectById(memberId);
        if (member == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "会员不存在");
        }
        if (member.getStatus() != null && member.getStatus() == 3) {
            throw new BusinessException(ErrorCode.MEMBER_BLACKLISTED);
        }

        BigDecimal rechargeAmount = amount.setScale(2, RoundingMode.HALF_UP);
        RechargeBonusCalculator.BonusResult bonus = bonusCalculator.calculate(rechargeAmount);

        RechargePreviewResponse response = new RechargePreviewResponse();
        response.setMemberId(memberId);
        response.setRechargeAmount(rechargeAmount);
        response.setBonusAmount(bonus.bonusAmount());
        response.setTotalAmount(rechargeAmount.add(bonus.bonusAmount()));
        response.setCampaignId(bonus.campaignId());
        return response;
    }

    public PageResult<RechargeRecordResponse> getRechargeRecords(Long memberId, String startTime,
                                                                 String endTime, Byte status,
                                                                 PageQuery pageQuery) {
        // P0-4 资源归属：员工须有充值操作权限；会员只能查询自己的充值流水
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

    /**
     * 按充值单 ID 查询。
     * <p>
     * <b>P0-4 授权说明</b>：本方法被 {@code recharge()} 内部调用（回读刚生成的充值单），
     * 故不能在此加归属校验。其对外暴露的 {@code GET /api/member/recharge-records/{id}}
     * 已由 {@code SecurityConfig} 限定为员工角色，会员身份无法访问。
     */
    public RechargeRecordResponse getRechargeById(Long id) {
        RechargeRecordResponse response = rechargeMapper.selectRechargeDetail(id);
        if (response == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        response.setPaymentMethodLabel(getPaymentMethodLabel(response.getPaymentMethod()));
        response.setStatusLabel(getStatusLabel(response.getStatus()));
        return response;
    }

    /* ==================== 内部方法 ==================== */

    /**
     * 由「会员ID + 客户端幂等键」生成<b>确定性</b>充值单号。
     * <p>
     * 同一请求必得同一单号，从而复用 {@code uk_recharge_no} 实现幂等，无需新增数据库字段。
     * 单号形如 {@code RCH + yyyyMMdd + 16位十六进制摘要}（长度 27 ≤ VARCHAR(32)）。
     */
    private String buildDeterministicRechargeNo(Long memberId, String idempotentKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((memberId + "|" + idempotentKey).getBytes(StandardCharsets.UTF_8));
            String hex = HexFormat.of().formatHex(hash).substring(0, 16);
            return "RCH" + LocalDate.now().format(DATE_PART) + hex;
        } catch (Exception e) {
            // 摘要算法必然可用；此处仅为编译期受检异常兜底
            throw new IllegalStateException("生成充值单号失败", e);
        }
    }

    /** 未提供幂等键时的随机单号（无幂等保护）。 */
    private String generateRandomRechargeNo() {
        return "RCH" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }

    private MemberRecharge findByRechargeNo(String rechargeNo) {
        return rechargeMapper.selectOne(new LambdaQueryWrapper<MemberRecharge>()
                .eq(MemberRecharge::getRechargeNo, rechargeNo));
    }

    /**
     * 充值赠送积分 = 实付金额 × 会员等级积分倍数（向下取整）。
     * <p>
     * 依据 {@code member_level.points_multiple}（“积分获取倍数”）字段语义。
     * 注：种子数据 member_points_log 中的“充值赠送积分”是按 1 倍记录的（与倍数不一致），
     * 本实现以字段语义为准（1×/1.5×/2×/3×）。
     */
    private int calcBonusPoints(Member member, BigDecimal rechargeAmount) {
        BigDecimal multiple = BigDecimal.ONE;
        if (member.getLevelId() != null) {
            MemberLevel level = levelMapper.selectById(member.getLevelId());
            if (level != null && level.getPointsMultiple() != null) {
                multiple = level.getPointsMultiple();
            }
        }
        return rechargeAmount.multiply(multiple).setScale(0, RoundingMode.DOWN).intValue();
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
