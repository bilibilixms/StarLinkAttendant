package com.starlink.product.dto.resp;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 小程序「热门商品」响应 DTO。
 * <p>
 * 字段对齐小程序首页 Product 的展示需要（id/name/price/memberPrice/sales/cover），
 * 数据来自真实 product 表按销量聚合。
 */
@Data
public class HotProductResponse {

    /** 商品 ID（product.id） */
    private Long id;

    /** 商品名称 */
    private String name;

    /** 商品类型（映射：1→1, 2→2, 其余→2 虚拟） */
    private Integer type;

    /** 分类 ID */
    private Long categoryId;

    /** 单位 */
    private String unit;

    /** 售价 */
    private BigDecimal price;

    /** 会员价（可能为 null） */
    private BigDecimal memberPrice;

    /** 销量（order_item 累计数量） */
    private Integer sales;

    /** 商品图片 */
    private String cover;

    /** 上架状态：1-上架 0-下架 */
    private Integer status;
}