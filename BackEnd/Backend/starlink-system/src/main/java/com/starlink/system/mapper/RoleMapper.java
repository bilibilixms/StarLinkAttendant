package com.starlink.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.starlink.system.entity.Role;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RoleMapper extends BaseMapper<Role> {

    Role selectByCode(@Param("roleCode") String roleCode);

    List<Role> selectByEmployeeId(@Param("employeeId") Long employeeId);
}
