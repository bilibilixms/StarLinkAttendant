package com.starlink.member.dto.resp;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class LevelResponse {

    private Long id;

    private String levelName;

    private Byte levelOrder;

    private Integer minGrowth;

    private Integer maxGrowth;

    private BigDecimal discountRate;

    private BigDecimal hourlyDiscount;

    private BigDecimal rechargeBonusRate;

    private BigDecimal pointsMultiple;

    private Byte autoUpgrade;

    private String iconUrl;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}