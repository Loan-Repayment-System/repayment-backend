-- ============================================================
-- 펌뱅킹 장애 부하 테스트 정합성 확인. 복구 구간이 끝난 뒤 펌뱅킹 DB에서 실행한다.
-- ============================================================

-- 1. 집금(2) 이체 성공 건수·금액. 상환 DB verify.sql 2번의 상환 완료 요청과 같아야 한다.
SELECT COUNT(*) AS count, COALESCE(SUM(amount), 0) AS amount
FROM transfer
WHERE transfer_type_id = 2
  AND response_code = '0000';

-- 2. 고객 계좌마다 집금 성공은 1건 이하여야 한다. 0행이어야 한다.
SELECT withdrawal_account_no, COUNT(*) AS success_count
FROM transfer
WHERE transfer_type_id = 2
  AND response_code = '0000'
GROUP BY withdrawal_account_no
HAVING COUNT(*) > 1;
