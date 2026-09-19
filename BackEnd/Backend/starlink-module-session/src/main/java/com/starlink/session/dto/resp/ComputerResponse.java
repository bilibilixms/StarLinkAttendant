package com.starlink.session.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 机位响应。
 *
 */
@Data
public class ComputerResponse {

    private Long id;

    /** 所属区域 ID */
    private Long areaId;

    /** 区域名称 */
    private String areaName;

    /** 机位编号 */
    private String computerNo;

    /** 设备名称 */
    private String computerName;

    /** 座位标签 */
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

    /** 是否在线 */
    private Byte isOnline;

    /** 运营状态：0-空闲 1-使用中 2-锁定 3-维修 4-关机 */
    private Byte status;

    /** 状态标签 */
    private String statusLabel;

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

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
