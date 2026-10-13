-- ============================================================
-- V3: 기준금리 지표 테이블(base_rate_index) 제거
-- 기준금리는 KORIBOR 12개월 하나만 쓰므로 지표 구분을 없앤다. base_rate_history는 KORIBOR 12개월 금리 이력이다.
-- ============================================================

ALTER TABLE base_rate_history
    DROP FOREIGN KEY fk_base_rate_history_base_rate_index;

ALTER TABLE base_rate_history
    DROP INDEX uq_base_rate_history_index_announced_date,
    DROP COLUMN base_rate_index_id,
    ADD UNIQUE KEY uq_base_rate_history_announced_date (announced_date);

DROP TABLE base_rate_index;
