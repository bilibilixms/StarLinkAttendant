package com.starlink.product.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.starlink.common.result.Result;
import com.starlink.common.util.PageResult;
import com.starlink.product.dto.request.ComboCreateRequest;
import com.starlink.product.dto.request.ComboItemRequest;
import com.starlink.product.dto.request.ComboQueryRequest;
import com.starlink.product.dto.request.ComboUpdateRequest;
import com.starlink.product.dto.resp.ComboItemResponse;
import com.starlink.product.dto.resp.ComboResponse;
import com.starlink.product.entity.ProductCombo;
import com.starlink.product.entity.ProductComboItem;
import com.starlink.product.mapper.ProductComboItemMapper;
import com.starlink.product.service.ProductComboService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/product/combos")
@RequiredArgsConstructor
public class ComboController {

    private final ProductComboService productComboService;
    private final ProductComboItemMapper productComboItemMapper;

    /**
     * 套餐列表（分页）
     */
    @GetMapping
    public Result<PageResult<ComboResponse>> listCombos(ComboQueryRequest queryRequest) {
        PageResult<ProductCombo> comboPage = productComboService.getComboPage(
                queryRequest,
                queryRequest.getComboName(),
                queryRequest.getIsActive()
        );

        List<ComboResponse> responseRecords = comboPage.getRecords().stream()
                .map(this::toComboResponse)
                .toList();
        PageResult<ComboResponse> result = new PageResult<>(
                responseRecords, comboPage.getTotal(),
                comboPage.getPage(), comboPage.getSize()
        );
        return Result.ok(result);
    }

    /**
     * 套餐详情
     */
    @GetMapping("/{id}")
    public Result<ComboResponse> getCombo(@PathVariable Long id) {
        ProductCombo combo = productComboService.getComboById(id);
        return Result.ok(toComboResponse(combo));
    }

    /**
     * 新增套餐
     */
    @PostMapping
    public Result<ComboResponse> createCombo(@Valid @RequestBody ComboCreateRequest request) {
        ProductCombo combo = new ProductCombo();
        combo.setComboName(request.getComboName());
        combo.setComboCode(request.getComboCode());
        combo.setDescription(request.getDescription());
        combo.setComboPrice(request.getComboPrice());
        combo.setImageUrl(request.getImageUrl());
        combo.setIsActive(request.getIsActive());
        combo.setSortOrder(request.getSortOrder());

        Map<Long, Integer> productIdQuantityMap = buildItemIdMap(request.getComboItems());

        ProductCombo created = productComboService.createCombo(combo, productIdQuantityMap);
        log.info("套餐创建成功: id={}, name={}", created.getId(), created.getComboName());
        return Result.ok(toComboResponse(created));
    }

    /**
     * 编辑套餐
     */
    @PutMapping("/{id}")
    public Result<ComboResponse> updateCombo(@PathVariable Long id,
                                              @Valid @RequestBody ComboUpdateRequest request) {
        ProductCombo combo = new ProductCombo();
        combo.setComboName(request.getComboName());
        combo.setComboCode(request.getComboCode());
        combo.setDescription(request.getDescription());
        combo.setComboPrice(request.getComboPrice());
        combo.setImageUrl(request.getImageUrl());
        combo.setIsActive(request.getIsActive());
        combo.setSortOrder(request.getSortOrder());

        Map<Long, Integer> productIdQuantityMap = buildItemIdMap(request.getComboItems());

        ProductCombo updated = productComboService.updateCombo(id, combo, productIdQuantityMap);
        log.info("套餐更新成功: id={}", id);
        return Result.ok(toComboResponse(updated));
    }

    /**
     * 删除套餐
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteCombo(@PathVariable Long id) {
        productComboService.deleteCombo(id);
        log.info("套餐删除成功: id={}", id);
        return Result.ok();
    }

    /**
     * 套餐上下架
     */
    @PatchMapping("/{id}/status")
    public Result<Void> updateComboStatus(@PathVariable Long id,
                                           @RequestBody Map<String, Integer> body) {
        Integer isActive = body.get("isActive");
        productComboService.updateComboStatus(id, isActive);
        log.info("套餐状态更新成功: id={}, isActive={}", id, isActive);
        return Result.ok();
    }

    // ==================== 私有转换方法 ====================

    private ComboResponse toComboResponse(ProductCombo combo) {
        ComboResponse response = new ComboResponse();
        response.setId(combo.getId());
        response.setComboName(combo.getComboName());
        response.setComboCode(combo.getComboCode());
        response.setDescription(combo.getDescription());
        response.setOriginalPrice(combo.getOriginalPrice());
        response.setComboPrice(combo.getComboPrice());
        response.setImageUrl(combo.getImageUrl());
        response.setIsActive(combo.getIsActive());
        response.setIsActiveLabel(getIsActiveLabel(combo.getIsActive()));
        response.setSortOrder(combo.getSortOrder());
        response.setCreatedAt(combo.getCreatedAt());
        response.setUpdatedAt(combo.getUpdatedAt());

        // 查询套餐包含的商品
        response.setItems(getComboItems(combo.getId()));

        return response;
    }

    private List<ComboItemResponse> getComboItems(Long comboId) {
        if (comboId == null) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<ProductComboItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductComboItem::getComboId, comboId);
        List<ProductComboItem> items = productComboItemMapper.selectList(wrapper);

        return items.stream().map(item -> {
            ComboItemResponse resp = new ComboItemResponse();
            resp.setId(item.getId());
            resp.setComboId(item.getComboId());
            resp.setProductId(item.getProductId());
            resp.setProductName(item.getProductName());
            resp.setUnitPrice(item.getUnitPrice());
            resp.setQuantity(item.getQuantity());
            resp.setSubtotal(item.getSubtotal());
            return resp;
        }).toList();
    }

    private Map<Long, Integer> buildItemIdMap(List<ComboItemRequest> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        Map<Long, Integer> map = new LinkedHashMap<>();
        for (ComboItemRequest item : items) {
            map.put(item.getProductId(), item.getQuantity());
        }
        return map;
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
