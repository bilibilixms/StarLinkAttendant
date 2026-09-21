package com.starlink.system.service;

import com.starlink.common.exception.BusinessException;
import com.starlink.common.result.ErrorCode;
import com.starlink.system.dto.req.LoginRequest;
import com.starlink.system.dto.req.RefreshTokenRequest;
import com.starlink.system.dto.resp.LoginResponse;
import com.starlink.system.dto.resp.UserInfoResponse;
import com.starlink.system.entity.Employee;
import com.starlink.system.mapper.EmployeeMapper;
import com.starlink.system.mapper.PermissionMapper;
import com.starlink.system.mapper.RoleMapper;
import com.starlink.system.security.JwtTokenUtil;
import com.starlink.system.security.SecurityUser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import com.starlink.common.util.RedisService;
import cn.hutool.crypto.SecureUtil;
import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final EmployeeMapper employeeMapper;
    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final JwtTokenUtil jwtTokenUtil;
    private final PasswordEncoder passwordEncoder;
    private final RedisService redisService;

    public LoginResponse login(LoginRequest request) {
        Employee employee = employeeMapper.selectByPhone(request.getUsername());
        
        if (employee == null) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        if (!passwordEncoder.matches(request.getPassword(), employee.getPasswordHash())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        if (employee.getStatus() != 1) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }

        if (employee.getIsActive() != 1) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }

        employee.setLastLoginAt(LocalDateTime.now());
        employeeMapper.updateById(employee);

        String accessToken = jwtTokenUtil.generateAccessToken(employee.getId(), employee.getPhone());
        String refreshToken = jwtTokenUtil.generateRefreshToken(employee.getId(), employee.getPhone());

        LoginResponse response = new LoginResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setExpiresIn(7200L);

        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo();
        userInfo.setId(employee.getId());
        userInfo.setEmployeeNo(employee.getEmployeeNo());
        userInfo.setRealName(employee.getRealName());
        userInfo.setPhone(employee.getPhone());
        userInfo.setPosition(employee.getPosition());
        response.setUserInfo(userInfo);

        log.info("用户登录成功: {}", employee.getRealName());
        return response;
    }

    public void logout(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        long remaining = jwtTokenUtil.getRemainingSeconds(token);
        if (remaining <= 0) {
            // token 已过期，没必要写黑名单
            log.info("用户退出登录（token 已过期）");
            return;
        }
        String key = "jwt:blacklist:" + SecureUtil.sha256(token);
        redisService.set(key, "1", Duration.ofSeconds(remaining));
        log.info("用户退出登录，token 已加入黑名单，剩余 {} 秒", remaining);
    }

    public LoginResponse refreshToken(RefreshTokenRequest request) {
        Long userId;
        try {
            userId = jwtTokenUtil.getUserIdFromToken(request.getRefreshToken());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }

        Employee employee = employeeMapper.selectById(userId);
        if (employee == null) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }

        String accessToken = jwtTokenUtil.generateAccessToken(employee.getId(), employee.getPhone());
        String refreshToken = jwtTokenUtil.generateRefreshToken(employee.getId(), employee.getPhone());

        LoginResponse response = new LoginResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setExpiresIn(7200L);

        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo();
        userInfo.setId(employee.getId());
        userInfo.setEmployeeNo(employee.getEmployeeNo());
        userInfo.setRealName(employee.getRealName());
        userInfo.setPhone(employee.getPhone());
        userInfo.setPosition(employee.getPosition());
        response.setUserInfo(userInfo);

        return response;
    }

    public UserInfoResponse getUserInfo(SecurityUser securityUser) {
        UserInfoResponse response = new UserInfoResponse();
        response.setId(securityUser.getUserId());
        response.setEmployeeNo(securityUser.getEmployee().getEmployeeNo());
        response.setRealName(securityUser.getEmployee().getRealName());
        response.setPhone(securityUser.getUsername());
        response.setPosition(securityUser.getEmployee().getPosition());
        response.setRoles(securityUser.getRoles());
        response.setPermissions(securityUser.getPermissions());
        return response;
    }
}
