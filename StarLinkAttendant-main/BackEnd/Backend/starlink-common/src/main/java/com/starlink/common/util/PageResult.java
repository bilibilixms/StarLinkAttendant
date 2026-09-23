package com.starlink.common.util;

import lombok.Data;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 分页响应结果。
 * <p>
 * 对应 API 规范中的分页响应格式：
 * <pre>
 * {
 *   "records": [],
 *   "total": 128,
 *   "page": 1,
 *   "size": 10,
 *   "pages": 13
 * }
 * </pre>
 *
 * @param <T> 记录类型
 */
@Data
public class PageResult<T> {

    /** 当前页数据列表 */
    private List<T> records;

    /** 总记录数 */
    private long total;

    /** 当前页码 */
    private int page;

    /** 每页条数 */
    private int size;

    /** 总页数 */
    private int pages;

    public PageResult() {
    }

    public PageResult(List<T> records, long total, int page, int size) {
        this.records = records;
        this.total = total;
        this.page = page;
        this.size = size;
        this.pages = size > 0 ? (int) Math.ceil((double) total / size) : 0;
    }

    /**
     * 空分页结果。
     */
    public static <T> PageResult<T> empty(int page, int size) {
        return new PageResult<>(Collections.emptyList(), 0, page, size);
    }

    /**
     * 从 MyBatis-Plus 的 IPage 构建 PageResult。
     */
    public static <T> PageResult<T> from(com.baomidou.mybatisplus.core.metadata.IPage<T> mpPage) {
        PageResult<T> result = new PageResult<>();
        result.setRecords(mpPage.getRecords());
        result.setTotal(mpPage.getTotal());
        result.setPage((int) mpPage.getCurrent());
        result.setSize((int) mpPage.getSize());
        result.setPages((int) mpPage.getPages());
        return result;
    }

    /**
     * 从 MyBatis-Plus 的 IPage 构建 PageResult（of 别名）。
     */
    public static <T> PageResult<T> of(com.baomidou.mybatisplus.core.metadata.IPage<T> mpPage) {
        return from(mpPage);
    }

    /**
     * 从 MyBatis-Plus 的 IPage 构建 PageResult，并对每条记录应用转换函数。
     */
    public static <S, T> PageResult<T> of(com.baomidou.mybatisplus.core.metadata.IPage<S> mpPage, Function<S, T> converter) {
        List<T> converted = mpPage.getRecords().stream()
                .map(converter)
                .collect(Collectors.toList());
        PageResult<T> result = new PageResult<>();
        result.setRecords(converted);
        result.setTotal(mpPage.getTotal());
        result.setPage((int) mpPage.getCurrent());
        result.setSize((int) mpPage.getSize());
        result.setPages((int) mpPage.getPages());
        return result;
    }

    /**
     * 对当前分页结果中的每条记录应用转换函数，返回新的分页结果。
     *
     * @param converter 转换函数
     * @param <R>       目标类型
     * @return 转换后的分页结果
     */
    public <R> PageResult<R> map(Function<T, R> converter) {
        List<R> converted = this.records == null
                ? Collections.emptyList()
                : this.records.stream().map(converter).collect(Collectors.toList());
        return new PageResult<>(converted, this.total, this.page, this.size);
    }
}
