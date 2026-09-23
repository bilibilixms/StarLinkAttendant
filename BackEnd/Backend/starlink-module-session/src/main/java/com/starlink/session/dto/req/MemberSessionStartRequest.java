package com.starlink.session.dto.req;

import lombok.Data;

/**
 * 会员端「一键上机」请求（小程序）。
 * <p>
 * 小程序已改为选座直开（无需扫码），正常会直接传 computerId；
 * qrContent 仅作为兼容字段保留（JSON 串或 "storeId-areaId-computerId" 文本），
 * computerId 缺失时尝试从中解析。
 *
 */
@Data
public class MemberSessionStartRequest {

    /** 机位 ID（选座直开必传） */
    private Long computerId;

    /** 二维码原始内容（兼容字段） */
    private String qrContent;

    /** 门店 ID（单店演示阶段保留） */
    private Long storeId;

    /** 区域 ID（仅展示用，费率一律以机位绑定的费率方案为准） */
    private Long areaId;

    /** 机位名称（仅展示用） */
    private String computerName;
}
