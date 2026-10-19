package com.starlink.cashier.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.starlink.cashier.dto.response.OrderDetailResponse;
import com.starlink.cashier.dto.response.OrderResponse;
import com.starlink.cashier.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    @Select("SELECT * FROM orders WHERE id = #{id} AND deleted_at IS NULL FOR UPDATE")
    Order selectByIdForUpdate(@Param("id") Long id);

    IPage<OrderResponse> selectOrderPage(Page<OrderResponse> page,
                                         @Param("orderNo") String orderNo,
                                         @Param("orderType") Integer orderType,
                                         @Param("status") Integer status,
                                         @Param("statusList") List<Integer> statusList,
                                         @Param("memberId") Long memberId,
                                         @Param("startTime") String startTime,
                                         @Param("endTime") String endTime);

    OrderDetailResponse selectDetailById(@Param("id") Long id);
}
