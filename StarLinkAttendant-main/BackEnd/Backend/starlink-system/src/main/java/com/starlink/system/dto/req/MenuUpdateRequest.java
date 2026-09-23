package com.starlink.system.dto.req;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MenuUpdateRequest {

    private Long parentId;

    @Size(max = 64, message = "菜单名称长度不能超过64")
    private String permName;

    @Size(max = 128, message = "权限编码长度不能超过128")
    private String permCode;

    private Byte permType;

    private String icon;

    private String route;

    private Integer sortOrder;
}
