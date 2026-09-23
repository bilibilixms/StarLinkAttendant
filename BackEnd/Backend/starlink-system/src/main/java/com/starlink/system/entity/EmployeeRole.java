package com.starlink.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.starlink.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("employee_role")
public class EmployeeRole extends BaseEntity {

    private Long employeeId;

    private Long roleId;
}
