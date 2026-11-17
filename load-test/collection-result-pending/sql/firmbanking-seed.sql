-- ============================================================
-- 펌뱅킹 장애 부하 테스트용 펌뱅킹 DB 더미 고객 계좌 3만 개와 캐피탈 출금 동의.
-- 펌뱅킹이 뜬 뒤(Flyway가 테이블을 만든 뒤) 넣는다.
-- 상환 DB 시드(seed.sql)의 대출 n과 짝이다.
--   고객 계좌 = 대출 n의 상환 계좌 300-000-00000n
--   납부자번호 = 대출 n의 계좌번호 910-000-00000n
-- 잔액은 상환 1건(1만 원)보다 넉넉히 둔다. 이중 집금이 잔액 부족으로 가려지지 않게 한다.
-- ============================================================

SET SESSION cte_max_recursion_depth = 30000;
SET @account_count = 30000;

INSERT INTO account (bank_code, account_no, holder_name, holder_birth_date, balance)
WITH RECURSIVE seq (n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < @account_count
)
SELECT '999', CONCAT('300-', LPAD(n DIV 1000000, 3, '0'), '-', LPAD(n MOD 1000000, 6, '0')),
       '부하테스트', '900101', 1000000
FROM seq;

INSERT INTO withdrawal_consent (company_no, account_id, consent_status_id, payer_no, applied_date)
SELECT 'CAPITAL00001', account_id, 1, CONCAT('910-', SUBSTRING(account_no, 5)), DATE_FORMAT(CURDATE(), '%Y%m%d')
FROM account
WHERE bank_code = '999'
  AND account_no LIKE '300-%';
