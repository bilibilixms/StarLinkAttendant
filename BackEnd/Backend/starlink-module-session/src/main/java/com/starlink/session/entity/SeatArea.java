package com.starlink.session.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.starlink.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 机位区域实体。
 *
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("seat_area")
public class SeatArea extends BaseEntity {

    /** 区域名称：如 A区/电竞区/包间 */
    private String areaName;

    /** 区域标识色（十六进制，如 #1890ff） */
    private String areaColor;

    /** 排序 */
    private Integer sortOrder;

    /** 是否启用 */
    private Byte isActive;
}
