package com.repayment.global.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 충당 대상 종류. 코드성 테이블 repayment_target_type의 id와 1:1이다.
 * 십의 자리로 묶는다(0 회차, 1 기한이익상실, 2 만기 경과). 대상 행 id가 가리키는 테이블은 종류마다 정해져 있다.
 */
@Getter
@RequiredArgsConstructor
public enum RepaymentTargetType implements CodeEnum {

    // 0 정상, 11·21 연체
    INSTALLMENT_DELINQUENCY_CHARGE(1, "회차 연체금"),                      // installment_delinquency_charge
    INSTALLMENT_INTEREST(2, "회차 이자"),                                 // repayment_schedule
    INSTALLMENT_PRINCIPAL(3, "회차 원금"),                                // repayment_schedule

    // 12·13·22·23 기한이익상실
    ACCELERATION_CARRIED_DELINQUENCY_CHARGE(11, "기한이익상실 옮긴 연체금"),   // loan_acceleration
    ACCELERATION_DAILY_DELINQUENCY_CHARGE(12, "기한이익상실 하루 연체금"),     // acceleration_daily_charge
    ACCELERATION_CARRIED_INTEREST(13, "기한이익상실 옮긴 이자"),              // loan_acceleration
    ACCELERATION_DAILY_INTEREST(14, "기한이익상실 하루 약정이자"),             // acceleration_daily_charge
    ACCELERATION_CARRIED_OVERDUE_PRINCIPAL(15, "기한이익상실 옮긴 밀린 원금"),  // loan_acceleration
    ACCELERATION_REMAINING_PRINCIPAL(16, "기한이익상실 나머지 원금"),          // loan_account

    // 1 만기 경과
    MATURITY_OVERDUE_CARRIED_DELINQUENCY_CHARGE(21, "만기 경과 옮긴 연체금"),  // loan_maturity_overdue
    MATURITY_OVERDUE_DAILY_DELINQUENCY_CHARGE(22, "만기 경과 하루 연체금"),    // maturity_overdue_daily_charge
    MATURITY_OVERDUE_CARRIED_INTEREST(23, "만기 경과 옮긴 이자"),             // loan_maturity_overdue
    MATURITY_OVERDUE_PRINCIPAL(24, "만기 경과 원금잔액");                    // loan_account

    private final int id;
    private final String description;
}
