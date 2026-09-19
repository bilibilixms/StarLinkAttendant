package com.starlink.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.starlink.common.exception.BusinessException;
import com.starlink.common.result.ErrorCode;
import com.starlink.product.entity.ProductCategory;
import com.starlink.product.mapper.ProductCategoryMapper;
import com.starlink.product.mapper.ProductMapper;
import com.starlink.product.service.ProductCategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCategoryServiceImpl implements ProductCategoryService {

    private final ProductCategoryMapper categoryMapper;
    private final ProductMapper productMapper;

    @Override
    public List<ProductCategory> getAllCategories() {
        LambdaQueryWrapper<ProductCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(ProductCategory::getSortOrder);
        return categoryMapper.selectList(wrapper);
    }

    @Override
    public List<ProductCategory> getCategoryTree() {
        List<ProductCategory> all = getAllCategories();

        Map<Long, List<ProductCategory>> childrenMap = all.stream()
                .filter(c -> c.getParentId() != null)
                .collect(Collectors.groupingBy(ProductCategory::getParentId));

        List<ProductCategory> roots = all.stream()
                .filter(c -> c.getParentId() == null)
                .collect(Collectors.toList());

        for (ProductCategory root : roots) {
            root.setChildren(buildChildren(root.getId(), childrenMap));
        }

        return roots;
    }

    private List<ProductCategory> buildChildren(Long parentId, Map<Long, List<ProductCategory>> childrenMap) {
        List<ProductCategory> children = childrenMap.getOrDefault(parentId, new ArrayList<>());
        for (ProductCategory child : children) {
            child.setChildren(buildChildren(child.getId(), childrenMap));
        }
        return children;
    }

    @Override
    public ProductCategory getCategoryById(Long id) {
        return categoryMapper.selectById(id);
    }

    @Override
    @Transactional
    public ProductCategory createCategory(ProductCategory category) {
        if (category.getParentId() != null) {
            ProductCategory parent = categoryMapper.selectById(category.getParentId());
            if (parent == null) {
                throw new BusinessException(ErrorCode.PARENT_CATEGORY_NOT_FOUND);
            }
            category.setLevel(parent.getLevel() + 1);
        } else {
            category.setLevel(1);
        }

        category.setIsActive(category.getIsActive() == null ? 1 : category.getIsActive());
        category.setSortOrder(category.getSortOrder() == null ? 0 : category.getSortOrder());

        categoryMapper.insert(category);
        log.info("创建分类: id={}, name={}", category.getId(), category.getCategoryName());
        return category;
    }

    @Override
    @Transactional
    public ProductCategory updateCategory(Long id, ProductCategory category) {
        ProductCategory existing = categoryMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.PRODUCT_CATEGORY_NOT_FOUND);
        }

        if (category.getParentId() != null && category.getParentId().equals(id)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "分类不能设为自己的子分类");
        }

        if (category.getParentId() != null) {
            ProductCategory parent = categoryMapper.selectById(category.getParentId());
            if (parent == null) {
                throw new BusinessException(ErrorCode.PARENT_CATEGORY_NOT_FOUND);
            }
            category.setLevel(parent.getLevel() + 1);
        } else {
            category.setLevel(1);
        }

        int oldLevel = existing.getLevel();
        int newLevel = category.getLevel();

        category.setId(id);
        categoryMapper.updateById(category);

        if (oldLevel != newLevel) {
            cascadeUpdateLevel(id, newLevel);
        }

        log.info("更新分类: id={}", id);
        return categoryMapper.selectById(id);
    }

    private void cascadeUpdateLevel(Long parentId, int parentLevel) {
        LambdaQueryWrapper<ProductCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductCategory::getParentId, parentId);
        List<ProductCategory> children = categoryMapper.selectList(wrapper);

        for (ProductCategory child : children) {
            child.setLevel(parentLevel + 1);
            categoryMapper.updateById(child);
            cascadeUpdateLevel(child.getId(), parentLevel + 1);
        }
    }

    @Override
    @Transactional
    public void deleteCategory(Long id) {
        ProductCategory category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException(ErrorCode.PRODUCT_CATEGORY_NOT_FOUND);
        }

        LambdaQueryWrapper<ProductCategory> childWrapper = new LambdaQueryWrapper<>();
        childWrapper.eq(ProductCategory::getParentId, id);
        if (categoryMapper.selectCount(childWrapper) > 0) {
            throw new BusinessException(ErrorCode.PRODUCT_CATEGORY_HAS_CHILDREN);
        }

        LambdaQueryWrapper<com.starlink.product.entity.Product> productWrapper = new LambdaQueryWrapper<>();
        productWrapper.eq(com.starlink.product.entity.Product::getCategoryId, id);
        if (productMapper.selectCount(productWrapper) > 0) {
            throw new BusinessException(ErrorCode.PRODUCT_CATEGORY_HAS_PRODUCTS);
        }

        categoryMapper.deleteById(id);
        log.info("删除分类: id={}, name={}", id, category.getCategoryName());
    }

    @Override
    @Transactional
    public void updateCategoryStatus(Long id, Integer isActive) {
        ProductCategory category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException(ErrorCode.PRODUCT_CATEGORY_NOT_FOUND);
        }
        category.setIsActive(isActive);
        categoryMapper.updateById(category);
        log.info("更新分类状态: id={}, status={}", id, isActive);
    }
}
