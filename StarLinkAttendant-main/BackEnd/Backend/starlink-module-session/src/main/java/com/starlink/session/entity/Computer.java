package com.starlink.session.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.starlink.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 终端设备（机位）实体。
 *
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("computer")
public class Computer extends BaseEntity {

    /** 所属区域 ID */
    private Long areaId;

    /** 机位编号，如 A-001 */
    private String computerNo;

    /** 设备名称 */
    private String computerName;

    /** 座位标签（靠窗/双人/电竞椅） */
    private String seatLabel;

    /** 设备类型：1-普通 PC 2-电竞 PC 3-包间 4-PS5/主机 */
    private Byte deviceType;

    /** CPU */
    private String cpu;

    /** GPU */
    private String gpu;

    /** 内存 */
    private String memory;

    /** 屏幕尺寸 */
    private String screenSize;

    /** MAC 地址 */
    private String macAddress;

    /** IP 地址 */
    private String ipAddress;

    /** 是否在线（心跳上报） */
    private Byte isOnline;

    /** 运营状态：0-空闲 1-使用中 2-锁定 3-维修 4-关机 */
    private Byte status;

    /** 当前费率方案 ID */
    private Long tariffPlanId;

    /** 座位图排序 */
    private Integer sortOrder;

    /** 座位图 X 坐标 */
    private Integer posX;

    /** 座位图 Y 坐标 */
    private Integer posY;

    /** 是否启用 */
    private Byte isActive;
}
