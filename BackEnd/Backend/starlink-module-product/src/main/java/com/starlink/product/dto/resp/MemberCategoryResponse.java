package com.starlink.product.dto.resp;

import lombok.Data;

/**
 * 小程序自助点餐「商品分类」响应 DTO。
 * <p>
 * 字段对齐小程序分类展示（id/name/sort），数据来自真实 product_category 表。
 */
@Data
public class MemberCategoryResponse {

    private Long id;

    /** 分类名称 */
    private String name;

    /** 排序（越小越靠前） */
    private Integer sort;
}