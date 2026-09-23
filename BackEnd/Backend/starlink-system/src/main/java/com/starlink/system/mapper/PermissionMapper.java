package com.starlink.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.starlink.system.entity.Permission;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PermissionMapper extends BaseMapper<Permission> {

    List<Permission> selectByRoleId(@Param("roleId") Long roleId);

    List<Permission> selectByRoleIds(@Param("roleIds") List<Long> roleIds);

    List<Permission> selectTreeByParentId(@Param("parentId") Long parentId);
}
