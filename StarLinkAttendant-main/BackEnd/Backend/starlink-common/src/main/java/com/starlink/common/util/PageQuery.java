package com.starlink.common.util;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 分页查询请求参数。
 * <p>
 * Controller 方法使用此类接收前端分页参数，与 API 规范中的分页请求保持一致：
 * <pre>
 * {
 *   "page": 1,
 *   "size": 10,
 *   "sortField": "createdAt",
 *   "sortOrder": "desc"
 * }
 * </pre>
 *
 */
@Data
public class PageQuery {

    /** 页码（从 1 开始） */
    @Min(value = 1, message = "页码最小为 1")
    private Integer page = 1;

    /** 每页条数（最大 100） */
    @Min(value = 1, message = "每页条数最小为 1")
    @Max(value = 100, message = "每页条数最大为 100")
    private Integer size = 10;

    /** 排序字段（camelCase） */
    private String sortField;

    /** 排序方向：asc / desc */
    private String sortOrder = "desc";

    /**
     * 计算 MyBatis-Plus 的 offset（内部使用）。
     */
    public long getOffset() {
        return (long) (page - 1) * size;
    }
}
