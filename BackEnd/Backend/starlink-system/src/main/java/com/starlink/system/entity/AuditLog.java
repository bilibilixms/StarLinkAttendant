package com.starlink.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.starlink.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("audit_log")
public class AuditLog extends BaseEntity {

    private Long operatorId;

    private String operatorName;

    private String bizType;

    private Long bizId;

    private String action;

    private String detail;

    private String ipAddress;

    private String userAgent;

    private String requestId;
}
