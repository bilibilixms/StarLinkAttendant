package com.starlink.product.dto.request;

import com.starlink.common.util.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProductQueryRequest extends PageQuery {

    private String productName;

    private Long categoryId;

    private Integer productType;

    private Integer isActive;
}
