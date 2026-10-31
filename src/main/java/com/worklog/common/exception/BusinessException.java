package com.worklog.common.exception;

import com.worklog.common.ResultCode;
import lombok.Getter;

import java.io.Serial;

/**
 * 业务异常。
 *
 * <p>业务代码中直接 {@code throw new BusinessException(ResultCode.ACCOUNT_DISABLED)}，
 * 由 {@link GlobalExceptionHandler} 统一转为 {@code Result}，不需要在 Controller 里写 try-catch。</p>
 */
@Getter
public class BusinessException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 业务状态码 */
    private final int code;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
