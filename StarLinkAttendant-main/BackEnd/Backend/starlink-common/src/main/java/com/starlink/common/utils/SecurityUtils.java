package com.starlink.common.utils;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;

/**
 * 安全工具类 - 获取当前登录用户信息
 */
public class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * 获取当前登录用户 ID
     *
     * @return 当前用户 ID，未登录时返回 null
     */
    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal == null || "anonymousUser".equals(principal)) {
            return null;
        }

        // 通过反射调用 getUserId() 方法，避免 starlink-common 对 starlink-system 的循环依赖
        try {
            Method method = principal.getClass().getMethod("getUserId");
            Object userId = method.invoke(principal);
            if (userId instanceof Long) {
                return (Long) userId;
            }
            if (userId instanceof Number) {
                return ((Number) userId).longValue();
            }
        } catch (Exception ignored) {
        }

        return null;
    }
}
