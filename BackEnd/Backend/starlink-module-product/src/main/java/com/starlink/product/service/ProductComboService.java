package com.starlink.product.service;

import com.starlink.common.util.PageQuery;
import com.starlink.common.util.PageResult;
import com.starlink.product.entity.ProductCombo;

import java.util.Map;

public interface ProductComboService {

    PageResult<ProductCombo> getComboPage(PageQuery pageQuery, String comboName, Integer isActive);

    ProductCombo getComboById(Long id);

    ProductCombo createCombo(ProductCombo combo, Map<Long, Integer> productIdQuantityMap);

    ProductCombo updateCombo(Long id, ProductCombo combo, Map<Long, Integer> productIdQuantityMap);

    void deleteCombo(Long id);

    void updateComboStatus(Long id, Integer isActive);
}
