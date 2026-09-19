package com.starlink.system.dto.req;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NoticeUpdateRequest {

    private String title;

    private String content;

    private LocalDateTime publishedAt;

    private LocalDateTime expiredAt;
}
