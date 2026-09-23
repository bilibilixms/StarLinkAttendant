package com.starlink.system.dto.resp;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class UserResponse {

    private Long id;

    private String employeeNo;

    private String realName;

    private String phone;

    private String position;

    private Byte employmentType;

    private String employmentTypeLabel;

    private LocalDate hireDate;

    private LocalDate resignDate;

    private Byte status;

    private String statusLabel;

    private Byte isActive;

    private LocalDateTime lastLoginAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
