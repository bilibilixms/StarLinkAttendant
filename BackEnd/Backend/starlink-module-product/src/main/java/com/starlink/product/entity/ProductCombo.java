package com.starlink.product.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.starlink.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product_combo")
public class ProductCombo extends BaseEntity {

    @TableField("combo_name")
    private String comboName;

    @TableField("combo_code")
    private String comboCode;

    @TableField("description")
    private String description;

    @TableField("original_price")
    private BigDecimal originalPrice;

    @TableField("combo_price")
    private BigDecimal comboPrice;

    @TableField("image_url")
    private String imageUrl;

    @TableField("is_active")
    private Integer isActive;

    @TableField("sort_order")
    private Integer sortOrder;
}
