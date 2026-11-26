-- ============================================================
-- V901: 로컬 자동 이체 약정 (local 프로필만)
-- V900의 대출 3건 모두 약정을 사용 중으로 두어 집금 성공, 잔액 부족, 출금 동의 없음을 함께 확인한다.
-- ============================================================

INSERT INTO auto_debit_agreement (loan_account_id, auto_debit_agreement_status_id, start_date, end_date) VALUES
    (1, 1, CURDATE(), NULL),
    (2, 1, CURDATE(), NULL),
    (3, 1, CURDATE(), NULL);
