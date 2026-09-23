package com.starlink.member.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class LevelCreateRequest {

    @NotBlank(message = "等级名称不能为空")
    private String levelName;

    @NotNull(message = "排序序号不能为空")
    private Byte levelOrder;

    @NotNull(message = "成长值下限不能为空")
    private Integer minGrowth;

    @NotNull(message = "成长值上限不能为空")
    private Integer maxGrowth;

    private BigDecimal discountRate = new BigDecimal("100.00");

    private BigDecimal hourlyDiscount = new BigDecimal("100.00");

    private BigDecimal rechargeBonusRate = BigDecimal.ZERO;

    private BigDecimal pointsMultiple = BigDecimal.ONE;

    private Byte autoUpgrade = 1;

    private String iconUrl;
}