package com.starlink.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.starlink.product.entity.Product;
import com.starlink.product.entity.ProductCombo;
import com.starlink.product.entity.ProductComboItem;
import com.starlink.product.mapper.ProductComboItemMapper;
import com.starlink.product.mapper.ProductComboMapper;
import com.starlink.product.mapper.ProductMapper;
import com.starlink.product.service.ProductComboService;
import com.starlink.common.exception.BusinessException;
import com.starlink.common.result.ErrorCode;
import com.starlink.common.util.PageQuery;
import com.starlink.common.util.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductComboServiceImpl implements ProductComboService {

    private final ProductComboMapper comboMapper;
    private final ProductComboItemMapper comboItemMapper;
    private final ProductMapper productMapper;

    @Override
    public PageResult<ProductCombo> getComboPage(PageQuery pageQuery, String comboName, Integer isActive) {
        LambdaQueryWrapper<ProductCombo> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(comboName)) {
            wrapper.like(ProductCombo::getComboName, comboName);
        }
        if (isActive != null) {
            wrapper.eq(ProductCombo::getIsActive, isActive);
        }

        wrapper.orderByDesc(ProductCombo::getCreatedAt);

        Page<ProductCombo> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());
        IPage<ProductCombo> mpPage = comboMapper.selectPage(page, wrapper);
        return PageResult.from(mpPage);
    }

    @Override
    public ProductCombo getComboById(Long id) {
        ProductCombo combo = comboMapper.selectById(id);
        if (combo == null) {
            throw new BusinessException(ErrorCode.COMBO_NOT_FOUND);
        }
        return combo;
    }

    @Override
    @Transactional
    public ProductCombo createCombo(ProductCombo combo, Map<Long, Integer> productIdQuantityMap) {
        if (CollectionUtils.isEmpty(productIdQuantityMap)) {
            throw new BusinessException(ErrorCode.COMBO_PRODUCT_EMPTY);
        }

        List<Long> productIds = productIdQuantityMap.keySet().stream().distinct().collect(Collectors.toList());
        List<Product> products = productMapper.selectBatchIds(productIds);
        if (products.size() != productIds.size()) {
            throw new BusinessException(ErrorCode.COMBO_PRODUCT_NOT_FOUND);
        }

        Map<Long, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        BigDecimal originalPrice = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> entry : productIdQuantityMap.entrySet()) {
            Product product = productMap.get(entry.getKey());
            if (product == null) {
                throw new BusinessException(ErrorCode.COMBO_PRODUCT_NOT_FOUND);
            }
            if (product.getRetailPrice() == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "商品“" + product.getProductName() + "”未设置零售价");
            }
            int quantity = entry.getValue() != null ? entry.getValue() : 1;
            originalPrice = originalPrice.add(product.getRetailPrice().multiply(BigDecimal.valueOf(quantity)));
        }
        combo.setOriginalPrice(originalPrice);

        combo.setIsActive(combo.getIsActive() == null ? 1 : combo.getIsActive());
        combo.setSortOrder(combo.getSortOrder() == null ? 0 : combo.getSortOrder());

        comboMapper.insert(combo);
        log.info("创建套餐: id={}, name={}", combo.getId(), combo.getComboName());

        saveComboItems(combo.getId(), productIdQuantityMap, productMap);
        return combo;
    }

    @Override
    @Transactional
    public ProductCombo updateCombo(Long id, ProductCombo combo, Map<Long, Integer> productIdQuantityMap) {
        ProductCombo existing = comboMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.COMBO_NOT_FOUND);
        }

        combo.setId(id);

        if (productIdQuantityMap != null) {
            if (CollectionUtils.isEmpty(productIdQuantityMap)) {
                throw new BusinessException(ErrorCode.COMBO_PRODUCT_EMPTY);
            }

            List<Long> productIds = productIdQuantityMap.keySet().stream().distinct().collect(Collectors.toList());
            List<Product> products = productMapper.selectBatchIds(productIds);
            if (products.size() != productIds.size()) {
                throw new BusinessException(ErrorCode.COMBO_PRODUCT_NOT_FOUND);
            }

            Map<Long, Product> productMap = products.stream()
                    .collect(Collectors.toMap(Product::getId, p -> p));

            BigDecimal originalPrice = BigDecimal.ZERO;
            for (Map.Entry<Long, Integer> entry : productIdQuantityMap.entrySet()) {
                Product product = productMap.get(entry.getKey());
                if (product == null) {
                    throw new BusinessException(ErrorCode.COMBO_PRODUCT_NOT_FOUND);
                }
                if (product.getRetailPrice() == null) {
                    throw new BusinessException(ErrorCode.PARAM_ERROR, "商品“" + product.getProductName() + "”未设置零售价");
                }
                int quantity = entry.getValue() != null ? entry.getValue() : 1;
                originalPrice = originalPrice.add(product.getRetailPrice().multiply(BigDecimal.valueOf(quantity)));
            }
            combo.setOriginalPrice(originalPrice);

            comboMapper.updateById(combo);

            LambdaQueryWrapper<ProductComboItem> deleteWrapper = new LambdaQueryWrapper<>();
            deleteWrapper.eq(ProductComboItem::getComboId, id);
            comboItemMapper.delete(deleteWrapper);

            saveComboItems(id, productIdQuantityMap, productMap);
        } else {
            comboMapper.updateById(combo);
        }

        log.info("更新套餐: id={}", id);
        return comboMapper.selectById(id);
    }

    @Override
    @Transactional
    public void deleteCombo(Long id) {
        ProductCombo combo = comboMapper.selectById(id);
        if (combo == null) {
            throw new BusinessException(ErrorCode.COMBO_NOT_FOUND);
        }

        LambdaQueryWrapper<ProductComboItem> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(ProductComboItem::getComboId, id);
        comboItemMapper.delete(deleteWrapper);

        comboMapper.deleteById(id);
        log.info("删除套餐: id={}, name={}", id, combo.getComboName());
    }

    @Override
    @Transactional
    public void updateComboStatus(Long id, Integer isActive) {
        ProductCombo combo = comboMapper.selectById(id);
        if (combo == null) {
            throw new BusinessException(ErrorCode.COMBO_NOT_FOUND);
        }
        combo.setIsActive(isActive);
        comboMapper.updateById(combo);
        log.info("更新套餐状态: id={}, status={}", id, isActive);
    }

    private void saveComboItems(Long comboId, Map<Long, Integer> productIdQuantityMap, Map<Long, Product> productMap) {
        for (Map.Entry<Long, Integer> entry : productIdQuantityMap.entrySet()) {
            Product product = productMap.get(entry.getKey());
            if (product == null) continue;

            int quantity = entry.getValue() != null ? entry.getValue() : 1;

            ProductComboItem item = new ProductComboItem();
            item.setComboId(comboId);
            item.setProductId(product.getId());
            item.setProductName(product.getProductName());
            item.setUnitPrice(product.getRetailPrice());
            item.setQuantity(quantity);
            item.setSubtotal(product.getRetailPrice().multiply(BigDecimal.valueOf(quantity)));
            comboItemMapper.insert(item);
        }
    }
}
