-- ============================================================
-- V4: 대출 상태 판정(loan_status_judgment) 컬럼 정리
-- 판정 행은 (대출, 기준일, 판정 상태)만 남긴다. 직전 판정은 그 대출의 가장 큰 loan_status_judgment_id 행이다.
-- 판정 주체·상환 연결·판정 입력값·판정 일시는 읽는 곳이 없어 지운다.
-- ============================================================

ALTER TABLE loan_status_judgment
    DROP FOREIGN KEY fk_loan_status_judgment_judgment_source,
    DROP FOREIGN KEY fk_loan_status_judgment_repayment;

ALTER TABLE loan_status_judgment
    ADD KEY idx_loan_status_judgment_loan_account_business_date (loan_account_id, business_date);

ALTER TABLE loan_status_judgment
    DROP INDEX idx_loan_status_judgment_loan_account_judged_at,
    DROP COLUMN judgment_source_id,
    DROP COLUMN repayment_id,
    DROP COLUMN overdue_installment_count,
    DROP COLUMN oldest_overdue_date,
    DROP COLUMN debtor_protection_applied,
    DROP COLUMN judged_at;

DROP TABLE judgment_source;
