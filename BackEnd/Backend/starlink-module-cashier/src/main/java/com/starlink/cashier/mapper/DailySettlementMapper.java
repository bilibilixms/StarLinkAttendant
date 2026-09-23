package com.starlink.cashier.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.starlink.cashier.dto.response.SettlementResponse;
import com.starlink.cashier.entity.DailySettlement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

@Mapper
public interface DailySettlementMapper extends BaseMapper<DailySettlement> {

    DailySettlement selectByDate(@Param("settleDate") LocalDate settleDate);

    IPage<SettlementResponse> selectSettlementPage(Page<SettlementResponse> page,
                                                   @Param("startTime") String startTime,
                                                   @Param("endTime") String endTime,
                                                   @Param("status") Integer status);
}
