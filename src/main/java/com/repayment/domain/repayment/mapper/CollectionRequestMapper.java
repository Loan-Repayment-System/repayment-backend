package com.repayment.domain.repayment.mapper;

import com.repayment.domain.repayment.dto.response.CollectionRequestResponse;
import com.repayment.global.code.CollectionRequestStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CollectionRequestMapper {

    int insertCollectionRequest(CollectionRequestResponse collectionRequest);

    /**
     * 상태가 from일 때만 to로 바꾼다. 이미 바뀐 행은 건드리지 않고 0을 돌려준다.
     */
    int updateCollectionRequestStatus(@Param("collectionRequestId") Long collectionRequestId,
                                      @Param("from") CollectionRequestStatus from,
                                      @Param("to") CollectionRequestStatus to);
}
