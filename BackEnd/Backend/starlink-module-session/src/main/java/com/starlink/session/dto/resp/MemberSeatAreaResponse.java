package com.starlink.session.dto.resp;

import lombok.Data;

import java.util.List;

/**
 * 会员端机位区域响应。
 * <p>
 * 隐藏费率方案 / 设备 MAC / IP 等敏感字段，仅暴露会员可见的展示信息。
 * 状态语义与 Web 管理端 SeatAreaResponse 一致，由活跃会话驱动。
 *
 */
@Data
public class MemberSeatAreaResponse {

    private Long id;

    /** 区域名称 */
    private String areaName;

    /** 区域标识色 */
    private String areaColor;

    /** 排序 */
    private Integer sortOrder;

    /** 空闲机位数（状态=0 且启用） */
    private Integer freeCount;

    /** 总机位数（启用） */
    private Integer totalCount;

    /** 区域下机位列表（仅座位图接口返回，区域摘要接口为 null） */
    private List<MemberComputerResponse> computers;
}
