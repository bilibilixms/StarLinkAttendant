package com.starlink.system.dto.resp;

import lombok.Data;

import java.util.List;

@Data
public class MenuResponse {

    private Long id;

    private Long parentId;

    private String permName;

    private String permCode;

    private Byte permType;

    private String icon;

    private String route;

    private Integer sortOrder;

    private List<MenuResponse> children;
}
