package com.starlink.member.dto.resp;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class MemberResponse {

    private Long id;

    private String memberNo;

    private String realName;

    private Byte gender;

    private String genderLabel;

    private String phone;

    private LocalDate birthday;

    private Long levelId;

    private String levelName;

    private Long totalPoints;

    private Long availablePoints;

    private Integer growthValue;

    private BigDecimal totalRecharge;

    private BigDecimal balance;

    private BigDecimal totalConsumption;

    private LocalDateTime lastLoginTime;

    private LocalDateTime lastOnlineTime;

    private Integer totalOnlineHours;

    private Byte registerSource;

    private String registerSourceLabel;

    private Byte status;

    private String statusLabel;

    private String blacklistReason;

    private String tag;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}