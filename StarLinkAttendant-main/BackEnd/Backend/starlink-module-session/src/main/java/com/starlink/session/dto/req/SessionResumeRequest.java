package com.starlink.session.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 恢复上机请求。
 *
 */
@Data
public class SessionResumeRequest {

    /** 会话 ID */
    @NotNull(message = "会话 ID 不能为空")
    private Long sessionId;
}
