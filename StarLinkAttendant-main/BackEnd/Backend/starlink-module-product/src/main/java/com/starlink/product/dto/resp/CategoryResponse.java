package com.starlink.product.dto.resp;

import lombok.Data;

import java.util.List;

@Data
public class CategoryResponse {

    private Long id;

    private Long parentId;

    private String categoryName;

    private String icon;

    private Integer sortOrder;

    private Integer level;

    private Integer isActive;

    /** 启用状态标签（0-禁用, 1-启用） */
    private String isActiveLabel;

    /** 子分类列表（树形结构） */
    private List<CategoryResponse> children;
}
