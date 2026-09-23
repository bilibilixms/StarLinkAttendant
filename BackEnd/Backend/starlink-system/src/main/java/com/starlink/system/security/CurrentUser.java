package com.starlink.system.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {

    public SecurityUser get() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof SecurityUser) {
            return (SecurityUser) principal;
        }
        return null;
    }

    public Long getUserId() {
        SecurityUser user = get();
        return user != null ? user.getUserId() : null;
    }

    public String getUsername() {
        SecurityUser user = get();
        return user != null ? user.getUsername() : null;
    }

    public String getRealName() {
        SecurityUser user = get();
        return user != null ? user.getEmployee().getRealName() : null;
    }

    public boolean hasPermission(String permission) {
        SecurityUser user = get();
        return user != null && user.getPermissions().contains(permission);
    }
}
