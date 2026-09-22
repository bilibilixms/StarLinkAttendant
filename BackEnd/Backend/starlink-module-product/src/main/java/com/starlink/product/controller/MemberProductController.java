package com.starlink.product.controller;

import com.starlink.common.result.Result;
import com.starlink.product.dto.resp.HotProductResponse;
import com.starlink.product.dto.resp.MemberCategoryResponse;
import com.starlink.product.dto.resp.MemberProductDetailResponse;
import com.starlink.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/member/products")
@RequiredArgsConstructor
public class MemberProductController {

    private final ProductService productService;

    /**
     * 小程序「热门商品」：按累计销量倒序返回前 limit 个上架商品。
     * 公开接口（未登录也可在首页查看）。
     */
    @GetMapping("/hot")
    public Result<List<HotProductResponse>> hotProducts(@RequestParam(defaultValue = "6") int limit) {
        int safe = Math.max(1, Math.min(limit, 50));
        return Result.ok(productService.listHotProducts(safe));
    }

    /**
     * 小程序热门商品「详情」：按 id 取上架商品详情。
     * 公开接口（与热门列表一致，未登录可见）。
     */
    @GetMapping("/hot/{id}")
    public Result<MemberProductDetailResponse> hotProductDetail(@PathVariable("id") Long id) {
        return Result.ok(productService.getMemberProductDetail(id));
    }

    /**
     * 小程序自助点餐「商品列表」：取真实商品表全部上架商品（可按分类过滤）。
     * 公开接口，与热门列表保持一致。
     */
    @GetMapping
    public Result<List<MemberProductDetailResponse>> products(
            @RequestParam(required = false) Long categoryId) {
        return Result.ok(productService.listMemberProducts(categoryId));
    }

    /**
     * 小程序自助点餐「商品分类」。
     */
    @GetMapping("/categories")
    public Result<List<MemberCategoryResponse>> productCategories() {
        return Result.ok(productService.listMemberCategories());
    }

    /**
     * 小程序自助点餐「商品详情」：与热门详情逻辑一致（真实商品表）。
     */
    @GetMapping("/{id}")
    public Result<MemberProductDetailResponse> productDetail(@PathVariable("id") Long id) {
        return Result.ok(productService.getMemberProductDetail(id));
    }
}