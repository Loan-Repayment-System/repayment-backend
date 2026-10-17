-- ============================================================
-- V9: daily_closing에 별도 식별자를 둔다
-- 다른 테이블처럼 {테이블}_id를 PK로 쓰고, 기준일은 하루 1행을 지키는 UNIQUE로 둔다.
-- 테이블에 아직 행이 없다.
-- ============================================================

ALTER TABLE daily_closing
    DROP PRIMARY KEY,
    ADD COLUMN daily_closing_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST,
    ADD CONSTRAINT uq_daily_closing_business_date UNIQUE (business_date);
