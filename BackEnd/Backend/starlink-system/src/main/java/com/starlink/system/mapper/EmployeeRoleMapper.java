package com.starlink.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.starlink.system.entity.EmployeeRole;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface EmployeeRoleMapper extends BaseMapper<EmployeeRole> {

    void deleteByEmployeeId(@Param("employeeId") Long employeeId);

    void deleteByRoleId(@Param("roleId") Long roleId);

    List<Long> selectRoleIdsByEmployeeId(@Param("employeeId") Long employeeId);

    void batchInsert(@Param("employeeId") Long employeeId, @Param("roleIds") List<Long> roleIds);
}
