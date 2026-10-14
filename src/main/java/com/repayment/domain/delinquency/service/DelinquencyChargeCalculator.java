package com.repayment.domain.delinquency.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * 연체 이자율과 하루치 연체금·약정이자를 계산한다.
 * 금리는 %로 받고, 결과 금액만 원 미만 버린다.
 */
@Component
public class DelinquencyChargeCalculator {

    private static final MathContext DIVISION_CONTEXT = MathContext.DECIMAL128;
    private static final BigDecimal PERCENT = new BigDecimal("100");
    private static final BigDecimal DAYS_PER_YEAR = new BigDecimal("365");
    private static final BigDecimal MAX_DELINQUENCY_RATE = new BigDecimal("15");
    private static final BigDecimal SPREAD_BELOW_MAX = new BigDecimal("3");
    private static final BigDecimal SPREAD_AT_OR_ABOVE_MAX = new BigDecimal("2");

    /**
     * 대출이자율이 15% 이상이면 2%p를 더하고, 그 외에는 3%p를 더하되 최고 15%다.
     *
     * @param loanRate 대출이자율(%) = 적용 기준금리 + 적용 가산금리
     */
    public BigDecimal calculateDelinquencyRate(BigDecimal loanRate) {
        if (loanRate.compareTo(MAX_DELINQUENCY_RATE) >= 0) {
            return loanRate.add(SPREAD_AT_OR_ABOVE_MAX);
        }
        return loanRate.add(SPREAD_BELOW_MAX).min(MAX_DELINQUENCY_RATE);
    }

    /**
     * 하루 연체금 = 대상 금액 × 연체 이자율 ÷ 365
     *
     * @param delinquencyRate 연체 이자율(%)
     */
    public BigDecimal calculateDailyCharge(BigDecimal targetAmount, BigDecimal delinquencyRate) {
        return dailyAmount(targetAmount, delinquencyRate);
    }

    /**
     * 하루 약정이자 = 원금 × 대출이자율 ÷ 365 (13·23 기한이익상실+❻항의 나머지 원금)
     *
     * @param loanRate 대출이자율(%)
     */
    public BigDecimal calculateDailyInterest(BigDecimal principal, BigDecimal loanRate) {
        return dailyAmount(principal, loanRate);
    }

    private BigDecimal dailyAmount(BigDecimal amount, BigDecimal annualRate) {
        return amount.multiply(annualRate)
                .divide(PERCENT, DIVISION_CONTEXT)
                .divide(DAYS_PER_YEAR, DIVISION_CONTEXT)
                .setScale(0, RoundingMode.DOWN);
    }
}
