package com.starlink.session.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 暂停上机（临时下机）请求。
 *
 */
@Data
public class SessionPauseRequest {

    /** 会话 ID */
    @NotNull(message = "会话 ID 不能为空")
    private Long sessionId;
}
