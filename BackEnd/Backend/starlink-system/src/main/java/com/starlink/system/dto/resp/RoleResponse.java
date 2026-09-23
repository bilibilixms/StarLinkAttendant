package com.starlink.system.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RoleResponse {

    private Long id;

    private String roleName;

    private String roleCode;

    private String description;

    private Byte isSystem;

    private Integer sortOrder;

    private Byte isActive;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
