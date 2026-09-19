package com.starlink.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.starlink.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("employee")
public class Employee extends BaseEntity {

    private String employeeNo;

    private String realName;

    private String phone;

    private String passwordHash;

    private String position;

    private Byte employmentType;

    private LocalDate hireDate;

    private LocalDate resignDate;

    private Byte status;

    private Byte isActive;

    private LocalDateTime lastLoginAt;
}
