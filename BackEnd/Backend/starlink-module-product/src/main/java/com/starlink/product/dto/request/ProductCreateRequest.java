package com.starlink.product.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductCreateRequest {

    private Long categoryId;

    private String productCode;

    @NotBlank(message = "商品名称不能为空")
    private String productName;

    private Integer productType;

    private String unit;

    @Positive(message = "成本价必须大于0")
    private BigDecimal costPrice;

    @NotNull(message = "零售价不能为空")
    @Positive(message = "零售价必须大于0")
    private BigDecimal retailPrice;

    @Positive(message = "会员价必须大于0")
    private BigDecimal memberPrice;

    private String imageUrl;

    private Integer isVipOnly;

    private Integer isActive;
}
