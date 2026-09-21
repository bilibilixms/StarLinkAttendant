package com.starlink.system.controller;

import com.starlink.common.result.Result;
import com.starlink.system.dto.req.LoginRequest;
import com.starlink.system.dto.req.RefreshTokenRequest;
import com.starlink.system.dto.resp.LoginResponse;
import com.starlink.system.dto.resp.UserInfoResponse;
import com.starlink.system.security.SecurityUser;
import com.starlink.system.service.AuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            String token = bearerToken.substring(7);
            authService.logout(token);
        }
        return Result.ok();
    }

    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return Result.ok(authService.refreshToken(request));
    }

    @GetMapping("/info")
    public Result<UserInfoResponse> getUserInfo(@AuthenticationPrincipal SecurityUser securityUser) {
        return Result.ok(authService.getUserInfo(securityUser));
    }
}
