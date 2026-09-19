package com.starlink.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.starlink.product.entity.Product;
import com.starlink.product.mapper.ProductMapper;
import com.starlink.product.service.ProductService;
import com.starlink.common.exception.BusinessException;
import com.starlink.common.result.ErrorCode;
import com.starlink.common.util.PageQuery;
import com.starlink.common.util.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;

    @Override
    public PageResult<Product> getProductPage(PageQuery pageQuery, String productName, Long categoryId, Integer productType, Integer isActive) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(productName)) {
            wrapper.like(Product::getProductName, productName);
        }
        if (categoryId != null) {
            wrapper.eq(Product::getCategoryId, categoryId);
        }
        if (productType != null) {
            wrapper.eq(Product::getProductType, productType);
        }
        if (isActive != null) {
            wrapper.eq(Product::getIsActive, isActive);
        }

        wrapper.orderByDesc(Product::getCreatedAt);

        Page<Product> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());
        IPage<Product> mpPage = productMapper.selectPage(page, wrapper);
        return PageResult.from(mpPage);
    }

    @Override
    public Product getProductById(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        return product;
    }

    @Override
    @Transactional
    public Product createProduct(Product product) {
        if (StringUtils.hasText(product.getProductCode())) {
            LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Product::getProductCode, product.getProductCode());
            if (productMapper.selectCount(wrapper) > 0) {
                throw new BusinessException(ErrorCode.PRODUCT_CODE_DUPLICATE);
            }
        }

        product.setIsActive(product.getIsActive() == null ? 1 : product.getIsActive());
        product.setIsVipOnly(product.getIsVipOnly() == null ? 0 : product.getIsVipOnly());
        product.setUnit(product.getUnit() == null ? "份" : product.getUnit());

        productMapper.insert(product);
        log.info("创建商品: id={}, name={}", product.getId(), product.getProductName());
        return product;
    }

    @Override
    @Transactional
    public Product updateProduct(Long id, Product product) {
        Product existing = productMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        if (StringUtils.hasText(product.getProductCode()) && !product.getProductCode().equals(existing.getProductCode())) {
            LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Product::getProductCode, product.getProductCode());
            wrapper.ne(Product::getId, id);
            if (productMapper.selectCount(wrapper) > 0) {
                throw new BusinessException(ErrorCode.PRODUCT_CODE_DUPLICATE);
            }
        }

        product.setId(id);
        productMapper.updateById(product);
        log.info("更新商品: id={}", id);
        return productMapper.selectById(id);
    }

    @Override
    @Transactional
    public void deleteProduct(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        productMapper.deleteById(id);
        log.info("删除商品: id={}, name={}", id, product.getProductName());
    }

    @Override
    @Transactional
    public void updateProductStatus(Long id, Integer isActive) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        product.setIsActive(isActive);
        productMapper.updateById(product);
        log.info("更新商品状态: id={}, status={}", id, isActive);
    }
}
