package com.starlink.session.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 换机请求。
 *
 */
@Data
public class SessionTransferRequest {

    /** 会话 ID */
    @NotNull(message = "会话 ID 不能为空")
    private Long sessionId;

    /** 目标机位 ID */
    @NotNull(message = "目标机位 ID 不能为空")
    private Long targetComputerId;
}
