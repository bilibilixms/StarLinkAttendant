package com.starlink.marketing.enums;

import lombok.Getter;

/**
 * 优惠券类型枚举。
 *
 */
@Getter
public enum CouponType {

    THRESHOLD(1, "满减券"),
    PERCENTAGE(2, "折扣券"),
    CASH(3, "现金券"),
    TIME_BASED(4, "时段券");

    private final int code;
    private final String name;

    CouponType(int code, String name) {
        this.code = code;
        this.name = name;
    }

    public static String getNameByCode(Integer code) {
        if (code == null) return "";
        for (CouponType type : values()) {
            if (type.code == code) return type.name;
        }
        return "未知";
    }
}
