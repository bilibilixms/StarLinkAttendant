package com.starlink.system.security;

import com.starlink.common.exception.BusinessException;
import com.starlink.common.util.RedisService;
import com.starlink.system.config.JwtProperties;
import com.starlink.system.dto.req.RefreshTokenRequest;
import com.starlink.system.entity.Employee;
import com.starlink.system.mapper.EmployeeMapper;
import com.starlink.system.mapper.MemberProfileMapper;
import com.starlink.system.mapper.PermissionMapper;
import com.starlink.system.mapper.RoleMapper;
import com.starlink.system.service.AuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JwtLifecycleTest {
    private JwtTokenUtil tokens;
    private EmployeeMapper employees;
    private MemberProfileMapper members;
    private RedisService redis;

    @BeforeEach
    void setup() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("test-secret-0123456789-0123456789-0123456789");
        tokens = new JwtTokenUtil(properties);
        employees = mock(EmployeeMapper.class);
        members = mock(MemberProfileMapper.class);
        redis = mock(RedisService.class);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void accessAndRefreshTokensHaveDistinctPurposesAndIds() {
        String access = tokens.generateAccessToken(1L, "13800000001");
        String refresh = tokens.generateRefreshToken(1L, "13800000001");

        assertNotEquals(access, refresh);
        assertNotEquals(tokens.generateAccessToken(1L, "13800000001"), access);
        assertThrows(IllegalArgumentException.class, () -> tokens.parseRefreshToken(access));
        assertThrows(IllegalArgumentException.class, () -> tokens.parseAccessToken(refresh));
    }

    @Test
    void refreshTokenCanOnlyBeUsedOnce() {
        Employee employee = new Employee();
        employee.setId(1L);
        employee.setPhone("13800000001");
        employee.setStatus((byte) 1);
        employee.setIsActive((byte) 1);
        when(employees.selectById(1L)).thenReturn(employee);
        when(redis.setIfAbsent(anyString(), eq("1"), any())).thenReturn(true, false);
        AuthService auth = new AuthService(employees, mock(RoleMapper.class), mock(PermissionMapper.class),
                tokens, mock(PasswordEncoder.class), members, redis);
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken(tokens.generateRefreshToken(1L, employee.getPhone()));

        assertNotNull(auth.refreshToken(request).getAccessToken());
        assertThrows(BusinessException.class, () -> auth.refreshToken(request));
    }

    @Test
    void frozenMemberIsNotAuthenticatedByOldToken() throws Exception {
        when(members.selectMemberProfile(7L)).thenReturn(Map.of("status", 2, "phone", "13800000007"));
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(tokens, employees,
                mock(RoleMapper.class), mock(PermissionMapper.class), members, redis);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/member/session/current");
        request.addHeader("Authorization", "Bearer " + tokens.generateAccessToken(-7L, "m:13800000007"));

        filter.doFilter(request, new MockHttpServletResponse(), (req, resp) -> {});

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
