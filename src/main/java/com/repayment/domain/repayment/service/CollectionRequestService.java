package com.repayment.domain.repayment.service;

import com.repayment.domain.repayment.dto.response.CollectionRequestResponse;
import com.repayment.domain.repayment.dto.response.RepaymentEligibilityResponse;
import com.repayment.domain.repayment.exception.RepaymentInProgressException;
import com.repayment.domain.repayment.mapper.CollectionRequestMapper;
import com.repayment.global.code.CollectionRequestStatus;
import com.repayment.global.code.RepaymentChannel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 집금 요청을 저장하고 상태를 바꾼다. 집금 요청의 상태는 여기서만 바꾼다.
 * 상태는 직전 상태일 때만 바꿔, 같은 요청이 두 번 반영되지 않게 한다.
 */
@Service
@RequiredArgsConstructor
public class CollectionRequestService {

    // 요청 처리 중 즉시 재요청이 끝난 뒤에 배치가 잡도록 늦춘다.
    private static final Duration FIRST_RETRY_DELAY = Duration.ofMinutes(1);

    private final RepaymentEligibilityService repaymentEligibilityService;
    private final TelegramNoService telegramNoService;
    private final CollectionRequestMapper collectionRequestMapper;

    /**
     * 상환 가능 확인 → 처리 중인 상환 확인 → 채번 → 집금 요청 저장을 한 트랜잭션으로 하고 커밋한다.
     * 확인이 잡은 대출 잠금은 커밋할 때 풀리므로, 같은 대출의 동시 요청은 앞 요청이 저장한 집금 요청을 보고 거절된다.
     * 처리 중인 상환 확인은 상환 반영도 부르는 상환 가능 확인에 넣지 않는다. 반영 중인 자기 요청에 걸리기 때문이다.
     */
    @Transactional
    public CollectionRequestResponse create(Long loanAccountId, BigDecimal amount, RepaymentChannel channel,
                                            LocalDateTime sentAt) {
        LocalDate sendDate = sentAt.toLocalDate();
        RepaymentEligibilityResponse eligibility = repaymentEligibilityService.check(loanAccountId, amount, sendDate);
        rejectIfInProgress(loanAccountId);
        int telegramNo = telegramNoService.next(sendDate);

        CollectionRequestResponse request = CollectionRequestResponse.builder()
                .loanAccountId(loanAccountId)
                .repaymentChannelId(channel)
                .collectionRequestStatusId(CollectionRequestStatus.REQUESTED)
                .sendDate(sendDate)
                .telegramNo(telegramNo)
                .sentAt(sentAt)
                .withdrawalBankCode(eligibility.getRepaymentBankCode())
                .withdrawalAccountNo(eligibility.getRepaymentAccountNo())
                .amount(amount)
                .nextRetryAt(sentAt.plus(FIRST_RETRY_DELAY))
                .build();
        collectionRequestMapper.insertCollectionRequest(request);
        return request;
    }

    public void markCollected(Long collectionRequestId) {
        collectionRequestMapper.updateCollectionRequestStatus(collectionRequestId,
                CollectionRequestStatus.REQUESTED, CollectionRequestStatus.COLLECTED);
    }

    public void markFailed(Long collectionRequestId) {
        collectionRequestMapper.updateCollectionRequestStatus(collectionRequestId,
                CollectionRequestStatus.REQUESTED, CollectionRequestStatus.FAILED);
    }

    // 상환 반영 트랜잭션 안에서 불러, 반영이 롤백되면 함께 롤백된다.
    public void markCompleted(Long collectionRequestId) {
        collectionRequestMapper.updateCollectionRequestStatus(collectionRequestId,
                CollectionRequestStatus.COLLECTED, CollectionRequestStatus.COMPLETED);
    }

    private void rejectIfInProgress(Long loanAccountId) {
        if (collectionRequestMapper.existsInProgressByLoanAccountId(loanAccountId)) {
            throw new RepaymentInProgressException(loanAccountId);
        }
    }
}
