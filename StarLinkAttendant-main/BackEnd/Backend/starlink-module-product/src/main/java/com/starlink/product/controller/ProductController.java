package com.starlink.product.controller;

import com.starlink.common.result.Result;
import com.starlink.common.util.PageResult;
import com.starlink.product.dto.request.ProductCreateRequest;
import com.starlink.product.dto.request.ProductQueryRequest;
import com.starlink.product.dto.request.ProductUpdateRequest;
import com.starlink.product.dto.resp.ProductResponse;
import com.starlink.product.entity.Product;
import com.starlink.product.entity.ProductCategory;
import com.starlink.product.service.ProductCategoryService;
import com.starlink.product.service.ProductService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductCategoryService productCategoryService;

    /**
     * 商品列表（分页）
     */
    @GetMapping("/list")
    public Result<PageResult<ProductResponse>> listProducts(ProductQueryRequest queryRequest) {
        PageResult<Product> productPage = productService.getProductPage(
                queryRequest,
                queryRequest.getProductName(),
                queryRequest.getCategoryId(),
                queryRequest.getProductType(),
                queryRequest.getIsActive()
        );

        List<ProductResponse> responseRecords = productPage.getRecords().stream()
                .map(this::toProductResponse)
                .toList();
        PageResult<ProductResponse> result = new PageResult<>(
                responseRecords, productPage.getTotal(),
                productPage.getPage(), productPage.getSize()
        );
        return Result.ok(result);
    }

    /**
     * 商品详情
     */
    @GetMapping("/{id}")
    public Result<ProductResponse> getProduct(@PathVariable Long id) {
        Product product = productService.getProductById(id);
        return Result.ok(toProductResponse(product));
    }

    /**
     * 新增商品
     */
    @PostMapping
    public Result<ProductResponse> createProduct(@Valid @RequestBody ProductCreateRequest request) {
        Product product = new Product();
        product.setCategoryId(request.getCategoryId());
        product.setProductCode(request.getProductCode());
        product.setProductName(request.getProductName());
        product.setProductType(request.getProductType());
        product.setUnit(request.getUnit());
        product.setCostPrice(request.getCostPrice());
        product.setRetailPrice(request.getRetailPrice());
        product.setMemberPrice(request.getMemberPrice());
        product.setImageUrl(request.getImageUrl());
        product.setIsVipOnly(request.getIsVipOnly());
        product.setIsActive(request.getIsActive());

        Product created = productService.createProduct(product);
        log.info("商品创建成功: id={}, name={}", created.getId(), created.getProductName());
        return Result.ok(toProductResponse(created));
    }

    /**
     * 编辑商品
     */
    @PutMapping("/{id}")
    public Result<ProductResponse> updateProduct(@PathVariable Long id,
                                                  @Valid @RequestBody ProductUpdateRequest request) {
        Product product = new Product();
        product.setCategoryId(request.getCategoryId());
        product.setProductCode(request.getProductCode());
        product.setProductName(request.getProductName());
        product.setProductType(request.getProductType());
        product.setUnit(request.getUnit());
        product.setCostPrice(request.getCostPrice());
        product.setRetailPrice(request.getRetailPrice());
        product.setMemberPrice(request.getMemberPrice());
        product.setImageUrl(request.getImageUrl());
        product.setIsVipOnly(request.getIsVipOnly());
        product.setIsActive(request.getIsActive());

        Product updated = productService.updateProduct(id, product);
        log.info("商品更新成功: id={}", id);
        return Result.ok(toProductResponse(updated));
    }

    /**
     * 删除商品
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        log.info("商品删除成功: id={}", id);
        return Result.ok();
    }

    /**
     * 上下架商品
     */
    @PatchMapping("/{id}/status")
    public Result<Void> updateProductStatus(@PathVariable Long id,
                                             @RequestBody Map<String, Integer> body) {
        Integer isActive = body.get("isActive");
        productService.updateProductStatus(id, isActive);
        log.info("商品状态更新成功: id={}, isActive={}", id, isActive);
        return Result.ok();
    }

    // ==================== 私有转换方法 ====================

    private ProductResponse toProductResponse(Product product) {
        ProductResponse response = new ProductResponse();
        response.setId(product.getId());
        response.setCategoryId(product.getCategoryId());
        response.setProductCode(product.getProductCode());
        response.setProductName(product.getProductName());
        response.setProductType(product.getProductType());
        response.setProductTypeLabel(getProductTypeLabel(product.getProductType()));
        response.setUnit(product.getUnit());
        response.setCostPrice(product.getCostPrice());
        response.setRetailPrice(product.getRetailPrice());
        response.setMemberPrice(product.getMemberPrice());
        response.setImageUrl(product.getImageUrl());
        response.setIsVipOnly(product.getIsVipOnly());
        response.setIsActive(product.getIsActive());
        response.setIsActiveLabel(getIsActiveLabel(product.getIsActive()));
        response.setCreatedAt(product.getCreatedAt());
        response.setUpdatedAt(product.getUpdatedAt());

        // 查询分类名称
        if (product.getCategoryId() != null) {
            try {
                ProductCategory category = productCategoryService.getCategoryById(product.getCategoryId());
                if (category != null) {
                    response.setCategoryName(category.getCategoryName());
                }
            } catch (Exception e) {
                log.warn("查询分类名称失败: categoryId={}", product.getCategoryId(), e);
            }
        }

        return response;
    }

    private String getProductTypeLabel(Integer productType) {
        if (productType == null) {
            return null;
        }
        return switch (productType) {
            case 1 -> "食品";
            case 2 -> "饮料";
            case 3 -> "虚拟商品";
            case 4 -> "日用品";
            case 5 -> "网游点卡";
            default -> "未知";
        };
    }

    private String getIsActiveLabel(Integer isActive) {
        if (isActive == null) {
            return null;
        }
        return switch (isActive) {
            case 0 -> "下架";
            case 1 -> "上架";
            default -> "未知";
        };
    }
}
