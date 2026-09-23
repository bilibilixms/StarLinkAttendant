package com.starlink.member.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PointsRecordResponse {

    private Long id;

    private Long memberId;

    private Integer points;

    private Integer balanceBefore;

    private Integer balanceAfter;

    private Byte bizType;

    private String bizTypeLabel;

    private Long bizId;

    private String remark;

    private LocalDateTime expireAt;

    private LocalDateTime createdAt;
}