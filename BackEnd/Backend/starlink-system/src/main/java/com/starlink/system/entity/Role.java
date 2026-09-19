package com.starlink.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.starlink.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("role")
public class Role extends BaseEntity {

    private String roleName;

    private String roleCode;

    private String description;

    private Byte isSystem;

    private Integer sortOrder;

    private Byte isActive;
}
