package com.starlink.system.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NoticeResponse {

    private Long id;

    private Byte notifyType;

    private String title;

    private String content;

    private Byte targetType;

    private String targetIds;

    private Byte isRead;

    private LocalDateTime publishedAt;

    private LocalDateTime expiredAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
