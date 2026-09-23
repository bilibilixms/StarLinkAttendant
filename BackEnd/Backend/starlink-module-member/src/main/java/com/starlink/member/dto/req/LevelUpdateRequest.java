package com.starlink.member.dto.req;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class LevelUpdateRequest {

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
}