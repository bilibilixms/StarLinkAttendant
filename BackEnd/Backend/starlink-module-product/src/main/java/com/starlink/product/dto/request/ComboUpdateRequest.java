package com.starlink.product.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ComboUpdateRequest {

    private String comboName;

    private String comboCode;

    private String description;

    @Positive(message = "套餐价格必须大于0")
    private BigDecimal comboPrice;

    private String imageUrl;

    private Integer isActive;

    private Integer sortOrder;

    @Valid
    private List<ComboItemRequest> comboItems;
}
