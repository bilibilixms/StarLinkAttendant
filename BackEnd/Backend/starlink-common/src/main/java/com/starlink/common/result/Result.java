package com.starlink.common.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 统一 API 响应封装。
 * <p>
 * 所有接口返回此结构，前端据此判断业务成功/失败。
 * <pre>
 * {
 *   "code": 0,
 *   "message": "操作成功",
 *   "data": { ... },
 *   "timestamp": "2026-07-13 14:30:00"
 * }
 * </pre>
 *
 * @param <T> 业务数据类型
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> {

    /** 业务状态码，0 = 成功，非 0 = 失败 */
    private int code;

    /** 提示信息 */
    private String message;

    /** 业务数据（失败时为 null） */
    private T data;

    /** 服务器时间戳 */
    private String timestamp;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Result() {
        this.timestamp = LocalDateTime.now().format(FORMATTER);
    }

    // ==================== 成功 ====================

    public static <T> Result<T> ok() {
        Result<T> result = new Result<>();
        result.code = ErrorCode.SUCCESS.getCode();
        result.message = ErrorCode.SUCCESS.getMessage();
        return result;
    }

    public static <T> Result<T> ok(T data) {
        Result<T> result = new Result<>();
        result.code = ErrorCode.SUCCESS.getCode();
        result.message = ErrorCode.SUCCESS.getMessage();
        result.data = data;
        return result;
    }

    public static <T> Result<T> ok(T data, String message) {
        Result<T> result = new Result<>();
        result.code = ErrorCode.SUCCESS.getCode();
        result.message = message;
        result.data = data;
        return result;
    }

    // ==================== 失败 ====================

    public static <T> Result<T> fail(ErrorCode errorCode) {
        Result<T> result = new Result<>();
        result.code = errorCode.getCode();
        result.message = errorCode.getMessage();
        return result;
    }

    public static <T> Result<T> fail(ErrorCode errorCode, String message) {
        Result<T> result = new Result<>();
        result.code = errorCode.getCode();
        result.message = message;
        return result;
    }

    public static <T> Result<T> fail(int code, String message) {
        Result<T> result = new Result<>();
        result.code = code;
        result.message = message;
        return result;
    }

    // ==================== 便捷判断 ====================

    public boolean isSuccess() {
        return this.code == ErrorCode.SUCCESS.getCode();
    }
}
