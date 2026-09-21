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

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenUtil jwtTokenUtil;
    private final EmployeeMapper employeeMapper;
    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final RedisService redisService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);
        if (token != null) {
            try {
                Long userId = jwtTokenUtil.getUserIdFromToken(token);
                String username = jwtTokenUtil.getUsernameFromToken(token);

                // 黑名单校验：token 已被主动登出则视为未认证
                String blacklistKey = "jwt:blacklist:" + SecureUtil.sha256(token);
                if (redisService.exists(blacklistKey)) {
                    log.debug("Token 已在登出黑名单，拒绝认证");
                    filterChain.doFilter(request, response);
                    return;
                }

                if (userId < 0) {
                    // 会员身份：token 里 userId = -memberId，username = "m:phone"
                    Long memberId = -userId;
                    String phone = username != null && username.startsWith("m:")
                            ? username.substring(2)
                            : (username != null ? username : "");
                    // 不查数据库（避免跨模块依赖 member mapper），状态默认正常
                    SecurityUser securityUser = new SecurityUser(memberId, phone, (byte) 1);
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            securityUser, null, securityUser.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                } else {
                    // 员工身份：从数据库加载角色与权限
                    Employee employee = employeeMapper.selectById(userId);

                    if (employee != null && employee.getIsActive() == 1) {
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
