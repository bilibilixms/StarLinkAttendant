package com.starlink.system.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RoleCreateRequest {

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 32, message = "角色名称长度不能超过32")
    private String roleName;

    @NotBlank(message = "角色编码不能为空")
    @Size(max = 32, message = "角色编码长度不能超过32")
    private String roleCode;

    private String description;

    private Integer sortOrder;

    private Byte isActive = 1;
}
