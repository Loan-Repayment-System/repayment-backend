package com.repayment.global.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 집금 요청 상태. 코드성 테이블 collection_request_status의 id와 1:1이다.
 * REQUESTED는 응답을 받지 못해 펌뱅킹이 처리했는지 모르는 건도 포함한다.
 */
@Getter
@RequiredArgsConstructor
public enum CollectionRequestStatus implements CodeEnum {

    REQUESTED(1, "요청"),
    COLLECTED(2, "집금 완료"),
    COMPLETED(3, "상환 완료"),
    FAILED(4, "거절");

    private final int id;
    private final String description;
}
