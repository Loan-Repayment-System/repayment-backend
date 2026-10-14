package com.repayment.global.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 대출 상태 판정. id가 판정 번호와 같다. 코드성 테이블 loan_judgment_status의 id와 1:1이다.
 * 십의 자리는 상환 방법(1 만기일시, 2 분할상환), 일의 자리는 단계(1 회차 연체, 2 기한이익상실, 3 기한이익상실 ❻항)다.
 * 한 자리는 상환 방법과 상관없는 상태다.
 */
@Getter
@RequiredArgsConstructor
public enum LoanJudgmentStatus implements CodeEnum {

    NORMAL(0, "정상"),
    MATURITY_OVERDUE(1, "만기 경과 미상환"),
    PAID_OFF(2, "완제"),

    BULLET_INTEREST_OVERDUE(11, "만기일시 이자연체 1개월 미만"),
    BULLET_ACCELERATION(12, "만기일시 기한이익상실"),
    BULLET_ACCELERATION_CLAUSE_6(13, "만기일시 기한이익상실 ❻항"),

    INSTALLMENT_OVERDUE(21, "분할상환 1회 연체"),
    INSTALLMENT_ACCELERATION(22, "분할상환 기한이익상실"),
    INSTALLMENT_ACCELERATION_CLAUSE_6(23, "분할상환 기한이익상실 ❻항");

    private final int id;
    private final String description;
}
