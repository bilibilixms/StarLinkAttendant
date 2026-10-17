package com.starlink.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.starlink.marketing.entity.CouponTemplate;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 优惠券模板表Mapper接口。
 *
 */
public interface CouponTemplateMapper extends BaseMapper<CouponTemplate> {
    @Select("SELECT * FROM coupon_template WHERE id = #{id} AND deleted_at IS NULL FOR UPDATE")
    CouponTemplate selectByIdForUpdate(@Param("id") Long id);
}
