package com.starlink.product.controller;

import com.starlink.common.result.Result;
import com.starlink.product.dto.request.CategoryCreateRequest;
import com.starlink.product.dto.request.CategoryUpdateRequest;
import com.starlink.product.dto.resp.CategoryResponse;
import com.starlink.product.entity.ProductCategory;
import com.starlink.product.service.ProductCategoryService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/product/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final ProductCategoryService productCategoryService;

    /**
     * 获取分类树
     */
    @GetMapping
    public Result<List<CategoryResponse>> getCategoryTree() {
        List<ProductCategory> tree = productCategoryService.getCategoryTree();
        List<CategoryResponse> responseList = convertToCategoryResponseList(tree);
        return Result.ok(responseList);
    }

    /**
     * 新增分类
     */
    @PostMapping
    public Result<CategoryResponse> createCategory(@Valid @RequestBody CategoryCreateRequest request) {
        ProductCategory category = new ProductCategory();
        category.setParentId(request.getParentId());
        category.setCategoryName(request.getCategoryName());
        category.setIcon(request.getIcon());
        category.setSortOrder(request.getSortOrder());
        category.setIsActive(request.getIsActive());

        ProductCategory created = productCategoryService.createCategory(category);
        log.info("分类创建成功: id={}, name={}", created.getId(), created.getCategoryName());
        return Result.ok(toCategoryResponse(created));
    }

    /**
     * 编辑分类
     */
    @PutMapping("/{id}")
    public Result<CategoryResponse> updateCategory(@PathVariable Long id,
                                                    @Valid @RequestBody CategoryUpdateRequest request) {
        ProductCategory category = new ProductCategory();
        category.setParentId(request.getParentId());
        category.setCategoryName(request.getCategoryName());
        category.setIcon(request.getIcon());
        category.setSortOrder(request.getSortOrder());
        category.setIsActive(request.getIsActive());

        ProductCategory updated = productCategoryService.updateCategory(id, category);
        log.info("分类更新成功: id={}", id);
        return Result.ok(toCategoryResponse(updated));
    }

    /**
     * 删除分类
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        productCategoryService.deleteCategory(id);
        log.info("分类删除成功: id={}", id);
        return Result.ok();
    }

    // ==================== 私有转换方法 ====================

    private List<CategoryResponse> convertToCategoryResponseList(List<ProductCategory> categories) {
        if (categories == null || categories.isEmpty()) {
            return Collections.emptyList();
        }
        return categories.stream().map(this::toCategoryResponse).toList();
    }

    private CategoryResponse toCategoryResponse(ProductCategory category) {
        CategoryResponse response = new CategoryResponse();
        response.setId(category.getId());
        response.setParentId(category.getParentId());
        response.setCategoryName(category.getCategoryName());
        response.setIcon(category.getIcon());
        response.setSortOrder(category.getSortOrder());
        response.setLevel(category.getLevel());
        response.setIsActive(category.getIsActive());
        response.setIsActiveLabel(getIsActiveLabel(category.getIsActive()));
        response.setChildren(convertToCategoryResponseList(category.getChildren()));
        return response;
    }

    private String getIsActiveLabel(Integer isActive) {
        if (isActive == null) {
            return null;
        }
        return switch (isActive) {
            case 0 -> "禁用";
            case 1 -> "启用";
            default -> "未知";
        };
    }
}
