package com.starlink.product.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.starlink.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product")
public class Product extends BaseEntity {

    @TableField("category_id")
    private Long categoryId;

    @TableField("product_code")
    private String productCode;

    @TableField("product_name")
    private String productName;

    @TableField("product_type")
    private Integer productType;

    @TableField("unit")
    private String unit;

    @TableField("cost_price")
    private BigDecimal costPrice;

    @TableField("retail_price")
    private BigDecimal retailPrice;

    @TableField("member_price")
    private BigDecimal memberPrice;

    @TableField("image_url")
    private String imageUrl;

    @TableField("is_vip_only")
    private Integer isVipOnly;

    @TableField("is_active")
    private Integer isActive;
}
