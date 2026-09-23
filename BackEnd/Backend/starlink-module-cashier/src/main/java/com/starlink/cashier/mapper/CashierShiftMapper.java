package com.starlink.cashier.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.starlink.cashier.dto.response.ShiftResponse;
import com.starlink.cashier.entity.CashierShift;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CashierShiftMapper extends BaseMapper<CashierShift> {

    CashierShift selectActiveShift(@Param("employeeId") Long employeeId);

    IPage<ShiftResponse> selectShiftPage(Page<ShiftResponse> page,
                                         @Param("employeeId") Long employeeId,
                                         @Param("status") Integer status);
}
