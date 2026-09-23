package com.starlink.marketing.service;

import com.starlink.common.util.PageResult;
import com.starlink.marketing.dto.request.CampaignCreateRequest;
import com.starlink.marketing.dto.request.CampaignQueryRequest;
import com.starlink.marketing.dto.request.CampaignUpdateRequest;
import com.starlink.marketing.dto.response.CampaignResponse;

/**
 * 活动服务接口。
 *
 */
public interface CampaignService {

    /**
     * 获取活动列表（分页）。
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<CampaignResponse> getCampaignList(CampaignQueryRequest request);

    /**
     * 获取活动详情。
     *
     * @param id 活动ID
     * @return 活动详情
     */
    CampaignResponse getCampaignById(Long id);

    /**
     * 创建活动。
     *
     * @param request 创建请求
     * @return 创建后的活动
     */
    CampaignResponse createCampaign(CampaignCreateRequest request);

    /**
     * 更新活动。
     *
     * @param id      活动ID
     * @param request 更新请求
     * @return 更新后的活动
     */
    CampaignResponse updateCampaign(Long id, CampaignUpdateRequest request);

    /**
     * 删除活动（软删除）。
     *
     * @param id 活动ID
     */
    void deleteCampaign(Long id);

    /**
     * 修改活动状态（发布、结束、下架等），不触发字段校验。
     *
     * @param id     活动ID
     * @param status 新状态
     * @return 更新后的活动
     */
    CampaignResponse updateCampaignStatus(Long id, Integer status);

    /**
     * 获取充值促销活动列表（分页）。
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<CampaignResponse> getRechargePromoList(CampaignQueryRequest request);

    /**
     * 删除充值促销活动（仅允许删除已下架状态的活动）。
     *
     * @param id 活动ID
     */
    void deleteRechargePromo(Long id);

    /**
     * 定时自动下架已到结束时间的活动。
     */
    void processCampaignStatus();
}
