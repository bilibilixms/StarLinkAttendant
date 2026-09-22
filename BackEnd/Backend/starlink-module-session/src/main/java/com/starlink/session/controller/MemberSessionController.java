package com.starlink.session.controller;

import com.starlink.common.exception.BusinessException;
import com.starlink.common.result.ErrorCode;
import com.starlink.common.result.Result;
import com.starlink.session.dto.req.MemberSessionStartRequest;
import com.starlink.session.dto.req.SessionEndRequest;
import com.starlink.session.dto.req.SessionPauseRequest;
import com.starlink.session.dto.req.SessionResumeRequest;
import com.starlink.session.dto.resp.MemberCurrentSessionResponse;
import com.starlink.session.dto.resp.MemberScanStartResponse;
import com.starlink.session.dto.resp.MemberSelfEndResponse;
import com.starlink.session.service.MemberSessionService;
import com.starlink.system.security.SecurityUser;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会员端自助上机 Controller（小程序「一键上机」）。
 * <p>
 * 配套接口（均需会员登录，见 SecurityConfig 白名单）：
 * <ul>
 *   <li>{@code POST /api/member/session/scan-start} — 选座直开（沿用小程序 scan-start 命名）</li>
 *   <li>{@code GET  /api/member/session/current}    — 当前活跃会话（无会话返回 data:null）</li>
 *   <li>{@code POST /api/member/session/self-end}   — 自助下机，按费率实算并扣减余额</li>
 *   <li>{@code POST /api/member/session/pause}      — 临时下机</li>
 *   <li>{@code POST /api/member/session/resume}     — 恢复上机</li>
 * </ul>
 * 会员身份取自 JWT（{@link SecurityUser}），会话归属由 {@link MemberSessionService} 校验，
 * 会员无法操作他人会话。
 *
 */
@Slf4j
@RestController
@RequestMapping("/api/member/session")
@RequiredArgsConstructor
public class MemberSessionController {

    private final MemberSessionService memberSessionService;

    /**
     * 一键上机（选座直开）。
     */
    @PostMapping("/scan-start")
    public Result<MemberScanStartResponse> scanStart(
            @AuthenticationPrincipal SecurityUser securityUser,
            @Valid @RequestBody MemberSessionStartRequest request) {
        return Result.ok(memberSessionService.scanStart(requireMemberId(securityUser), request));
    }

    /**
     * 当前活跃会话；无进行中的会话时返回 data:null。
     */
    @GetMapping("/current")
    public Result<MemberCurrentSessionResponse> current(
            @AuthenticationPrincipal SecurityUser securityUser) {
        return Result.ok(memberSessionService.getCurrent(requireMemberId(securityUser)));
    }

    /**
     * 自助下机结算：实算费用、扣减余额、释放机位。
     */
    @PostMapping("/self-end")
    public Result<MemberSelfEndResponse> selfEnd(
            @AuthenticationPrincipal SecurityUser securityUser,
            @Valid @RequestBody SessionEndRequest request) {
        // 复用 SessionEndRequest：只使用 sessionId，paidAmount 由服务端实算，忽略客户端传值
        return Result.ok(memberSessionService.selfEnd(
                requireMemberId(securityUser), request.getSessionId()));
    }

    /**
     * 临时下机（暂停计时，机位保留）。
     */
    @PostMapping("/pause")
    public Result<Void> pause(
            @AuthenticationPrincipal SecurityUser securityUser,
            @Valid @RequestBody SessionPauseRequest request) {
        memberSessionService.pause(requireMemberId(securityUser), request.getSessionId());
        return Result.ok();
    }

    /**
     * 恢复上机。
     */
    @PostMapping("/resume")
    public Result<Void> resume(
            @AuthenticationPrincipal SecurityUser securityUser,
            @Valid @RequestBody SessionResumeRequest request) {
        memberSessionService.resume(requireMemberId(securityUser), request.getSessionId());
        return Result.ok();
    }

    /**
     * 仅会员身份可调用会员端自助接口；员工令牌即使登录也拒绝。
     */
    private Long requireMemberId(SecurityUser securityUser) {
        if (securityUser == null || securityUser.getMemberId() == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return securityUser.getMemberId();
    }
}
