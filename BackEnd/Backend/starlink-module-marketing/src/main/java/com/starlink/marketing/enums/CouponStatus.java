package com.starlink.marketing.enums;

import lombok.Getter;

/**
 * 优惠券状态枚举。
 *
 */
@Getter
public enum CouponStatus {

    UNUSED(0, "未使用"),
    USED(1, "已使用"),
    EXPIRED(2, "已过期"),
    VOIDED(3, "已作废");

    private final int code;
    private final String name;

    CouponStatus(int code, String name) {
        this.code = code;
        this.name = name;
    }

    public static String getNameByCode(Integer code) {
        if (code == null) return "";
        for (CouponStatus status : values()) {
            if (status.code == code) return status.name;
        }
        return "未知";
    }
}
