-- ============================================================
-- V5: 판정 상태(loan_judgment_status) 번호 재편
-- 십의 자리 = 상환 방법(1 만기일시, 2 분할상환), 일의 자리 = 단계(1 회차 연체, 2 기한이익상실, 3 기한이익상실 ❻항).
-- 한 자리는 상환 방법과 상관없는 상태다(0 정상, 1 만기 경과 미상환, 2 완제).
-- 이전 → 새: 0→0, 1→11, 2→12, 3→13, 4→1, 5→21, 6→22, 7→23, 8→2
-- ============================================================

SET FOREIGN_KEY_CHECKS = 0;

-- 새 번호와 겹치지 않게 먼저 100번대로 옮긴 뒤 새 번호를 매긴다
UPDATE loan_judgment_status
SET loan_judgment_status_id = loan_judgment_status_id + 100;

UPDATE loan_judgment_status
SET loan_judgment_status_id = CASE loan_judgment_status_id
    WHEN 100 THEN 0
    WHEN 101 THEN 11
    WHEN 102 THEN 12
    WHEN 103 THEN 13
    WHEN 104 THEN 1
    WHEN 105 THEN 21
    WHEN 106 THEN 22
    WHEN 107 THEN 23
    WHEN 108 THEN 2
END;

UPDATE loan_status_judgment
SET loan_judgment_status_id = CASE loan_judgment_status_id
    WHEN 0 THEN 0 WHEN 1 THEN 11 WHEN 2 THEN 12 WHEN 3 THEN 13 WHEN 4 THEN 1
    WHEN 5 THEN 21 WHEN 6 THEN 22 WHEN 7 THEN 23 WHEN 8 THEN 2
END;

UPDATE installment_delinquency_charge
SET loan_judgment_status_id = CASE loan_judgment_status_id
    WHEN 1 THEN 11 WHEN 5 THEN 21
END;

UPDATE acceleration_daily_charge
SET loan_judgment_status_id = CASE loan_judgment_status_id
    WHEN 2 THEN 12 WHEN 3 THEN 13 WHEN 6 THEN 22 WHEN 7 THEN 23
END;

UPDATE maturity_overdue_daily_charge
SET loan_judgment_status_id = CASE loan_judgment_status_id
    WHEN 4 THEN 1
END;

SET FOREIGN_KEY_CHECKS = 1;
