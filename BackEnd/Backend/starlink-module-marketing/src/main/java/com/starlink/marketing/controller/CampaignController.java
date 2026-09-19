package com.starlink.marketing.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import com.starlink.common.result.Result;
import com.starlink.common.util.PageResult;
import com.starlink.marketing.dto.request.CampaignCreateRequest;
import com.starlink.marketing.dto.request.CampaignQueryRequest;
import com.starlink.marketing.dto.request.CampaignStatusRequest;
import com.starlink.marketing.dto.request.CampaignUpdateRequest;
import com.starlink.marketing.dto.response.CampaignResponse;
import com.starlink.marketing.service.CampaignService;

import java.util.Map;

/**
 * 活动管理控制器。
 * <p>
 * 提供营销活动的CRUD操作，包括充值促销、限时活动等。
 *
 */
@Slf4j
@RestController
@RequestMapping("/api/marketing")
@RequiredArgsConstructor
public class CampaignController {

    private final CampaignService campaignService;

    // ==================== 通用活动接口 ====================

    /**
     * 获取活动列表（分页）。
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/campaigns")
    public Result<PageResult<CampaignResponse>> getCampaignList(CampaignQueryRequest request) {
        log.info("查询活动列表，条件: {}", request);
        PageResult<CampaignResponse> result = campaignService.getCampaignList(request);
        return Result.ok(result, "查询成功");
    }

    /**
     * 获取活动详情。
     *
     * @param id 活动ID
     * @return 活动详情
     */
    @GetMapping("/campaigns/{id}")
    public Result<CampaignResponse> getCampaignById(@PathVariable Long id) {
        log.info("查询活动详情，ID: {}", id);
        CampaignResponse result = campaignService.getCampaignById(id);
        return Result.ok(result, "查询成功");
    }

    /**
     * 创建活动。
     *
     * @param request 创建请求
     * @return 创建后的活动
     */
    @PostMapping("/campaigns")
    public Result<CampaignResponse> createCampaign(@Valid @RequestBody CampaignCreateRequest request) {
        log.info("创建活动，请求: {}", request);
        CampaignResponse result = campaignService.createCampaign(request);
        return Result.ok(result, "创建成功");
    }

    /**
     * 更新活动。
     *
     * @param id      活动ID
     * @param request 更新请求
     * @return 更新后的活动
     */
    @PutMapping("/campaigns/{id}")
    public Result<CampaignResponse> updateCampaign(
            @PathVariable Long id,
            @Valid @RequestBody CampaignUpdateRequest request) {
        log.info("更新活动，ID: {}, 请求: {}", id, request);
        CampaignResponse result = campaignService.updateCampaign(id, request);
        return Result.ok(result, "更新成功");
    }

    /**
     * 修改活动状态（发布、结束、下架等）。
     *
     * @param id      活动ID
     * @param request 状态请求
     * @return 更新后的活动
     */
    @PatchMapping("/campaigns/{id}/status")
    public Result<CampaignResponse> updateCampaignStatus(
            @PathVariable Long id,
            @Valid @RequestBody CampaignStatusRequest request) {
        log.info("修改活动状态，ID: {}, 新状态: {}", id, request.getStatus());
        CampaignResponse result = campaignService.updateCampaignStatus(id, request.getStatus());
        return Result.ok(result, "状态更新成功");
    }

    /**
     * 删除活动（软删除）。
     *
     * @param id 活动ID
     * @return 操作结果
     */
    @DeleteMapping("/campaigns/{id}")
    public Result<Void> deleteCampaign(@PathVariable Long id) {
        log.info("删除活动，ID: {}", id);
        campaignService.deleteCampaign(id);
        return Result.ok(null, "删除成功");
    }

    // ==================== 充值促销接口 ====================

    /**
     * 获取充值促销活动列表（分页）。
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/recharge-promo")
    public Result<PageResult<CampaignResponse>> getRechargePromoList(CampaignQueryRequest request) {
        log.info("查询充值促销活动列表，条件: {}", request);
        PageResult<CampaignResponse> result = campaignService.getRechargePromoList(request);
        return Result.ok(result, "查询成功");
    }

    /**
     * 创建充值促销活动。
     *
     * @param request 创建请求
     * @return 创建后的活动
     */
    @PostMapping("/recharge-promo")
    public Result<CampaignResponse> createRechargePromo(@Valid @RequestBody CampaignCreateRequest request) {
        log.info("创建充值促销活动，请求: {}", request);
        request.setCampaignType(1);
        CampaignResponse result = campaignService.createCampaign(request);
        return Result.ok(result, "创建成功");
    }

    /**
     * 更新充值促销活动。
     *
     * @param id      活动ID
     * @param request 更新请求
     * @return 更新后的活动
     */
    @PutMapping("/recharge-promo/{id}")
    public Result<CampaignResponse> updateRechargePromo(
            @PathVariable Long id,
            @Valid @RequestBody CampaignUpdateRequest request) {
        log.info("更新充值促销活动，ID: {}, 请求: {}", id, request);
        CampaignResponse result = campaignService.updateCampaign(id, request);
        return Result.ok(result, "更新成功");
    }

    /**
     * 删除充值促销活动（仅允许删除已下架状态的活动）。
     *
     * @param id 活动ID
     * @return 操作结果
     */
    @DeleteMapping("/recharge-promo/{id}")
    public Result<Map<String, Object>> deleteRechargePromo(@PathVariable Long id) {
        log.info("删除充值促销活动，ID: {}", id);
        campaignService.deleteRechargePromo(id);
        return Result.ok(Map.of("id", id), "删除成功");
    }

}
