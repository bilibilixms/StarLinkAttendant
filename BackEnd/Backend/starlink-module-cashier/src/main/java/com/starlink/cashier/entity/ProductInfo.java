package com.starlink.cashier.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 轻量级商品信息（仅供收银模块创建订单时查询价格和名称）。
 * 不依赖 starlink-module-product，直接读取 product 表。
 */
@Data
@TableName("product")
public class ProductInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("product_name")
    private String productName;

    @TableField("retail_price")
    private BigDecimal retailPrice;

    @TableField("member_price")
    private BigDecimal memberPrice;

    @TableField("is_active")
    private Integer isActive;
}
