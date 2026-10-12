package com.repayment.global.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 처리 실패 종류. 코드성 테이블 failure_type의 id와 1:1이다.
 */
@Getter
@RequiredArgsConstructor
public enum FailureType implements CodeEnum {

    BASE_RATE_FETCH_FAILED(1, "기준금리 조회 오류"),
    SCHEDULE_JUDGMENT_FAILED(2, "대출 스케줄 판정 실패"),
    LOAN_STATUS_JUDGMENT_FAILED(3, "대출 상태 판정 실패"),
    STATUS_CHANGE_PROCESSING_FAILED(4, "상태 변화 처리 실패"),
    BATCH_ABORTED(5, "배치 중단");

    private final int id;
    private final String description;
}
