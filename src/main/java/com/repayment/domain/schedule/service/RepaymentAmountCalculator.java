package com.repayment.domain.schedule.service;

import com.repayment.domain.schedule.dto.response.RepaymentAmountResponse;
import com.repayment.global.code.RepaymentMethod;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * 상환 방법별로 회차 하나의 원금과 이자를 계산한다.
 * 중간 계산은 버리지 않고, 결과 원금·이자를 각각 원 미만 버린다.
 */
@Component
public class RepaymentAmountCalculator {

    private static final MathContext DIVISION_CONTEXT = MathContext.DECIMAL128;
    private static final BigDecimal PERCENT = new BigDecimal("100");
    private static final BigDecimal MONTHS_PER_YEAR = new BigDecimal("12");

    /**
     * @param balance               계산 잔액 B (최초 원금 − 앞 회차 원금 합)
     * @param annualRate            연이율(%) = 기준금리 + 가산금리
     * @param remainingInstallments 남은 회차 n (이 회차 포함)
     * @param originalPrincipal     최초 원금 P0
     * @param totalInstallments     총 회차 N
     */
    public RepaymentAmountResponse calculate(RepaymentMethod method, BigDecimal balance, BigDecimal annualRate,
                                             int remainingInstallments, BigDecimal originalPrincipal,
                                             int totalInstallments) {
        BigDecimal monthlyRate = annualRate
                .divide(PERCENT, DIVISION_CONTEXT)
                .divide(MONTHS_PER_YEAR, DIVISION_CONTEXT);
        BigDecimal interest = balance.multiply(monthlyRate);

        // 마지막 회차는 앞에서 버린 금액까지 남은 잔액을 모두 원금으로 한다
        if (remainingInstallments == 1) {
            return new RepaymentAmountResponse(floor(balance), floor(interest));
        }

        BigDecimal principal = switch (method) {
            case EQUAL_PRINCIPAL_INTEREST -> equalPrincipalInterestPrincipal(balance, monthlyRate, remainingInstallments);
            case EQUAL_PRINCIPAL -> originalPrincipal.divide(BigDecimal.valueOf(totalInstallments), DIVISION_CONTEXT);
            case BULLET -> BigDecimal.ZERO;
        };
        return new RepaymentAmountResponse(floor(principal), floor(interest));
    }

    /**
     * 원리금균등: 상환액 = B·r·(1+r)ⁿ / ((1+r)ⁿ − 1), 원금 = 상환액 − 이자. r = 0이면 B / n.
     */
    private BigDecimal equalPrincipalInterestPrincipal(BigDecimal balance, BigDecimal monthlyRate,
                                                       int remainingInstallments) {
        if (monthlyRate.signum() == 0) {
            return balance.divide(BigDecimal.valueOf(remainingInstallments), DIVISION_CONTEXT);
        }
        BigDecimal growth = BigDecimal.ONE.add(monthlyRate).pow(remainingInstallments, DIVISION_CONTEXT);
        BigDecimal payment = balance.multiply(monthlyRate).multiply(growth)
                .divide(growth.subtract(BigDecimal.ONE), DIVISION_CONTEXT);
        return payment.subtract(balance.multiply(monthlyRate));
    }

    private BigDecimal floor(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.DOWN);
    }
}
