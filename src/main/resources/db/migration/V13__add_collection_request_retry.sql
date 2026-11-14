-- ============================================================
-- V13: 집금 요청 재요청 시도 기록
-- 요청·집금 완료로 남은 집금 요청은 배치가 다음 시도 시각마다 다시 시도한다.
-- 시도할 때마다 시도 횟수를 1 올리고 다음 시도 시각을 늦춘다.
-- ============================================================

ALTER TABLE collection_request
    ADD COLUMN retry_count   INT NOT NULL DEFAULT 0 AFTER amount,
    ADD COLUMN next_retry_at DATETIME NOT NULL AFTER retry_count;
