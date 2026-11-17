-- ============================================================
-- 펌뱅킹 장애 부하 테스트 정합성 확인. 복구 구간이 끝난 뒤 상환 DB에서 실행한다.
-- 집금 요청과 상환은 transaction_no(전송일자 yyyyMMdd + 전문번호 6자리)로 이어진다.
-- ============================================================

-- 1. 집금 요청 상태별 건수. 요청(1)·집금 완료(2)가 0건이어야 한다.
SELECT s.name AS status, COUNT(cr.collection_request_id) AS count
FROM collection_request_status s
    LEFT JOIN collection_request cr ON cr.collection_request_status_id = s.collection_request_status_id
GROUP BY s.collection_request_status_id, s.name
ORDER BY s.collection_request_status_id;

-- 2. 상환 완료 요청과 상환의 건수·금액이 같아야 한다.
-- 3. 상환 완료 요청의 건수·금액은 펌뱅킹 정합성 SQL의 이체 성공 건수·금액과 같아야 한다.
SELECT 'collection_request(상환 완료)' AS source, COUNT(*) AS count, COALESCE(SUM(amount), 0) AS amount
FROM collection_request
WHERE collection_request_status_id = 3
UNION ALL
SELECT 'repayment', COUNT(*), COALESCE(SUM(paid_amount), 0)
FROM repayment;

-- 4. 대출마다 상환은 1건 이하여야 한다. 0행이어야 한다.
SELECT loan_account_id, COUNT(*) AS repayment_count
FROM repayment
GROUP BY loan_account_id
HAVING COUNT(*) > 1;

-- 5. 상환 완료 요청마다 같은 transaction_no의 상환이 있어야 한다. 0행이어야 한다.
SELECT cr.collection_request_id, cr.loan_account_id, cr.amount
FROM collection_request cr
    LEFT JOIN repayment r
        ON r.transaction_no = CONCAT(DATE_FORMAT(cr.send_date, '%Y%m%d'), LPAD(cr.telegram_no, 6, '0'))
WHERE cr.collection_request_status_id = 3
  AND r.repayment_id IS NULL;
