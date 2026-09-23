package com.starlink.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.starlink.system.dto.resp.UserResponse;
import com.starlink.system.entity.Employee;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface EmployeeMapper extends BaseMapper<Employee> {

    IPage<UserResponse> selectUserPage(Page<UserResponse> page,
                                       @Param("realName") String realName,
                                       @Param("phone") String phone,
                                       @Param("status") Byte status);

    Employee selectByPhone(@Param("phone") String phone);

    Employee selectByEmployeeNo(@Param("employeeNo") String employeeNo);
}
