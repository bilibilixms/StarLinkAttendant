package com.starlink.common.exception;

import com.starlink.common.result.ErrorCode;
import lombok.Getter;

/**
 * 业务异常。
 * <p>
 * 业务逻辑中抛出此异常将被 {@link com.starlink.common.exception.GlobalExceptionHandler}
 * 统一捕获并转换为标准 {@link com.starlink.common.result.Result} 响应。
 *
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 业务错误码 */
    private final int code;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
