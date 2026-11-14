-- ============================================================
-- V13: 집금 요청 재요청 시도 기록
-- 요청·집금 완료로 남은 집금 요청은 배치가 다음 시도 시각마다 다시 시도한다.
-- 시도할 때마다 시도 횟수를 1 올리고 다음 시도 시각을 늦춘다.
-- ============================================================

ALTER TABLE collection_request
    ADD COLUMN retry_count   INT NOT NULL DEFAULT 0 AFTER amount,
    ADD COLUMN next_retry_at DATETIME NULL AFTER retry_count;

-- 이미 있는 행은 새로 저장하는 행과 같이 전송 시각 1분 뒤로 채운다.
UPDATE collection_request
SET next_retry_at = DATE_ADD(sent_at, INTERVAL 1 MINUTE);

ALTER TABLE collection_request
    MODIFY COLUMN next_retry_at DATETIME NOT NULL,
    ADD INDEX idx_collection_request_status_next_retry_at (collection_request_status_id, next_retry_at);
