package com.repayment.domain.loan.service;

import com.repayment.domain.acceleration.service.AccelerationConversionService;
import com.repayment.domain.acceleration.service.AccelerationDailyChargeService;
import com.repayment.domain.delinquency.service.InstallmentDelinquencyChargeService;
import com.repayment.domain.loan.dto.response.LoanStatusJudgmentResponse;
import com.repayment.domain.loan.exception.LoanStatusJudgmentNotFoundException;
import com.repayment.domain.loan.mapper.LoanStatusJudgmentMapper;
import com.repayment.domain.maturity.service.MaturityOverdueConversionService;
import com.repayment.domain.maturity.service.MaturityOverdueDailyChargeService;
import com.repayment.global.code.LoanJudgmentStatus;
import com.repayment.global.code.LoanStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 오늘 판정과 직전 판정을 비교해 대출 상태를 갱신하고, 연체금 쌓기·전환을 알맞게 부른다.
 * 오늘 판정은 그 대출의 가장 최근 판정 행이고, 직전 판정은 그 앞의 행이다(없으면 0 정상).
 * 부른 모듈은 모두 이미 한 일을 건너뛰므로 다시 실행해도 된다.
 */
@Service
@RequiredArgsConstructor
public class LoanStatusChangeService {

    private final LoanStatusJudgmentMapper loanStatusJudgmentMapper;
    private final LoanStatusUpdateService loanStatusUpdateService;
    private final InstallmentDelinquencyChargeService installmentDelinquencyChargeService;
    private final AccelerationConversionService accelerationConversionService;
    private final AccelerationDailyChargeService accelerationDailyChargeService;
    private final MaturityOverdueConversionService maturityOverdueConversionService;
    private final MaturityOverdueDailyChargeService maturityOverdueDailyChargeService;

    @Transactional
    public void process(Long loanAccountId, LocalDate businessDate) {
        List<LoanStatusJudgmentResponse> judgments = loanStatusJudgmentMapper.findRecentJudgments(loanAccountId);
        if (judgments.isEmpty() || !judgments.get(0).getBusinessDate().isEqual(businessDate)) {
            throw new LoanStatusJudgmentNotFoundException(loanAccountId, businessDate);
        }
        LoanJudgmentStatus today = judgments.get(0).getLoanJudgmentStatusId();
        LoanJudgmentStatus previous = judgments.size() > 1
                ? judgments.get(1).getLoanJudgmentStatusId()
                : LoanJudgmentStatus.NORMAL;

        loanStatusUpdateService.update(loanAccountId, today);
        LoanStatus todayStatus = today.getLoanStatus();
        boolean firstDay = previous.getLoanStatus() != todayStatus;
        switch (todayStatus) {
            case OVERDUE -> installmentDelinquencyChargeService.accrue(loanAccountId, businessDate, today);
            case ACCELERATED -> {
                if (firstDay) {
                    accelerationConversionService.convert(loanAccountId, businessDate);
                }
                accelerationDailyChargeService.accrue(loanAccountId, businessDate, today);
            }
            case MATURITY_OVERDUE -> {
                if (firstDay) {
                    maturityOverdueConversionService.convert(loanAccountId, businessDate);
                }
                maturityOverdueDailyChargeService.accrue(loanAccountId, businessDate);
            }
            case NORMAL, PAID_OFF -> {
            }
        }
    }
}
