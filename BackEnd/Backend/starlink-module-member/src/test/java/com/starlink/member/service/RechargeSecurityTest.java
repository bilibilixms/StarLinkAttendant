package com.starlink.member.service;

import com.starlink.member.dto.req.RechargeRequest;
import com.starlink.member.mapper.MemberLevelMapper;
import com.starlink.member.mapper.MemberMapper;
import com.starlink.member.mapper.MemberRechargeMapper;
import com.starlink.member.security.MemberAccessGuard;
import com.starlink.system.security.SecurityUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class RechargeSecurityTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void memberCannotClaimCashPaymentForSelfOrSomeoneElse() {
        SecurityUser member = new SecurityUser(7L, "13800000000", (byte) 1);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(member, null, member.getAuthorities()));

        MemberMapper members = mock(MemberMapper.class);
        MemberRechargeMapper recharges = mock(MemberRechargeMapper.class);
        RechargeService service = new RechargeService(recharges, members, mock(MemberLevelMapper.class),
                mock(GrowthValueService.class), mock(PointsService.class), mock(BalanceService.class),
                mock(RechargeBonusCalculator.class), new MemberAccessGuard());

        for (long target : new long[]{7L, 8L}) {
            RechargeRequest request = new RechargeRequest();
            request.setMemberId(target);
            request.setAmount(new BigDecimal("100"));
            request.setPaymentMethod((byte) 1);
            assertThrows(AccessDeniedException.class, () -> service.recharge(request));
        }
        verifyNoInteractions(members, recharges);
    }

    @Test
    void memberCanOnlyPreviewOwnRecharge() {
        SecurityUser member = new SecurityUser(7L, "13800000000", (byte) 1);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(member, null, member.getAuthorities()));
        MemberAccessGuard guard = new MemberAccessGuard();
        guard.assertMemberLedgerAccess(7L);
        assertThrows(AccessDeniedException.class, () -> guard.assertMemberLedgerAccess(8L));
    }
}
