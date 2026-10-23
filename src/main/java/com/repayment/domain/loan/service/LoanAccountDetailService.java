package com.repayment.domain.loan.service;

import com.repayment.domain.acceleration.exception.LoanAccelerationNotFoundException;
import com.repayment.domain.acceleration.mapper.LoanAccelerationMapper;
import com.repayment.domain.loan.dto.response.LoanAccountDetailResponse;
import com.repayment.domain.loan.exception.LoanAccountNotFoundException;
import com.repayment.domain.loan.mapper.LoanAccountMapper;
import com.repayment.domain.maturity.exception.LoanMaturityOverdueNotFoundException;
import com.repayment.domain.maturity.mapper.LoanMaturityOverdueMapper;
import com.repayment.domain.repayment.service.RepayableAmountService;
import com.repayment.domain.schedule.mapper.RepaymentScheduleMapper;
import com.repayment.global.code.LoanJudgmentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대출 하나의 현재 모습을 읽는다. 기본 정보·현재 상태에 더해, 대출 상태에 맞는 기록과 지금 갚을 금액을 넣는다.
 * 정상·연체는 회차 목록, 기한이익상실·만기 경과는 대출 단위 기록, 완제는 기본 정보·현재 상태만 넣는다.
 */
@Service
@RequiredArgsConstructor
public class LoanAccountDetailService {

    private final LoanAccountMapper loanAccountMapper;
    private final RepaymentScheduleMapper repaymentScheduleMapper;
    private final LoanAccelerationMapper loanAccelerationMapper;
    private final LoanMaturityOverdueMapper loanMaturityOverdueMapper;
    private final RepayableAmountService repayableAmountService;

    @Transactional(readOnly = true)
    public LoanAccountDetailResponse getDetail(Long loanAccountId) {
        LoanAccountDetailResponse detail = loanAccountMapper.findLoanAccountDetail(loanAccountId)
                .orElseThrow(() -> new LoanAccountNotFoundException(loanAccountId));
        LoanAccountDetailResponse.LoanAccountDetailResponseBuilder builder = detail.toBuilder();
        switch (detail.getStatus().getLoanStatus()) {
            case NORMAL, OVERDUE -> builder.installments(repaymentScheduleMapper.findInstallmentDetails(loanAccountId));
            case ACCELERATED -> builder.acceleration(loanAccelerationMapper.findAccelerationDetail(loanAccountId)
                    .orElseThrow(() -> new LoanAccelerationNotFoundException(loanAccountId)));
            case MATURITY_OVERDUE -> builder.maturityOverdue(loanMaturityOverdueMapper.findMaturityOverdueDetail(loanAccountId)
                    .orElseThrow(() -> new LoanMaturityOverdueNotFoundException(loanAccountId)));
            case PAID_OFF -> {
                return detail;
            }
        }

        // 판정 행이 없으면 0 정상으로 본다.
        LoanJudgmentStatus latestJudgment = detail.getStatus().getLatestJudgment() != null
                ? detail.getStatus().getLatestJudgment()
                : LoanJudgmentStatus.NORMAL;
        return builder.repayableAmount(repayableAmountService.calculate(loanAccountId, latestJudgment)).build();
    }
}
