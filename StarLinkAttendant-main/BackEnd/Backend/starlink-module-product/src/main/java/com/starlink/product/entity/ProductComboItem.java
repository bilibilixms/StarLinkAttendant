package com.starlink.product.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.starlink.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product_combo_item")
public class ProductComboItem extends BaseEntity {

    @TableField("combo_id")
    private Long comboId;

    @TableField("product_id")
    private Long productId;

    @TableField("product_name")
    private String productName;

    @TableField("unit_price")
    private BigDecimal unitPrice;

    @TableField("quantity")
    private Integer quantity;

    @TableField("subtotal")
    private BigDecimal subtotal;
}
