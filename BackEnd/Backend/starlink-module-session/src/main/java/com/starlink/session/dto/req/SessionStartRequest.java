package com.starlink.session.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 上机请求。
 *
 */
@Data
public class SessionStartRequest {

    /** 会员 ID */
    @NotNull(message = "会员 ID 不能为空")
    private Long memberId;

    /** 机位 ID */
    @NotNull(message = "机位 ID 不能为空")
    private Long computerId;

    /** 认证方式：1-刷卡 2-扫码 3-人脸 4-临时密码 */
    private Byte authMethod = 1;

    /** 费率方案 ID（可选，为空时使用机位默认方案） */
    private Long tariffPlanId;

    /** 预计时长（分钟，可选） */
    private Integer expectedMinutes;
}
