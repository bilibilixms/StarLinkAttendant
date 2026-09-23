package com.starlink.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.starlink.system.entity.RolePermission;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RolePermissionMapper extends BaseMapper<RolePermission> {

    void deleteByRoleId(@Param("roleId") Long roleId);

    void batchInsert(@Param("roleId") Long roleId, @Param("permissionIds") List<Long> permissionIds);
}
