package com.starlink.session.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.starlink.common.exception.BusinessException;
import com.starlink.common.result.ErrorCode;
import com.starlink.session.dto.req.MemberSessionStartRequest;
import com.starlink.session.dto.req.SessionEndRequest;
import com.starlink.session.dto.req.SessionPauseRequest;
import com.starlink.session.dto.req.SessionResumeRequest;
import com.starlink.session.dto.resp.MemberCurrentSessionResponse;
import com.starlink.session.dto.resp.MemberScanStartResponse;
import com.starlink.session.dto.resp.MemberSelfEndResponse;
import com.starlink.session.dto.resp.SessionResponse;
import com.starlink.session.entity.Computer;
import com.starlink.session.entity.SeatArea;
import com.starlink.session.entity.Session;
import com.starlink.session.mapper.ComputerMapper;
import com.starlink.session.mapper.SeatAreaMapper;
import com.starlink.session.mapper.SessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;

/**
 * 会员端自助上机服务（小程序）。
 * <p>
 * 与管理端 {@link SessionService} 的区别：
 * <ul>
 *   <li>会员身份一律取自 SecurityContext（Controller 传入），绝不信任请求体中的 memberId；</li>
 *   <li>所有操作先做会话归属校验（只能操作自己的会话）；</li>
 *   <li>响应按小程序字段裁剪（机位名/区域/配置/余额/折算时租）。</li>
 * </ul>
 * 扣费仍由 {@link SessionService#endSession(SessionEndRequest)} 完成（同管理端一致，
 * 直接更新 member 表余额与累计消费），保证 Web 管理端与小程序账目同源。
 *
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberSessionService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final BigDecimal SIXTY = BigDecimal.valueOf(60);

    /** 单店演示阶段的默认门店 ID（后端暂无门店域，与小程序 DEFAULT_STORE 对齐） */
    private static final Long DEFAULT_STORE_ID = 1L;

    private final SessionService sessionService;
    private final SessionMapper sessionMapper;
    private final ComputerMapper computerMapper;
    private final SeatAreaMapper seatAreaMapper;
    private final TariffCalculator tariffCalculator;

    /**
     * 一键上机（选座直开）。
     */
    @Transactional
    public MemberScanStartResponse scanStart(Long memberId, MemberSessionStartRequest request) {
        Long computerId = resolveComputerId(request);
        if (computerId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "机位信息无效，请重新选择机位");
        }

        Computer computer = computerMapper.selectById(computerId);
        if (computer == null || (computer.getIsActive() != null && computer.getIsActive() == 0)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "机位不存在或已停用");
        }

        // 余额校验：与小程序选座页一致，余额为 0/账户异常不允许开机。
        // 上机后余额用尽的兜底由 SessionBillingScheduler 强制下机处理。
        BigDecimal balance = sessionMapper.selectMemberBalance(memberId);
        if (balance == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "会员账户不存在");
        }
        if (balance.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.BALANCE_INSUFFICIENT.getCode(), "账户余额不足，请先充值");
        }

        com.starlink.session.dto.req.SessionStartRequest start =
                new com.starlink.session.dto.req.SessionStartRequest();
        start.setMemberId(memberId);
        start.setComputerId(computerId);
        // 小程序选座直开属于扫码自助认证
        start.setAuthMethod((byte) 2);
        // 费率方案不取请求参数：以机位绑定方案为准，避免前端传错区域导致错价
        sessionService.startSession(start);

        log.info("会员自助上机: memberId={}, computerId={}", memberId, computerId);
        return new MemberScanStartResponse(buildCurrent(memberId), false);
    }

    /**
     * 当前活跃会话；无会话返回 null（接口返回 data:null）。
     */
    public MemberCurrentSessionResponse getCurrent(Long memberId) {
        return buildCurrent(memberId);
    }

    /**
     * 自助下机：结算并扣减余额。
     */
    @Transactional
    public MemberSelfEndResponse selfEnd(Long memberId, Long sessionId) {
        assertOwnedSession(memberId, sessionId);

        SessionEndRequest request = new SessionEndRequest();
        request.setSessionId(sessionId);
        // paidAmount 不传：由 SessionService 按费率方案实算，禁止客户端指定扣款额
        SessionResponse ended = sessionService.endSession(request);

        BigDecimal balanceAfter = sessionMapper.selectMemberBalance(memberId);

        MemberSelfEndResponse resp = new MemberSelfEndResponse();
        resp.setSessionId(ended.getId());
        resp.setSessionNo(ended.getSessionNo());
        resp.setDurationMinutes(ended.getBilledMinutes());
        resp.setTotalAmount(nz(ended.getTotalAmount()));
        resp.setDiscountAmount(nz(ended.getDiscountAmount()));
        resp.setPaidAmount(nz(ended.getPaidAmount()));
        resp.setBalanceAfter(balanceAfter != null ? balanceAfter : BigDecimal.ZERO);
        resp.setEndTime(ended.getEndTime());

        log.info("会员自助下机: memberId={}, sessionId={}, paidAmount={}, balanceAfter={}",
                memberId, sessionId, resp.getPaidAmount(), resp.getBalanceAfter());
        return resp;
    }

    /**
     * 临时下机（暂停计时，机位保留）。
     */
    @Transactional
    public void pause(Long memberId, Long sessionId) {
        assertOwnedSession(memberId, sessionId);
        SessionPauseRequest request = new SessionPauseRequest();
        request.setSessionId(sessionId);
        sessionService.pauseSession(request);
    }

    /**
     * 恢复上机。
     */
    @Transactional
    public void resume(Long memberId, Long sessionId) {
        assertOwnedSession(memberId, sessionId);
        SessionResumeRequest request = new SessionResumeRequest();
        request.setSessionId(sessionId);
        sessionService.resumeSession(request);
    }

    // ==================== 私有方法 ====================

    /**
     * 组装小程序当前会话响应；无活跃会话时返回 null。
     */
    private MemberCurrentSessionResponse buildCurrent(Long memberId) {
        SessionResponse s = sessionService.getActiveSessionByMember(memberId);
        if (s == null) {
            return null;
        }

        Computer computer = s.getComputerId() != null ? computerMapper.selectById(s.getComputerId()) : null;
        String areaName = null;
        if (computer != null && computer.getAreaId() != null) {
            SeatArea area = seatAreaMapper.selectById(computer.getAreaId());
            if (area != null) {
                areaName = area.getAreaName();
            }
        }

        BigDecimal balance = sessionMapper.selectMemberBalance(memberId);
        balance = balance != null ? balance : BigDecimal.ZERO;

        BigDecimal totalAmount = nz(s.getTotalAmount());
        BigDecimal discountAmount = nz(s.getDiscountAmount());
        int billedMinutes = s.getBilledMinutes() != null ? s.getBilledMinutes() : 0;
        // 活跃会话：当前应付 = 实时总费用 - 优惠（后端目前无会员等级折扣，优惠恒为 0）
        BigDecimal paidAmount = totalAmount.subtract(discountAmount);
        BigDecimal hourlyRate = tariffCalculator.renewalHourlyRate(s.getTariffPlanId());
        BigDecimal firstHourPrice = tariffCalculator.firstPeriodPrice(s.getTariffPlanId());

        // 此刻下机的扣费后预计余额：让会员在「上机中」就能看到本次消费对余额的影响，
        // 与下机结算接口返回的 balanceAfter 同口径
        BigDecimal balanceAfter = balance.subtract(paidAmount).max(BigDecimal.ZERO);

        MemberCurrentSessionResponse r = new MemberCurrentSessionResponse();
        r.setSessionId(s.getId());
        r.setSessionNo(s.getSessionNo());
        r.setStoreId(DEFAULT_STORE_ID);
        r.setComputerId(s.getComputerId());
        r.setComputerName(firstText(s.getComputerName(),
                computer != null ? computer.getComputerName() : null, s.getComputerNo()));
        r.setSeatNo(s.getComputerNo());
        r.setAreaName(areaName);
        r.setComputerSpec(buildSpec(computer));
        r.setStartTime(s.getStartTime());
        r.setEndTime(s.getEndTime());
        r.setDurationMinutes(billedMinutes);
        r.setBilledMinutes(billedMinutes);
        r.setFreeMinutes(s.getFreeMinutes() != null ? s.getFreeMinutes() : 0);
        r.setTotalAmount(totalAmount);
        r.setDiscountAmount(discountAmount);
        r.setPaidAmount(paidAmount);
        r.setHourlyRate(hourlyRate);
        r.setFirstHourPrice(firstHourPrice);
        r.setBalance(balance);
        r.setBalanceAfter(balanceAfter);
        // 剩余时长按「扣费后余额 + 续费小时价」估算：首小时费用已从余额中预留
        r.setRemainingMinutes(calcRemainingMinutes(balanceAfter, hourlyRate));
        r.setStatus(s.getStatus());
        return r;
    }

    /**
     * 校验会话存在且归属于当前会员；员工不应通过会员端接口操作，同样拒绝。
     */
    private Session assertOwnedSession(Long memberId, Long sessionId) {
        if (sessionId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "会话 ID 不能为空");
        }
        Session session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
        if (!memberId.equals(session.getMemberId())) {
            log.warn("会员操作他人会话被拒绝: memberId={}, sessionId={}, ownerId={}",
                    memberId, sessionId, session.getMemberId());
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return session;
    }

    /**
     * 解析机位 ID：优先取请求体字段，缺失时从 qrContent（JSON 或 "a-b-computerId"）兜底解析。
     */
    private Long resolveComputerId(MemberSessionStartRequest request) {
        if (request.getComputerId() != null) {
            return request.getComputerId();
        }
        String qr = request.getQrContent();
        if (!StringUtils.hasText(qr)) {
            return null;
        }
        try {
            long parsed = JSON.readTree(qr).path("computerId").asLong(0);
            if (parsed > 0) {
                return parsed;
            }
        } catch (Exception ignored) {
            // 非 JSON，按简单串处理
        }
        String[] parts = qr.trim().split("[-_:]");
        try {
            long parsed = Long.parseLong(parts[parts.length - 1]);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 首小时价/续费小时价统一由 {@link TariffCalculator} 计算，与下机结算同口径。
     */

    /**
     * 按续费小时价估算余额可支撑分钟数；费率为 0 时返回 -1（不限时）。
     */
    private Integer calcRemainingMinutes(BigDecimal balance, BigDecimal hourlyRate) {
        if (hourlyRate == null || hourlyRate.compareTo(BigDecimal.ZERO) <= 0) {
            return -1;
        }
        BigDecimal perMinute = hourlyRate.divide(SIXTY, 6, RoundingMode.HALF_UP);
        return balance.divide(perMinute, 0, RoundingMode.FLOOR).intValue();
    }

    /**
     * 拼装机位配置描述。
     */
    private String buildSpec(Computer computer) {
        if (computer == null) {
            return null;
        }
        // Arrays.asList 允许 null 元素（CPU/GPU 等配置字段可能为空，List.of 会抛 NPE）
        return Arrays.asList(computer.getCpu(), computer.getGpu(), computer.getMemory(),
                        computer.getScreenSize() == null ? null : computer.getScreenSize() + "显示器")
                .stream()
                .filter(StringUtils::hasText)
                .reduce((a, b) -> a + " / " + b)
                .orElse(null);
    }

    private String firstText(String... candidates) {
        for (String c : candidates) {
            if (StringUtils.hasText(c)) {
                return c;
            }
        }
        return null;
    }

    private BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
