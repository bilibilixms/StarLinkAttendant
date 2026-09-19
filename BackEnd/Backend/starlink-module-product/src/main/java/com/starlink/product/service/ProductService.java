package com.starlink.product.service;

import com.starlink.common.util.PageQuery;
import com.starlink.common.util.PageResult;
import com.starlink.product.entity.Product;

public interface ProductService {

    PageResult<Product> getProductPage(PageQuery pageQuery, String productName, Long categoryId, Integer productType, Integer isActive);

    Product getProductById(Long id);

    Product createProduct(Product product);

    Product updateProduct(Long id, Product product);

    void deleteProduct(Long id);

    void updateProductStatus(Long id, Integer isActive);
}
