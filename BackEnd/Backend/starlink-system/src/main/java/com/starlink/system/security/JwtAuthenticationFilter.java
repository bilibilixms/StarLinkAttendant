package com.starlink.system.security;

import cn.hutool.crypto.SecureUtil;
import com.starlink.common.util.RedisService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.starlink.system.entity.Employee;
import com.starlink.system.mapper.EmployeeMapper;
import com.starlink.system.mapper.PermissionMapper;
import com.starlink.system.mapper.RoleMapper;
import com.starlink.system.mapper.MemberProfileMapper;
import io.jsonwebtoken.Claims;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenUtil jwtTokenUtil;
    private final EmployeeMapper employeeMapper;
    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final MemberProfileMapper memberProfileMapper;
    private final RedisService redisService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);
        if (token != null) {
            try {
                Claims claims = jwtTokenUtil.parseAccessToken(token);
                Long userId = claims.get("userId", Long.class);
                String username = claims.get("username", String.class);
                if (userId == null || userId == 0 || username == null) {
                    throw new IllegalArgumentException("令牌身份无效");
                }

                // 黑名单校验：token 已被主动登出则视为未认证
                String blacklistKey = "jwt:blacklist:" + SecureUtil.sha256(token);
                if (redisService.exists(blacklistKey)) {
                    log.debug("Token 已在登出黑名单，拒绝认证");
                    filterChain.doFilter(request, response);
                    return;
                }

                if (userId < 0) {
                    // 会员身份：token 里 userId = -memberId，username = "m:phone"
                    if (userId != Long.MIN_VALUE && username.startsWith("m:")) {
                        Long memberId = -userId;
                        Map<String, Object> profile = memberProfileMapper.selectMemberProfile(memberId);
                        if (profile != null && profile.get("status") instanceof Number status
                                && status.byteValue() == 1
                                && username.substring(2).equals(profile.get("phone"))) {
                            SecurityUser securityUser = new SecurityUser(memberId, username.substring(2), (byte) 1);
                            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                    securityUser, null, securityUser.getAuthorities());
                            SecurityContextHolder.getContext().setAuthentication(authToken);
                        }
                    }
                } else {
                    // 员工身份：从数据库加载角色与权限
                    Employee employee = employeeMapper.selectById(userId);

                    if (employee != null && employee.getIsActive() == 1 && employee.getStatus() == 1
                            && username.equals(employee.getPhone())) {
                        var roles = roleMapper.selectByEmployeeId(userId);

                        List<String> roleNames = roles.stream()
                                .map(r -> "ROLE_" + r.getRoleCode())
                                .collect(Collectors.toList());

                        List<Long> roleIds = roles.stream()
                                .map(r -> r.getId())
                                .collect(Collectors.toList());

                        List<String> permissions = List.of();
                        if (!roleIds.isEmpty()) {
                            permissions = permissionMapper.selectByRoleIds(roleIds).stream()
                                    .map(p -> p.getPermCode())
                                    .collect(Collectors.toList());
                        }

                        SecurityUser securityUser = new SecurityUser(employee, permissions, roleNames);
                        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                securityUser, null, securityUser.getAuthorities());

                        SecurityContextHolder.getContext().setAuthentication(authToken);
                    }
                }
            } catch (Exception e) {
                log.warn("JWT认证失败: {}", e.getMessage());
            }
        }
        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
