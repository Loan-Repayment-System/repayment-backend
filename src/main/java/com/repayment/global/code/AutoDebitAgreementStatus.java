package com.repayment.global.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 자동 이체 약정 상태. 코드성 테이블 auto_debit_agreement_status의 id와 1:1이다.
 */
@Getter
@RequiredArgsConstructor
public enum AutoDebitAgreementStatus implements CodeEnum {

    ACTIVE(1, "사용"),
    SUSPENDED(2, "정지"),
    TERMINATED(3, "해지");

    private final int id;
    private final String description;
}
