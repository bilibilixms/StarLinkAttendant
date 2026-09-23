package com.starlink.cashier.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.starlink.cashier.entity.PaymentRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PaymentRecordMapper extends BaseMapper<PaymentRecord> {

    PaymentRecord selectByOrderId(@Param("orderId") Long orderId);

    List<PaymentRecord> selectByOrderIdList(@Param("orderIds") List<Long> orderIds);
}
