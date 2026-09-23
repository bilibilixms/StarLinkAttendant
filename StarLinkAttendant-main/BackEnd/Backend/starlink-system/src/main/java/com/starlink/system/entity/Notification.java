package com.starlink.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.starlink.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("notification")
public class Notification extends BaseEntity {

    private Byte notifyType;

    private String title;

    private String content;

    private Byte targetType;

    private String targetIds;

    private Byte isRead;

    private LocalDateTime publishedAt;

    private LocalDateTime expiredAt;
}
