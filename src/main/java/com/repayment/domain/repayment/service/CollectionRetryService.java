package com.repayment.domain.repayment.service;

import com.repayment.domain.repayment.dto.response.CollectionRequestResponse;
import com.repayment.domain.repayment.exception.CollectionResultPendingException;
import com.repayment.global.code.CollectionRequestStatus;
import com.repayment.global.exception.CollectionRejectedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 결과가 정해지지 않은 집금 요청 1건을 상태에 따라 다시 처리한다.
 *
 * <ul>
 *   <li>요청 상태: 같은 집금 요청을 1번 보낸다. 성공하면 집금 완료로 바꾸고 상환에 반영하고, 거절이면 거절 상태로 바꾼다.</li>
 *   <li>집금 완료 상태: 돈은 이미 빠졌으므로 집금은 보내지 않고 상환 반영만 다시 한다.</li>
 * </ul>
 * 응답이 없거나 반영에 실패하면 상태는 그대로 두고 시도를 기록해, 다음 시도 시각에 다시 처리되게 한다.
 */
@Service
@RequiredArgsConstructor
public class CollectionRetryService {

    private final CollectionRequestService collectionRequestService;
    private final CollectionService collectionService;
    private final RepaymentReflectionService repaymentReflectionService;

    public void retry(CollectionRequestResponse request, LocalDateTime now) {
        if (request.getCollectionRequestStatusId() == CollectionRequestStatus.REQUESTED
                && !collect(request, now)) {
            return;
        }
        reflect(request, now);
    }

    // 집금에 성공했으면 true를 돌려준다.
    private boolean collect(CollectionRequestResponse request, LocalDateTime now) {
        try {
            collectionService.collect(request);
        } catch (CollectionRejectedException e) {
            collectionRequestService.markFailed(request.getCollectionRequestId());
            return false;
        } catch (CollectionResultPendingException e) {
            collectionRequestService.recordRetryAttempt(request, now);
            return false;
        }
        collectionRequestService.markCollected(request.getCollectionRequestId());
        return true;
    }

    // 다른 곳에서 먼저 반영해 실패한 경우도 시도만 기록한다. 이미 상환 완료라 다음 대상에 잡히지 않는다.
    private void reflect(CollectionRequestResponse request, LocalDateTime now) {
        try {
            repaymentReflectionService.reflect(request);
        } catch (RuntimeException e) {
            collectionRequestService.recordRetryAttempt(request, now);
        }
    }
}
