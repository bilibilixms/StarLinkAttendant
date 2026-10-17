package com.starlink.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.starlink.marketing.entity.Campaign;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 活动表Mapper接口。
 *
 */
public interface CampaignMapper extends BaseMapper<Campaign> {
    @Select("SELECT * FROM campaign WHERE id = #{id} AND deleted_at IS NULL FOR UPDATE")
    Campaign selectByIdForUpdate(@Param("id") Long id);
}
