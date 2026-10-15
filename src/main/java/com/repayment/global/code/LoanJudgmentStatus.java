package com.repayment.global.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 대출 상태 판정. id가 판정 번호와 같다. 코드성 테이블 loan_judgment_status의 id와 1:1이다.
 * 십의 자리는 상환 방법(1 만기일시, 2 분할상환), 일의 자리는 단계(1 회차 연체, 2 기한이익상실, 3 기한이익상실 ❻항)다.
 * 한 자리는 상환 방법과 상관없는 상태다.
 * loanStatus는 이 판정일 때 고객에게 보여 주는 대출 상태다.
 */
@Getter
@RequiredArgsConstructor
public enum LoanJudgmentStatus implements CodeEnum {

    NORMAL(0, "정상", LoanStatus.NORMAL),
    MATURITY_OVERDUE(1, "만기 경과 미상환", LoanStatus.MATURITY_OVERDUE),
    PAID_OFF(2, "완제", LoanStatus.PAID_OFF),

    BULLET_INTEREST_OVERDUE(11, "만기일시 이자연체 1개월 미만", LoanStatus.OVERDUE),
    BULLET_ACCELERATION(12, "만기일시 기한이익상실", LoanStatus.ACCELERATED),
    BULLET_ACCELERATION_CLAUSE_6(13, "만기일시 기한이익상실 ❻항", LoanStatus.ACCELERATED),

    INSTALLMENT_OVERDUE(21, "분할상환 1회 연체", LoanStatus.OVERDUE),
    INSTALLMENT_ACCELERATION(22, "분할상환 기한이익상실", LoanStatus.ACCELERATED),
    INSTALLMENT_ACCELERATION_CLAUSE_6(23, "분할상환 기한이익상실 ❻항", LoanStatus.ACCELERATED);

    private final int id;
    private final String description;
    private final LoanStatus loanStatus;
}
