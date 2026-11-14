package com.repayment.domain.repayment.exception;

import com.repayment.global.code.ErrorCode;
import com.repayment.global.exception.BusinessException;

/**
 * 상환 완료로 바꾸려는 집금 요청이 집금 완료 상태가 아니다. 예) 다른 곳에서 먼저 반영해 이미 상환 완료다.
 * 상환 반영 트랜잭션 안에서 던져, 이번 반영을 롤백한다.
 */
public class CollectionRequestNotCollectedException extends BusinessException {

    private final Long collectionRequestId;

    public CollectionRequestNotCollectedException(Long collectionRequestId) {
        super(ErrorCode.COLLECTION_REQUEST_NOT_COLLECTED);
        this.collectionRequestId = collectionRequestId;
    }

    public Long getCollectionRequestId() {
        return collectionRequestId;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + " collectionRequestId=" + collectionRequestId;
    }
}
