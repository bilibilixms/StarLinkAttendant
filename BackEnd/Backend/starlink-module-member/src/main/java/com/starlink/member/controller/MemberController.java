package com.starlink.member.controller;

import com.starlink.common.result.Result;
import com.starlink.common.util.PageQuery;
import com.starlink.common.util.PageResult;
import com.starlink.common.constant.CommonConstants;
import com.starlink.member.dto.req.BalanceConsumeRequest;
import com.starlink.member.dto.req.BlacklistRequest;
import com.starlink.member.dto.req.MemberLoginRequest;
import com.starlink.member.dto.req.MemberQueryRequest;
import com.starlink.member.dto.req.MemberRegisterRequest;
import com.starlink.member.dto.req.MemberUpdateRequest;
import com.starlink.member.dto.resp.BlacklistResponse;
import com.starlink.member.dto.resp.MemberLoginResponse;
import com.starlink.member.dto.resp.MemberResponse;
import com.starlink.member.service.BalanceService;
import com.starlink.member.service.MemberService;
import com.starlink.system.security.SecurityUser;

import java.math.BigDecimal;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/member")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final BalanceService balanceService;

    @GetMapping("/list")
    public Result<PageResult<MemberResponse>> listMembers(
            PageQuery pageQuery,
            @RequestParam(required = false) String memberNo,
            @RequestParam(required = false) String realName,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) Byte status,
            @RequestParam(required = false) Long levelId
    ) {
        MemberQueryRequest query = new MemberQueryRequest();
        query.setMemberNo(memberNo);
        query.setRealName(realName);
        query.setPhone(phone);
        query.setStatus(status);
        query.setLevelId(levelId);
        return Result.ok(memberService.listMembers(pageQuery, query));
    }

    @GetMapping("/{id}")
    public Result<MemberResponse> getMemberById(@PathVariable Long id) {
        return Result.ok(memberService.getMemberById(id));
    }

    @PostMapping("/register")
    public Result<MemberResponse> registerMember(@Valid @RequestBody MemberRegisterRequest request) {
        return Result.ok(memberService.registerMember(request));
    }

    /** 会员端登录（小程序），匿名访问 */
    @PostMapping("/login")
    public Result<MemberLoginResponse> loginMember(@Valid @RequestBody MemberLoginRequest request) {
        return Result.ok(memberService.loginMember(request));
    }

    @PutMapping("/{id}")
    public Result<MemberResponse> updateMember(
            @PathVariable Long id,
            @Valid @RequestBody MemberUpdateRequest request
    ) {
        return Result.ok(memberService.updateMember(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteMember(@PathVariable Long id) {
        memberService.deleteMember(id);
        return Result.ok();
    }

    /**
     * 会员余额消费（小程序自助点餐等场景）：从当前登录会员余额中扣减指定金额。
     * 调用 BalanceService 保证余额变动与流水一致。
     */
    @PostMapping("/balance/consume")
    public Result<BigDecimal> consumeBalance(
            @AuthenticationPrincipal SecurityUser securityUser,
            @Valid @RequestBody BalanceConsumeRequest request
    ) {
        Long memberId = securityUser.getMemberId();
        if (memberId == null) {
            return Result.fail(403, "仅会员可调用此接口");
        }
        BigDecimal balanceAfter = balanceService.deductBalance(
                memberId,
                request.getAmount(),
                (byte) CommonConstants.BALANCE_BIZ_CONSUME,
                request.getBizId(),
                request.getRemark() != null ? request.getRemark() : "小程序自助消费"
        );
        return Result.ok(balanceAfter);
    }

    @GetMapping("/blacklist")
    public Result<List<BlacklistResponse>> getBlacklist() {
        return Result.ok(memberService.getBlacklist());
    }

    @PostMapping("/blacklist")
    public Result<Void> addToBlacklist(
            @RequestParam Long id,
            @RequestBody(required = false) BlacklistRequest request
    ) {
        if (request == null) {
            request = new BlacklistRequest();
        }
        memberService.addToBlacklist(id, request);
        return Result.ok();
    }

    @DeleteMapping("/blacklist/{id}")
    public Result<Void> removeFromBlacklist(@PathVariable Long id) {
        memberService.removeFromBlacklist(id);
        return Result.ok();
    }
}
