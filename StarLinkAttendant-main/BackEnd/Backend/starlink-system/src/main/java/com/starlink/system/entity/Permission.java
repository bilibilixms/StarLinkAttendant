package com.starlink.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.starlink.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("permission")
public class Permission extends BaseEntity {

    private Long parentId;

    private String permName;

    private String permCode;

    private Byte permType;

    private String icon;

    private String route;

    private Integer sortOrder;
}
