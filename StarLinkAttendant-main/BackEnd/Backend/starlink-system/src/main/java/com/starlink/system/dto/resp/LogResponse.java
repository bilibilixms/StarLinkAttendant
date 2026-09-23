package com.starlink.system.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LogResponse {

    private Long id;

    private Long operatorId;

    private String operatorName;

    private String bizType;

    private String bizTypeLabel;

    private Long bizId;

    private String action;

    private String actionLabel;

    private String detail;

    private String ipAddress;

    private String userAgent;

    private LocalDateTime createdAt;
}
