package com.starlink.member.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.starlink.member.dto.resp.RechargeRecordResponse;
import com.starlink.member.entity.MemberRecharge;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MemberRechargeMapper extends BaseMapper<MemberRecharge> {

    IPage<RechargeRecordResponse> selectRechargePage(Page<RechargeRecordResponse> page,
                                                      @Param("memberId") Long memberId,
                                                      @Param("startTime") String startTime,
                                                      @Param("endTime") String endTime,
                                                      @Param("status") Byte status);

    RechargeRecordResponse selectRechargeDetail(@Param("id") Long id);
}