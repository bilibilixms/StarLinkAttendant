package com.starlink.product.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.starlink.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product_category")
public class ProductCategory extends BaseEntity {

    @TableField("parent_id")
    private Long parentId;

    @TableField("category_name")
    private String categoryName;

    @TableField("icon")
    private String icon;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("level")
    private Integer level;

    @TableField("is_active")
    private Integer isActive;

    /** 子分类列表（非数据库字段，用于构建树形结构） */
    @TableField(exist = false)
    private List<ProductCategory> children;
}
