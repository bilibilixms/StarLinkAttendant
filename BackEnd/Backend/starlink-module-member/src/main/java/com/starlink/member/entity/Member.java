package com.starlink.member.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.starlink.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("member")
public class Member extends BaseEntity {

    private String memberNo;

    private String realName;

    private Byte gender;

    private String phone;

    private String idCard;

    private String idCardHash;

    private LocalDate birthday;

    private Long levelId;

    private Long totalPoints;

    private Long availablePoints;

    /** 累计成长值（1元=1经验） */
    private Integer growthValue;

    private BigDecimal totalRecharge;

    private BigDecimal balance;

    private BigDecimal totalConsumption;

    private LocalDateTime lastLoginTime;

    private LocalDateTime lastOnlineTime;

    private Integer totalOnlineHours;

    private String faceFeature;

    private String passwordHash;

    private Byte registerSource;

    private Byte status;

    private String blacklistReason;

    private String tag;
}