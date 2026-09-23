package com.starlink.product.service;

import com.starlink.product.entity.ProductCategory;

import java.util.List;

public interface ProductCategoryService {

    List<ProductCategory> getAllCategories();

    List<ProductCategory> getCategoryTree();

    ProductCategory getCategoryById(Long id);

    ProductCategory createCategory(ProductCategory category);

    ProductCategory updateCategory(Long id, ProductCategory category);

    void deleteCategory(Long id);

    void updateCategoryStatus(Long id, Integer isActive);
}
