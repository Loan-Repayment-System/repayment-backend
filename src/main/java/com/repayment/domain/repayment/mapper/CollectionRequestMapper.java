package com.repayment.domain.repayment.mapper;

import com.repayment.domain.repayment.dto.response.CollectionRequestResponse;
import com.repayment.global.code.CollectionRequestStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CollectionRequestMapper {

    int insertCollectionRequest(CollectionRequestResponse collectionRequest);

    /**
     * 대출에 요청·집금 완료 상태인 집금 요청이 있는지 본다. 상환 완료·거절은 끝난 건이라 보지 않는다.
     */
    boolean existsInProgressByLoanAccountId(Long loanAccountId);

    /**
     * 상태가 from일 때만 to로 바꾼다. 이미 바뀐 행은 건드리지 않고 0을 돌려준다.
     */
    int updateCollectionRequestStatus(@Param("collectionRequestId") Long collectionRequestId,
                                      @Param("from") CollectionRequestStatus from,
                                      @Param("to") CollectionRequestStatus to);
}
