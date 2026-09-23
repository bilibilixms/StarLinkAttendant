package com.starlink.session.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 机位区域响应。
 *
 */
@Data
public class SeatAreaResponse {

    private Long id;

    /** 区域名称 */
    private String areaName;

    /** 区域标识色 */
    private String areaColor;

    /** 排序 */
    private Integer sortOrder;

    /** 是否启用 */
    private Byte isActive;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** 该区域下的机位列表（座位图场景使用） */
    private List<ComputerResponse> computers;
}
