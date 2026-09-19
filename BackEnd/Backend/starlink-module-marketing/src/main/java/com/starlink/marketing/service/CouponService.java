package com.starlink.marketing.service;

import com.starlink.common.util.PageResult;
import com.starlink.marketing.dto.request.CouponIssueRequest;
import com.starlink.marketing.dto.request.CouponRecordsQueryRequest;
import com.starlink.marketing.dto.response.CouponResponse;
import com.starlink.marketing.dto.response.CouponTemplateResponse;
import com.starlink.marketing.dto.request.CouponTemplateCreateRequest;
import com.starlink.marketing.dto.request.CouponTemplateQueryRequest;
import com.starlink.marketing.dto.request.CouponTemplateUpdateRequest;

/**
 * 优惠券服务接口。
 *
 */
public interface CouponService {

    /**
     * 获取优惠券模板列表（分页）。
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<CouponTemplateResponse> getCouponTemplateList(CouponTemplateQueryRequest request);

    /**
     * 获取优惠券模板详情。
     *
     * @param id 模板ID
     * @return 模板详情
     */
    CouponTemplateResponse getCouponTemplateById(Long id);

    /**
     * 创建优惠券模板。
     *
     * @param request 创建请求
     * @return 创建后的模板
     */
    CouponTemplateResponse createCouponTemplate(CouponTemplateCreateRequest request);

    /**
     * 更新优惠券模板。
     *
     * @param id      模板ID
     * @param request 更新请求
     * @return 更新后的模板
     */
    CouponTemplateResponse updateCouponTemplate(Long id, CouponTemplateUpdateRequest request);

    /**
     * 删除优惠券模板（软删除）。
     *
     * @param id 模板ID
     */
    void deleteCouponTemplate(Long id);

    /**
     * 发放优惠券。
     *
     * @param request 发放请求
     * @return 发放数量
     */
    Integer issueCoupon(CouponIssueRequest request);

    /**
     * 核销优惠券。
     *
     * @param couponId 优惠券ID
     * @param orderId  关联订单ID
     * @return 核销后的优惠券信息
     */
    CouponResponse redeemCoupon(Long couponId, Long orderId);

    /**
     * 处理过期优惠券（定时任务）。
     * 将超过过期时间且状态为"未使用"的优惠券标记为"已过期"。
     *
     * @return 处理数量
     */
    int processExpiredCoupons();

    /**
     * 获取优惠券核销记录（分页）。
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<CouponResponse> getCouponRecords(CouponRecordsQueryRequest request);
}
