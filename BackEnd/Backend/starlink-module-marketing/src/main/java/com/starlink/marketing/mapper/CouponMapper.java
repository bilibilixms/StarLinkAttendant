package com.starlink.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.starlink.marketing.entity.Coupon;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

/**
 * 优惠券实例表Mapper接口。
 *
 */
public interface CouponMapper extends BaseMapper<Coupon> {
    @Select("SELECT * FROM coupon WHERE id = #{id} AND deleted_at IS NULL FOR UPDATE")
    Coupon selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT id, member_id AS memberId FROM orders WHERE id = #{id} AND deleted_at IS NULL")
    Map<String, Object> selectOrderMember(@Param("id") Long id);
}
