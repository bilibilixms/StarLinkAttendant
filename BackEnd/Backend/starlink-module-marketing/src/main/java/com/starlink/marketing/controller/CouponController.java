package com.starlink.marketing.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import com.starlink.common.result.Result;
import com.starlink.common.util.PageResult;
import com.starlink.marketing.dto.request.CouponIssueRequest;
import com.starlink.marketing.dto.request.CouponRecordsQueryRequest;
import com.starlink.marketing.dto.request.CouponTemplateCreateRequest;
import com.starlink.marketing.dto.request.CouponTemplateQueryRequest;
import com.starlink.marketing.dto.request.CouponTemplateUpdateRequest;
import com.starlink.marketing.dto.response.CouponResponse;
import com.starlink.marketing.dto.response.CouponTemplateResponse;
import com.starlink.marketing.service.CouponService;

/**
 * 优惠券管理控制器。
 * <p>
 * 提供优惠券模板的CRUD操作和优惠券发放、核销记录查询等接口。
 *
 */
@Slf4j
@RestController
@RequestMapping("/api/marketing/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    /**
     * 获取优惠券模板列表（分页）。
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping
    public Result<PageResult<CouponTemplateResponse>> getCouponTemplateList(CouponTemplateQueryRequest request) {
        log.info("查询优惠券模板列表，条件: {}", request);
        PageResult<CouponTemplateResponse> result = couponService.getCouponTemplateList(request);
        return Result.ok(result, "查询成功");
    }

    /**
     * 获取优惠券模板详情。
     *
     * @param id 模板ID
     * @return 模板详情
     */
    @GetMapping("/{id}")
    public Result<CouponTemplateResponse> getCouponTemplateById(@PathVariable Long id) {
        log.info("查询优惠券模板详情，ID: {}", id);
        CouponTemplateResponse result = couponService.getCouponTemplateById(id);
        return Result.ok(result, "查询成功");
    }

    /**
     * 创建优惠券模板。
     *
     * @param request 创建请求
     * @return 创建后的模板
     */
    @PostMapping
    public Result<CouponTemplateResponse> createCouponTemplate(@Valid @RequestBody CouponTemplateCreateRequest request) {
        log.info("创建优惠券模板，请求: {}", request);
        CouponTemplateResponse result = couponService.createCouponTemplate(request);
        return Result.ok(result, "创建成功");
    }

    /**
     * 更新优惠券模板。
     *
     * @param id      模板ID
     * @param request 更新请求
     * @return 更新后的模板
     */
    @PutMapping("/{id}")
    public Result<CouponTemplateResponse> updateCouponTemplate(
            @PathVariable Long id,
            @Valid @RequestBody CouponTemplateUpdateRequest request) {
        log.info("更新优惠券模板，ID: {}, 请求: {}", id, request);
        CouponTemplateResponse result = couponService.updateCouponTemplate(id, request);
        return Result.ok(result, "更新成功");
    }

    /**
     * 删除优惠券模板（软删除）。
     *
     * @param id 模板ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteCouponTemplate(@PathVariable Long id) {
        log.info("删除优惠券模板，ID: {}", id);
        couponService.deleteCouponTemplate(id);
        return Result.ok(null, "删除成功");
    }

    /**
     * 发放优惠券。
     *
     * @param id      模板ID
     * @param request 发放请求
     * @return 发放数量
     */
    @PostMapping("/{id}/issue")
    public Result<Integer> issueCoupon(
            @PathVariable Long id,
            @RequestBody CouponIssueRequest request) {
        log.info("发放优惠券，模板ID: {}, 请求: {}", id, request);
        request.setTemplateId(id);
        Integer result = couponService.issueCoupon(request);
        return Result.ok(result, "发放成功");
    }

    /**
     * 核销优惠券。
     *
     * @param couponId 优惠券ID
     * @param orderId  关联订单ID
     * @return 核销后的优惠券信息
     */
    @PostMapping("/{couponId}/redeem")
    public Result<CouponResponse> redeemCoupon(
            @PathVariable Long couponId,
            @RequestParam Long orderId) {
        log.info("核销优惠券，券ID: {}, 订单ID: {}", couponId, orderId);
        CouponResponse result = couponService.redeemCoupon(couponId, orderId);
        return Result.ok(result, "核销成功");
    }

    /**
     * 获取优惠券核销记录（分页）。
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/records")
    public Result<PageResult<CouponResponse>> getCouponRecords(CouponRecordsQueryRequest request) {
        log.info("查询优惠券核销记录，条件: {}", request);
        PageResult<CouponResponse> result = couponService.getCouponRecords(request);
        return Result.ok(result, "查询成功");
    }
}
