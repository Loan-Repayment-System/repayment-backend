package com.repayment.domain.repayment.service;

/**
 * 갚을 금액을 채울 대상의 종류. 대상 행 id가 가리키는 테이블은 종류마다 정해져 있다.
 */
public enum RepaymentTargetType {

    // 0 정상, 11·21 연체
    INSTALLMENT_DELINQUENCY_CHARGE,         // installment_delinquency_charge
    INSTALLMENT_INTEREST,                   // repayment_schedule
    INSTALLMENT_PRINCIPAL,                  // repayment_schedule

    // 12·13·22·23 기한이익상실
    ACCELERATION_CARRIED_DELINQUENCY_CHARGE, // loan_acceleration
    ACCELERATION_DAILY_DELINQUENCY_CHARGE,   // acceleration_daily_charge
    ACCELERATION_CARRIED_INTEREST,           // loan_acceleration
    ACCELERATION_DAILY_INTEREST,             // acceleration_daily_charge
    ACCELERATION_CARRIED_OVERDUE_PRINCIPAL,  // loan_acceleration
    ACCELERATION_REMAINING_PRINCIPAL,        // loan_account

    // 1 만기 경과
    MATURITY_OVERDUE_CARRIED_DELINQUENCY_CHARGE, // loan_maturity_overdue
    MATURITY_OVERDUE_DAILY_DELINQUENCY_CHARGE,   // maturity_overdue_daily_charge
    MATURITY_OVERDUE_CARRIED_INTEREST,           // loan_maturity_overdue
    MATURITY_OVERDUE_PRINCIPAL                   // loan_account
}
