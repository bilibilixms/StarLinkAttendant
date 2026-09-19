package com.starlink.session.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 强制下机请求。
 *
 */
@Data
public class ForceEndRequest {

    /** 会话 ID */
    @NotNull(message = "会话 ID 不能为空")
    private Long sessionId;

    /** 强制下机原因 */
    private String reason;
}
