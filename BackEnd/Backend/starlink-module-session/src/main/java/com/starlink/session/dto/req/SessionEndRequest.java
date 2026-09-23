package com.starlink.session.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 下机请求。
 *
 */
@Data
public class SessionEndRequest {

    /** 会话 ID */
    @NotNull(message = "会话 ID 不能为空")
    private Long sessionId;

    /** 实付金额（可选，用于结算） */
    private java.math.BigDecimal paidAmount;
}
