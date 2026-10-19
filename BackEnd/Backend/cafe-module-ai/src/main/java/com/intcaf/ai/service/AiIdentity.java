package com.intcaf.ai.service;

import com.starlink.system.security.SecurityUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Identity resolved from the verified JWT, never from a chat request. */
public record AiIdentity(String userType, Long userId, String userName, boolean canQueryData) {

    public static AiIdentity current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof SecurityUser user)) {
            throw new AccessDeniedException("请先登录");
        }
        if (user.isMember()) {
            if (!user.isEnabled()) {
                throw new AccessDeniedException("会员账号不可用");
            }
            return new AiIdentity("user", user.getMemberId(), user.getUsername(), false);
        }
        boolean staff = auth.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_super_admin") ||
                a.getAuthority().equals("ROLE_store_manager") ||
                a.getAuthority().equals("ROLE_cashier") ||
                a.getAuthority().equals("ROLE_net_admin"));
        if (!staff || !user.isEnabled() || !user.isAccountNonLocked()) {
            throw new AccessDeniedException("账号不可用");
        }
        boolean canQueryData = auth.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_super_admin") ||
                a.getAuthority().equals("ROLE_store_manager"));
        return new AiIdentity("admin", user.getUserId(), user.getUsername(), canQueryData);
    }
}
