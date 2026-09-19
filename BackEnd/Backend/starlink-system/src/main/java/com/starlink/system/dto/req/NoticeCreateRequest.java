package com.starlink.system.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NoticeCreateRequest {

    @NotBlank(message = "标题不能为空")
    private String title;

    private String content;

    private Byte notifyType = 1;

    private Byte targetType = 0;

    private String targetIds;

    private LocalDateTime publishedAt;

    private LocalDateTime expiredAt;
}
