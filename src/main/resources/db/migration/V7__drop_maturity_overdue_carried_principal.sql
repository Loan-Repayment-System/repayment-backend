-- ============================================================
-- V7: 만기 경과의 옮긴 밀린 원금을 없앤다
-- 만기가 지나면 모든 회차의 예정일이 지나 '연체' 회차의 남은 원금 합이 원금잔액과 같다.
-- 같은 값을 두 곳에서 관리하지 않도록 원금은 loan_account.loan_principal_balance만 본다.
-- 테이블에 아직 행이 없다.
-- ============================================================

ALTER TABLE loan_maturity_overdue
    DROP COLUMN carried_overdue_principal,
    DROP COLUMN paid_carried_overdue_principal;
