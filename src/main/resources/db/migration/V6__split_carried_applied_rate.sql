-- ============================================================
-- V6: 기한이익상실·만기 경과의 적용 금리를 기준금리와 가산금리로 나눈다
-- 회차(repayment_schedule)처럼 적용 기준금리와 적용 가산금리를 따로 남긴다.
-- 두 테이블 모두 아직 행이 없다.
-- ============================================================

ALTER TABLE loan_acceleration
    DROP COLUMN applied_interest_rate,
    ADD COLUMN applied_base_rate       DECIMAL(10,5) NOT NULL AFTER converted_date,
    ADD COLUMN applied_additional_rate DECIMAL(10,5) NOT NULL AFTER applied_base_rate;

ALTER TABLE loan_maturity_overdue
    DROP COLUMN applied_interest_rate,
    ADD COLUMN applied_base_rate       DECIMAL(10,5) NOT NULL AFTER converted_date,
    ADD COLUMN applied_additional_rate DECIMAL(10,5) NOT NULL AFTER applied_base_rate;
