package com.starlink.system.dto.req;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RoleUpdateRequest {

    @Size(max = 32, message = "角色名称长度不能超过32")
    private String roleName;

    private String description;

    private Integer sortOrder;

    private Byte isActive;
}
