package com.repayment.domain.repayment.service;

import com.repayment.domain.loan.dto.response.LoanAccountStatusResponse;
import com.repayment.domain.loan.exception.LoanAccountNotFoundException;
import com.repayment.domain.loan.exception.LoanStatusJudgmentNotFoundException;
import com.repayment.domain.loan.mapper.LoanAccountMapper;
import com.repayment.domain.loan.mapper.LoanStatusJudgmentMapper;
import com.repayment.domain.repayment.dto.response.CollectionRequestResponse;
import com.repayment.domain.repayment.dto.response.RepaymentResultResponse;
import com.repayment.domain.repayment.exception.RepaymentReflectionPendingException;
import com.repayment.global.code.LoanJudgmentStatus;
import com.repayment.global.code.RepaymentChannel;
import com.repayment.global.exception.CollectionRejectedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 상환 요청 1건을 처리한다. 채널(고객 상환·자동 상환)은 부르는 쪽이 정해 넘긴다.
 * 집금은 DB 롤백으로 되돌릴 수 없어서, 트랜잭션을 집금 앞뒤로 나눈다.
 * 집금 요청 저장(TX1) → 집금 → 상환 반영(TX2) 순서로 하고, 집금 결과는 집금 요청의 상태로 남긴다.
 *
 * <ul>
 *   <li>거절 응답: 거절 상태로 바꾸고 거절 예외를 그대로 던진다.</li>
 *   <li>응답 없음: 요청 상태로 두고 결과 확인 중 예외를 던진다.</li>
 *   <li>반영 실패: 집금 완료 상태로 두고 반영 중 예외를 던진다.</li>
 * </ul>
 * 마지막에 상환을 모두 반영한 원금잔액·대출 상태·남은 갚을 금액을 읽어 돌려준다.
 */
@Service
@RequiredArgsConstructor
public class RepaymentService {

    private final CollectionRequestService collectionRequestService;
    private final CollectionService collectionService;
    private final RepaymentReflectionService repaymentReflectionService;
    private final LoanAccountMapper loanAccountMapper;
    private final LoanStatusJudgmentMapper loanStatusJudgmentMapper;
    private final RepayableAmountService repayableAmountService;
    private final Clock clock;

    public RepaymentResultResponse repay(Long loanAccountId, BigDecimal amount, RepaymentChannel channel) {
        // 납부 시각과 판정 기준일이 자정을 사이에 두고 어긋나지 않게 한 번만 읽는다.
        // DATETIME은 초 미만을 반올림해 저장하므로, 저장할 값과 돌려줄 값이 같도록 먼저 버린다.
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);

        CollectionRequestResponse request = collectionRequestService.create(loanAccountId, amount, channel, now);
        collect(request);
        reflect(request);
        return readResult(loanAccountId, amount, now, now.toLocalDate());
    }

    private void collect(CollectionRequestResponse request) {
        try {
            collectionService.collect(request);
        } catch (CollectionRejectedException e) {
            collectionRequestService.markFailed(request.getCollectionRequestId());
            throw e;
        }
        collectionRequestService.markCollected(request.getCollectionRequestId());
    }

    // 돈은 이미 빠졌으므로 어떤 이유로 실패해도 반영 중으로 알린다.
    private void reflect(CollectionRequestResponse request) {
        try {
            repaymentReflectionService.reflect(request);
        } catch (RuntimeException e) {
            throw new RepaymentReflectionPendingException(request.getTransactionNo(), e);
        }
    }

    private RepaymentResultResponse readResult(Long loanAccountId, BigDecimal paidAmount, LocalDateTime paidAt,
                                               LocalDate businessDate) {
        LoanAccountStatusResponse loanAccount = loanAccountMapper.findLoanAccountStatus(loanAccountId)
                .orElseThrow(() -> new LoanAccountNotFoundException(loanAccountId));
        // 상태 판정이 방금 판정을 남겼으므로 비어 있으면 데이터 오류다.
        LoanJudgmentStatus latestJudgment = loanStatusJudgmentMapper.findLatestJudgmentStatus(loanAccountId)
                .orElseThrow(() -> new LoanStatusJudgmentNotFoundException(loanAccountId, businessDate));
        BigDecimal remainingRepayableAmount =
                repayableAmountService.calculate(loanAccountId, latestJudgment).getTotalAmount();
        return RepaymentResultResponse.builder()
                .paidAmount(paidAmount)
                .paidAt(paidAt)
                .loanPrincipalBalance(loanAccount.getLoanPrincipalBalance())
                .loanStatus(loanAccount.getLoanStatusId())
                .remainingRepayableAmount(remainingRepayableAmount)
                .build();
    }
}
