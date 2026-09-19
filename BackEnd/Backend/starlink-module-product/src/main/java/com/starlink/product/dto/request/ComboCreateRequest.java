package com.starlink.product.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ComboCreateRequest {

    @NotBlank(message = "套餐名称不能为空")
    private String comboName;

    private String comboCode;

    private String description;

    @NotNull(message = "套餐价格不能为空")
    @Positive(message = "套餐价格必须大于0")
    private BigDecimal comboPrice;

    private String imageUrl;

    private Integer isActive;

    private Integer sortOrder;

    @NotEmpty(message = "套餐商品不能为空")
    @Valid
    private List<ComboItemRequest> comboItems;
}
