package com.starlink.product.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CategoryCreateRequest {

    private Long parentId;

    @NotBlank(message = "分类名称不能为空")
    private String categoryName;

    private String icon;

    private Integer sortOrder;

    private Integer isActive;
}
