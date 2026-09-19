package com.starlink.member.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.starlink.member.entity.MemberLevel;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MemberLevelMapper extends BaseMapper<MemberLevel> {

    MemberLevel selectByGrowth(@Param("growth") Integer growth);

    List<MemberLevel> selectAllOrderByLevel();
}