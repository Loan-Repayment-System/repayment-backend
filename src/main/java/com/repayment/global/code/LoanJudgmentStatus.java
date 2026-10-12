package com.repayment.global.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 대출 상태 판정. id가 판정 번호와 같다(0 정상, 1~7 연체 근거, 8 완제). 코드성 테이블 loan_judgment_status의 id와 1:1이다.
 */
@Getter
@RequiredArgsConstructor
public enum LoanJudgmentStatus implements CodeEnum {

    NORMAL(0, "정상"),
    BULLET_INTEREST_OVERDUE(1, "만기일시 이자연체 1개월 미만"),
    BULLET_ACCELERATION(2, "만기일시 기한이익상실"),
    BULLET_ACCELERATION_CLAUSE_6(3, "만기일시 기한이익상실 ❻항"),
    MATURITY_OVERDUE(4, "만기 경과 미상환"),
    INSTALLMENT_OVERDUE(5, "분할상환 1회 연체"),
    INSTALLMENT_ACCELERATION(6, "분할상환 기한이익상실"),
    INSTALLMENT_ACCELERATION_CLAUSE_6(7, "분할상환 기한이익상실 ❻항"),
    PAID_OFF(8, "완제");

    private final int id;
    private final String description;
}
