package com.starlink.marketing.enums;

import lombok.Getter;

/**
 * 活动类型枚举。
 *
 */
@Getter
public enum CampaignType {

    RECHARGE_GIFT(1, "充值赠送"),
    DISCOUNT(2, "满减优惠"),
    TIME_LIMITED(3, "限时折扣"),
    NEW_MEMBER(4, "新人专享"),
    BIRTHDAY(5, "生日活动"),
    POINTS_EXCHANGE(6, "积分兑换");

    private final int code;
    private final String name;

    CampaignType(int code, String name) {
        this.code = code;
        this.name = name;
    }

    public static String getNameByCode(Integer code) {
        if (code == null) return "";
        for (CampaignType type : values()) {
            if (type.code == code) return type.name;
        }
        return "未知";
    }

    public static CampaignType of(Integer code) {
        if (code == null) return null;
        for (CampaignType type : values()) {
            if (type.code == code) return type;
        }
        return null;
    }
}
