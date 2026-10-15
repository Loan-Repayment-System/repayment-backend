package com.repayment.domain.acceleration.service;

import com.repayment.domain.acceleration.dto.response.AccelerationDailyChargeResponse;
import com.repayment.domain.acceleration.dto.response.AccelerationDailyChargeTargetResponse;
import com.repayment.domain.acceleration.exception.AccelerationDelinquencyNotApplicableException;
import com.repayment.domain.acceleration.exception.LoanAccelerationNotFoundException;
import com.repayment.domain.acceleration.mapper.AccelerationDailyChargeMapper;
import com.repayment.domain.delinquency.service.DelinquencyChargeCalculator;
import com.repayment.global.code.DelinquencyStatus;
import com.repayment.global.code.LoanJudgmentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

/**
 * 기한이익상실 대출(오늘 판정 12·13·22·23)에 그날 연체금과 하루 약정이자를 1행 쌓는다.
 * 오늘 판정은 부르는 쪽이 넘긴다. 그날 이미 쌓았으면 아무것도 하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class AccelerationDailyChargeService {

    private static final Set<LoanJudgmentStatus> ACCELERATION_JUDGMENTS = EnumSet.of(
            LoanJudgmentStatus.BULLET_ACCELERATION,
            LoanJudgmentStatus.BULLET_ACCELERATION_CLAUSE_6,
            LoanJudgmentStatus.INSTALLMENT_ACCELERATION,
            LoanJudgmentStatus.INSTALLMENT_ACCELERATION_CLAUSE_6);
    private static final Set<LoanJudgmentStatus> CLAUSE_6_JUDGMENTS = EnumSet.of(
            LoanJudgmentStatus.BULLET_ACCELERATION_CLAUSE_6,
            LoanJudgmentStatus.INSTALLMENT_ACCELERATION_CLAUSE_6);

    private final AccelerationDailyChargeMapper accelerationDailyChargeMapper;
    private final DelinquencyChargeCalculator delinquencyChargeCalculator;

    @Transactional
    public void accrue(Long loanAccountId, LocalDate businessDate, LoanJudgmentStatus todayJudgment) {
        if (!ACCELERATION_JUDGMENTS.contains(todayJudgment)) {
            throw new AccelerationDelinquencyNotApplicableException(loanAccountId, todayJudgment);
        }
        AccelerationDailyChargeTargetResponse target = accelerationDailyChargeMapper.findAccrualTarget(loanAccountId)
                .orElseThrow(() -> new LoanAccelerationNotFoundException(loanAccountId));
        if (accelerationDailyChargeMapper.existsByLoanAccelerationIdAndChargeDate(
                target.getLoanAccelerationId(), businessDate)) {
            return;
        }
        accelerationDailyChargeMapper.insertCharge(charge(target, businessDate, todayJudgment));
    }

    /**
     * 12·22는 원금잔액 전체에 연체금이 붙는다.
     * 13·23(❻항)은 옮긴 밀린 금액에만 연체금이 붙고, 나머지 원금에는 하루 약정이자가 붙는다.
     * 13(만기일시)은 옮긴 밀린 원금이 0이라 23과 같은 식이 된다.
     */
    private AccelerationDailyChargeResponse charge(AccelerationDailyChargeTargetResponse target,
                                                   LocalDate businessDate, LoanJudgmentStatus todayJudgment) {
        BigDecimal loanRate = target.getAppliedBaseRate().add(target.getAppliedAdditionalRate());
        BigDecimal targetAmount;
        BigDecimal interestAmount;
        if (CLAUSE_6_JUDGMENTS.contains(todayJudgment)) {
            BigDecimal remainingOverduePrincipal =
                    target.getCarriedOverduePrincipal().subtract(target.getPaidCarriedOverduePrincipal());
            targetAmount = target.getCarriedInterest().subtract(target.getPaidCarriedInterest())
                    .add(remainingOverduePrincipal);
            interestAmount = delinquencyChargeCalculator.calculateDailyInterest(
                    target.getLoanPrincipalBalance().subtract(remainingOverduePrincipal), loanRate);
        } else {
            targetAmount = target.getLoanPrincipalBalance();
            interestAmount = BigDecimal.ZERO;
        }
        BigDecimal delinquencyRate = delinquencyChargeCalculator.calculateDelinquencyRate(loanRate);
        BigDecimal delinquencyCharge = delinquencyChargeCalculator.calculateDailyCharge(targetAmount, delinquencyRate);
        boolean nothingCharged = delinquencyCharge.compareTo(BigDecimal.ZERO) == 0
                && interestAmount.compareTo(BigDecimal.ZERO) == 0;
        return AccelerationDailyChargeResponse.builder()
                .loanAccelerationId(target.getLoanAccelerationId())
                .chargeDate(businessDate)
                .loanJudgmentStatusId(todayJudgment)
                .delinquencyTargetAmount(targetAmount)
                .delinquencyRate(delinquencyRate)
                .delinquencyCharge(delinquencyCharge)
                .interestAmount(interestAmount)
                .delinquencyStatusId(nothingCharged ? DelinquencyStatus.RESOLVED : DelinquencyStatus.IN_PROGRESS)
                .build();
    }
}
