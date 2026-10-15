package com.repayment.domain.maturity.service;

import com.repayment.domain.delinquency.service.DelinquencyChargeCalculator;
import com.repayment.domain.maturity.dto.response.MaturityOverdueDailyChargeResponse;
import com.repayment.domain.maturity.dto.response.MaturityOverdueDailyChargeTargetResponse;
import com.repayment.domain.maturity.exception.LoanMaturityOverdueNotFoundException;
import com.repayment.domain.maturity.mapper.MaturityOverdueDailyChargeMapper;
import com.repayment.global.code.DelinquencyStatus;
import com.repayment.global.code.LoanJudgmentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 만기 경과 대출(판정 1)에 그날 원금잔액에 붙는 연체금을 1행 쌓는다. 하루 약정이자는 붙지 않는다.
 * 만기 경과일 때만 부르는 것은 부르는 쪽이 정한다. 그날 이미 쌓았으면 아무것도 하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class MaturityOverdueDailyChargeService {

    private final MaturityOverdueDailyChargeMapper maturityOverdueDailyChargeMapper;
    private final DelinquencyChargeCalculator delinquencyChargeCalculator;

    @Transactional
    public void accrue(Long loanAccountId, LocalDate businessDate) {
        MaturityOverdueDailyChargeTargetResponse target = maturityOverdueDailyChargeMapper.findAccrualTarget(loanAccountId)
                .orElseThrow(() -> new LoanMaturityOverdueNotFoundException(loanAccountId));
        if (maturityOverdueDailyChargeMapper.existsByLoanMaturityOverdueIdAndChargeDate(
                target.getLoanMaturityOverdueId(), businessDate)) {
            return;
        }
        maturityOverdueDailyChargeMapper.insertCharge(charge(target, businessDate));
    }

    private MaturityOverdueDailyChargeResponse charge(MaturityOverdueDailyChargeTargetResponse target,
                                                      LocalDate businessDate) {
        BigDecimal loanRate = target.getAppliedBaseRate().add(target.getAppliedAdditionalRate());
        BigDecimal delinquencyRate = delinquencyChargeCalculator.calculateDelinquencyRate(loanRate);
        BigDecimal delinquencyCharge = delinquencyChargeCalculator.calculateDailyCharge(
                target.getLoanPrincipalBalance(), delinquencyRate);
        boolean nothingCharged = delinquencyCharge.compareTo(BigDecimal.ZERO) == 0;
        return MaturityOverdueDailyChargeResponse.builder()
                .loanMaturityOverdueId(target.getLoanMaturityOverdueId())
                .chargeDate(businessDate)
                .loanJudgmentStatusId(LoanJudgmentStatus.MATURITY_OVERDUE)
                .delinquencyTargetAmount(target.getLoanPrincipalBalance())
                .delinquencyRate(delinquencyRate)
                .delinquencyCharge(delinquencyCharge)
                .delinquencyStatusId(nothingCharged ? DelinquencyStatus.RESOLVED : DelinquencyStatus.IN_PROGRESS)
                .build();
    }
}
