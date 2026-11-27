package com.repayment.global.exception;

import com.repayment.global.code.ErrorCode;

/**
 * 판정과 그 근거 기록이 맞지 않는 등 대출 데이터가 잘못되었다. 다시 해도 같은 실패를 한 타입으로 잡는다.
 */
public abstract class DataInconsistencyException extends BusinessException {

    protected DataInconsistencyException(ErrorCode errorCode) {
        super(errorCode);
    }
}
