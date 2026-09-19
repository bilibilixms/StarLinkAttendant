package com.starlink.system.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MenuCreateRequest {

    private Long parentId;

    @NotBlank(message = "菜单名称不能为空")
    @Size(max = 64, message = "菜单名称长度不能超过64")
    private String permName;

    @NotBlank(message = "权限编码不能为空")
    @Size(max = 128, message = "权限编码长度不能超过128")
    private String permCode;

    private Byte permType = 1;

    private String icon;

    private String route;

    private Integer sortOrder = 0;
}
