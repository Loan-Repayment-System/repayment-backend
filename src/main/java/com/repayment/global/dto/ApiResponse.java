package com.repayment.global.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.repayment.global.code.ErrorCode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * /api 아래 모든 응답을 감싼다.
 * 성공 응답에는 code·message가 없고, errors는 검증 실패일 때만 나온다.
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiResponse<T> {

    private final boolean success;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final String code;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final String message;

    private final T data;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final List<FieldError> errors;

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, null, null, data, null);
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        return error(errorCode, null);
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, T data) {
        return new ApiResponse<>(false, errorCode.getCode(), errorCode.getMessage(), data, null);
    }

    public static <T> ApiResponse<T> invalid(List<FieldError> errors) {
        ErrorCode errorCode = ErrorCode.INVALID_INPUT_VALUE;
        return new ApiResponse<>(false, errorCode.getCode(), errorCode.getMessage(), null, errors);
    }

    @Getter
    @AllArgsConstructor
    public static class FieldError {

        private final String field;
        private final String reason;
    }
}
