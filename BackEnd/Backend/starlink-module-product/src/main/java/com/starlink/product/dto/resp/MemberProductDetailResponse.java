package com.starlink.product.dto.resp;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 小程序热门商品「详情」响应 DTO。
 * <p>
 * 字段对齐小程序详情页 Product 展示所需。数据来自真实 product 表，
 * 当前表无库存/描述/规格/多图字段，故 stock 用占位常量、description 用兜底文案，
 * images 单图（image_url）。仅供「详情真实展示 + 下单 mock」阶段使用。
 */
@Data
public class MemberProductDetailResponse {

    /** 商品 ID（product.id） */
    private Long id;

    /** 商品名称 */
    private String name;

    /** 商品类型（映射后：1=实物, 2=虚拟） */
    private Integer type;

    /** 分类 ID */
    private Long categoryId;

    /** 单位 */
    private String unit;

    /** 售价 */
    private BigDecimal price;

    /** 会员价（可能为 null） */
    private BigDecimal memberPrice;

    /** 库存（占位：商品表无库存字段，固定为充足值，供下单 mock 使用） */
    private Integer stock;

    /** 销量（order_item 累计数量） */
    private Integer sales;

    /** 商品主图 */
    private String cover;

    /** 图片列表（无多图，单图填 cover） */
    private List<String> images;

    /** 商品介绍（兜底文案） */
    private String description;

    /** 规格（表无此字段，为 null） */
    private String spec;

    /** 标签（表无此字段，为空） */
    private List<String> tags;

    /** 上架状态：1-上架 0-下架 */
    private Integer status;
}