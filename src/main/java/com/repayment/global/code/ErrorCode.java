package com.repayment.global.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 실패 응답의 code·message·상태코드를 관리한다.
 * 응답의 code는 상수 이름을 그대로 쓴다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 경로를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),

    // 기준금리
    BASE_RATE_FETCH_FAILED(HttpStatus.BAD_GATEWAY, "기준금리를 조회하지 못했습니다."),
    BASE_RATE_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "적용할 기준금리를 찾지 못했습니다."),

    // 대출
    LOAN_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "대출을 찾을 수 없습니다."),
    LOAN_STATUS_JUDGMENT_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "기준일의 대출 상태 판정을 찾지 못했습니다."),

    // 대출 스케줄
    REPAYMENT_SCHEDULE_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "상환 회차를 찾지 못했습니다."),
    REPAYMENT_SCHEDULE_NOT_CONFIRMABLE(HttpStatus.INTERNAL_SERVER_ERROR, "확정할 수 없는 상태의 회차입니다."),
    INVALID_CONFIRM_DATE(HttpStatus.INTERNAL_SERVER_ERROR, "확정할 수 있는 기간이 아닙니다."),
    INVALID_INSTALLMENT_CONFIRM_ORDER(HttpStatus.INTERNAL_SERVER_ERROR, "앞 회차를 먼저 확정해야 합니다."),
    UNCONFIRMED_INSTALLMENT_PAST_DUE(HttpStatus.INTERNAL_SERVER_ERROR, "예정일이 지났는데 금액이 확정되지 않은 회차입니다."),

    // 연체금
    INSTALLMENT_DELINQUENCY_NOT_APPLICABLE(HttpStatus.INTERNAL_SERVER_ERROR, "회차 연체금을 쌓을 수 없는 판정입니다."),

    // 기한이익상실
    LOAN_ACCELERATION_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "기한이익상실 전환 기록을 찾지 못했습니다."),
    ACCELERATION_DELINQUENCY_NOT_APPLICABLE(HttpStatus.INTERNAL_SERVER_ERROR, "기한이익상실 연체금을 쌓을 수 없는 판정입니다."),

    // 만기 경과
    LOAN_MATURITY_OVERDUE_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "만기 경과 전환 기록을 찾지 못했습니다."),

    // 이체
    ACCOUNT_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "계좌를 찾지 못했습니다."),
    INSUFFICIENT_BALANCE(HttpStatus.UNPROCESSABLE_ENTITY, "출금 계좌의 잔액이 부족합니다."),
    ;

    private final HttpStatus status;
    private final String message;

    public String getCode() {
        return name();
    }
}
