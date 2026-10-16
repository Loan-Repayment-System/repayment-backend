package com.repayment.domain.repayment.service;

import com.repayment.domain.loan.dto.response.RepaymentLoanAccountResponse;
import com.repayment.domain.loan.exception.LoanAccountAccessDeniedException;
import com.repayment.domain.loan.exception.LoanAccountNotFoundException;
import com.repayment.domain.loan.exception.LoanAlreadyPaidOffException;
import com.repayment.domain.loan.mapper.LoanAccountMapper;
import com.repayment.domain.loan.mapper.LoanStatusJudgmentMapper;
import com.repayment.domain.repayment.dto.response.RepayableAmountResponse;
import com.repayment.domain.repayment.dto.response.RepaymentEligibilityResponse;
import com.repayment.domain.repayment.exception.InvalidRepaymentAmountException;
import com.repayment.domain.repayment.exception.NothingToRepayException;
import com.repayment.domain.repayment.exception.RepaymentExceedsRepayableAmountException;
import com.repayment.global.code.LoanJudgmentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 대출을 잠그고 지금 받을 수 없는 상환 요청을 거절한다. 위에서부터 검사해 처음 걸린 것으로 거절한다.
 * 잠금은 부르는 쪽(상환 처리) 트랜잭션이 끝날 때까지 유지되어, 같은 대출의 상환은 한 건씩 처리된다.
 * 잔액 부족은 보지 않는다. 당행 이체가 거절한다.
 */
@Service
@RequiredArgsConstructor
public class RepaymentEligibilityService {

    private final LoanAccountMapper loanAccountMapper;
    private final LoanStatusJudgmentMapper loanStatusJudgmentMapper;
    private final RepayableAmountService repayableAmountService;

    @Transactional
    public RepaymentEligibilityResponse check(Long loanAccountId, Long memberId, BigDecimal amount) {
        RepaymentLoanAccountResponse loanAccount = loanAccountMapper.findRepaymentLoanAccountForUpdate(loanAccountId)
                .orElseThrow(() -> new LoanAccountNotFoundException(loanAccountId));
        // 남의 대출이 완제인지, 갚을 금액이 얼마인지 알 수 없도록 본인 확인을 먼저 한다.
        if (!loanAccount.getMemberId().equals(memberId)) {
            throw new LoanAccountAccessDeniedException(loanAccountId, memberId);
        }
        if (!isValidAmount(amount)) {
            throw new InvalidRepaymentAmountException(amount);
        }

        // 판정 행이 없으면 0 정상으로 본다.
        LoanJudgmentStatus latestJudgment = loanStatusJudgmentMapper.findLatestJudgmentStatus(loanAccountId)
                .orElse(LoanJudgmentStatus.NORMAL);
        if (latestJudgment == LoanJudgmentStatus.PAID_OFF) {
            throw new LoanAlreadyPaidOffException(loanAccountId);
        }

        RepayableAmountResponse repayableAmount = repayableAmountService.calculate(loanAccountId, latestJudgment);
        BigDecimal totalAmount = repayableAmount.getTotalAmount();
        if (totalAmount.compareTo(BigDecimal.ZERO) == 0) {
            throw new NothingToRepayException(loanAccountId);
        }
        if (amount.compareTo(totalAmount) > 0) {
            throw new RepaymentExceedsRepayableAmountException(loanAccountId, amount, totalAmount);
        }
        return new RepaymentEligibilityResponse(loanAccount.getRepaymentAccountId(), latestJudgment, repayableAmount);
    }

    // 1원 이상이고 원 미만 자릿수가 없어야 한다. 1000.00처럼 0으로 끝나는 소수 자리는 원 단위로 본다.
    private boolean isValidAmount(BigDecimal amount) {
        return amount != null
                && amount.compareTo(BigDecimal.ONE) >= 0
                && amount.stripTrailingZeros().scale() <= 0;
    }
}
