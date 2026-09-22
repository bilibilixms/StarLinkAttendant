package com.starlink.member.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 充值活动只读查询。
 * <p>
 * 跨模块读取 {@code campaign} 表（充值活动定义在 starlink-module-marketing）。
 * 采用注解 SQL 直查而非新增模块依赖 —— 与本项目既有做法一致
 * （{@code SessionMapper} 即以同样方式跨模块读取 member / tariff_rate）。
 */
@Mapper
public interface RechargeCampaignMapper {

    /**
     * 查询当前<b>生效中</b>的充值赠送活动（campaign_type = 1）。
     * <p>
     * 生效条件：未逻辑删除、状态为 已发布(1) 或 已生效(2)、且当前时间落在活动起止区间内。
     * 若存在多个同时生效，取<b>最新创建</b>的一个（id 倒序）：
     * 便于运营通过「新建活动」覆盖旧活动，而不是被历史活动长期压制。
     *
     * @param now 当前时间
     * @return {campaignId, rulesJson}；无生效活动时返回 null
     */
    @Select("SELECT id AS campaignId, rules AS rulesJson FROM campaign "
            + "WHERE campaign_type = 1 AND deleted_at IS NULL "
            + "AND status IN (1, 2) "
            + "AND start_time <= #{now} AND end_time >= #{now} "
            + "ORDER BY id DESC LIMIT 1")
    Map<String, Object> selectEffectiveRechargeCampaign(@Param("now") LocalDateTime now);
}
