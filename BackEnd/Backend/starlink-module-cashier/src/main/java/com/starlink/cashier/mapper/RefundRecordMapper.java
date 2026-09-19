package com.starlink.cashier.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.starlink.cashier.dto.response.RefundResponse;
import com.starlink.cashier.entity.RefundRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RefundRecordMapper extends BaseMapper<RefundRecord> {

    IPage<RefundResponse> selectRefundPage(Page<RefundResponse> page,
                                           @Param("orderId") Long orderId,
                                           @Param("status") Integer status,
                                           @Param("startTime") String startTime,
                                           @Param("endTime") String endTime);

    List<RefundRecord> selectByOrderId(@Param("orderId") Long orderId);
}
