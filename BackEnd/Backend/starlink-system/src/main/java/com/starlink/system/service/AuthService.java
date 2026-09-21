package com.starlink.system.service;

import com.starlink.common.exception.BusinessException;
import com.starlink.common.result.ErrorCode;
import com.starlink.system.dto.req.LoginRequest;
import com.starlink.system.dto.req.RefreshTokenRequest;
import com.starlink.system.dto.resp.LoginResponse;
import com.starlink.system.dto.resp.UserInfoResponse;
import com.starlink.system.entity.Employee;
import com.starlink.system.mapper.EmployeeMapper;
import com.starlink.system.mapper.MemberProfileMapper;
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
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final EmployeeMapper employeeMapper;
    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final JwtTokenUtil jwtTokenUtil;
    private final PasswordEncoder passwordEncoder;
    /** 会员实时信息只读查询（供会员端「我的」页刷新余额） */
    private final MemberProfileMapper memberProfileMapper;

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

    public void logout() {
        log.info("用户退出登录");
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
        if (securityUser == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        // 会员端身份：返回会员实时信息（含最新余额）
        if (securityUser.isMember()) {
            return buildMemberInfo(securityUser);
        }

        UserInfoResponse response = new UserInfoResponse();
        response.setId(securityUser.getUserId());
        if (securityUser.getEmployee() != null) {
            response.setEmployeeNo(securityUser.getEmployee().getEmployeeNo());
            response.setRealName(securityUser.getEmployee().getRealName());
            response.setPosition(securityUser.getEmployee().getPosition());
        }
        response.setPhone(securityUser.getUsername());
        response.setRoles(securityUser.getRoles());
        response.setPermissions(securityUser.getPermissions());
        return response;
    }

    /**
     * 组装会员端「当前登录者」信息。
     * <p>
     * 会员端「我的」页据此刷新余额：后台充值后无需退出登录即可看到最新余额。
     * 只读查询，不修改任何余额（余额变动仅经会员模块 BalanceService）。
     */
    private UserInfoResponse buildMemberInfo(SecurityUser securityUser) {
        Long memberId = securityUser.getMemberId();
        Map<String, Object> row = memberProfileMapper.selectMemberProfile(memberId);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "会员不存在或已注销");
        }

        UserInfoResponse.MemberInfo info = new UserInfoResponse.MemberInfo();
        info.setId(toLong(row.get("id")));
        info.setMemberNo(toStringValue(row.get("memberNo")));
        info.setRealName(toStringValue(row.get("realName")));
        info.setPhone(toStringValue(row.get("phone")));
        info.setLevelId(toLong(row.get("levelId")));
        info.setLevelName(toStringValue(row.get("levelName")));
        info.setBalance(toDecimal(row.get("balance")));
        info.setAvailablePoints(toLong(row.get("availablePoints")));
        info.setTotalPoints(toLong(row.get("totalPoints")));
        info.setTotalRecharge(toDecimal(row.get("totalRecharge")));
        info.setTotalConsumption(toDecimal(row.get("totalConsumption")));
        info.setStatus(row.get("status") == null ? null : ((Number) row.get("status")).byteValue());

        UserInfoResponse response = new UserInfoResponse();
        response.setId(memberId);
        response.setPhone(info.getPhone());
        response.setRealName(info.getRealName());
        response.setRoles(securityUser.getRoles());
        response.setPermissions(securityUser.getPermissions());
        response.setMember(info);
        return response;
    }

    private Long toLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private String toStringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private java.math.BigDecimal toDecimal(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof java.math.BigDecimal d ? d : new java.math.BigDecimal(String.valueOf(value));
    }
}
