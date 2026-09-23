package com.starlink.product.dto.request;

import com.starlink.common.util.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ComboQueryRequest extends PageQuery {

    private String comboName;

    private Integer isActive;
}
