package com.starlink.common.exception;

import com.starlink.common.result.ErrorCode;
import com.starlink.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局异常处理器。
 * <p>
 * 统一捕获异常并转换为标准 {@link Result} 响应，避免将堆栈信息暴露给前端。
 *
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ==================== 业务异常 ====================

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.warn("业务异常 [{}]: {}", request.getRequestURI(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    // ==================== 参数校验异常 ====================

    /**
     * @RequestBody 参数校验失败（@Valid / @Validated 触发）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Map<String, Object>> handleValidException(MethodArgumentNotValidException e) {
        List<FieldError> fieldErrors = e.getBindingResult().getFieldErrors();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("errors", fieldErrors.stream()
                .map(fe -> Map.of(
                        "field", fe.getField(),
                        "message", fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "校验失败"
                ))
                .toList());
        log.warn("参数校验失败: {}", fieldErrors);
        return fail(ErrorCode.PARAM_ERROR.getCode(), "参数校验失败", data);
    }

    /**
     * @ModelAttribute / @RequestParam 绑定异常
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Map<String, Object>> handleBindException(BindException e) {
        List<FieldError> fieldErrors = e.getFieldErrors();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("errors", fieldErrors.stream()
                .map(fe -> Map.of(
                        "field", fe.getField(),
                        "message", fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "校验失败"
                ))
                .toList());
        return fail(ErrorCode.PARAM_ERROR.getCode(), "参数绑定失败", data);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return Result.fail(ErrorCode.PARAM_ERROR.getCode(), "缺少必要参数: " + e.getParameterName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return Result.fail(ErrorCode.PARAM_ERROR.getCode(), "请求体格式错误");
    }

    // ==================== HTTP 方法 / 资源异常 ====================

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return Result.fail(405, "不支持的请求方法: " + e.getMethod());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNoResource(NoResourceFoundException e) {
        return Result.fail(ErrorCode.NOT_FOUND);
    }

    // ==================== 未知异常兜底 ====================

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("未知异常 [{}]: {}", request.getRequestURI(), e.getMessage(), e);
        return Result.fail(ErrorCode.INTERNAL_ERROR);
    }

    // ==================== 私有辅助 ====================

    /**
     * Result.fail 的三参数重载（校验失败场景需要携带 data）。
     */
    private static <T> Result<T> fail(int code, String message, T data) {
        Result<T> result = Result.fail(code, message);
        result.setData(data);
        return result;
    }
}
