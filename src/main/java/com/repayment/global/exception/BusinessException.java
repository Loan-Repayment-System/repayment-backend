package com.repayment.global.exception;

import com.repayment.global.code.ErrorCode;
import lombok.Getter;

/**
 * 업무 예외의 최상위 클래스. 직접 던지지 않고 domain/{도메인}/exception/의 구체 예외를 던진다.
 * data는 실패 응답의 data에 그대로 담긴다.
 */
@Getter
public abstract class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Object data;

    protected BusinessException(ErrorCode errorCode) {
        this(errorCode, null, null);
    }

    // 기술 예외(외부 연동 실패 등)를 감쌀 때 쓴다.
    protected BusinessException(ErrorCode errorCode, Throwable cause) {
        this(errorCode, null, cause);
    }

    protected BusinessException(ErrorCode errorCode, Object data) {
        this(errorCode, data, null);
    }

    private BusinessException(ErrorCode errorCode, Object data, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.data = data;
    }
}
