package com.starlink.member.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.starlink.member.dto.resp.PointsRecordResponse;
import com.starlink.member.entity.MemberPointsLog;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MemberPointsLogMapper extends BaseMapper<MemberPointsLog> {

    IPage<PointsRecordResponse> selectPointsPage(Page<PointsRecordResponse> page,
                                                  @Param("memberId") Long memberId,
                                                  @Param("bizType") Byte bizType,
                                                  @Param("startTime") String startTime,
                                                  @Param("endTime") String endTime);
}