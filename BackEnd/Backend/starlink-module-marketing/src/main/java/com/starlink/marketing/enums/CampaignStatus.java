package com.starlink.marketing.enums;

import lombok.Getter;

/**
 * 活动状态枚举。
 *
 */
@Getter
public enum CampaignStatus {

    DRAFT(0, "草稿"),
    PUBLISHED(1, "已发布"),
    ACTIVE(2, "已生效"),
    ENDED(3, "已结束"),
    REMOVED(4, "已下架");

    private final int code;
    private final String name;

    CampaignStatus(int code, String name) {
        this.code = code;
        this.name = name;
    }

    public static String getNameByCode(Integer code) {
        if (code == null) return "";
        for (CampaignStatus status : values()) {
            if (status.code == code) return status.name;
        }
        return "未知";
    }
}
