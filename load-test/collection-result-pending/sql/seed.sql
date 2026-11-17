-- ============================================================
-- 펌뱅킹 장애 부하 테스트용 상환 DB 더미 대출 3만 건.
-- 앱이 뜬 뒤(Flyway가 테이블을 만든 뒤) 넣고, 일마감 수동 실행 API로 1회차를 확정한다.
-- 로컬 시드(V900)와 같이 계약일 = 오늘 + 3일 − 1개월이라 일마감이 1회차를 확정한다.
-- 대출 n의 상환 계좌는 300-000-00000n이고, 펌뱅킹 더미 계좌와 맞춘다.
-- ============================================================

SET SESSION cte_max_recursion_depth = 30000;
SET @loan_count = 30000;
SET @contract_date = DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 3 DAY), INTERVAL 1 MONTH);

INSERT INTO member (member_id, login_id, password, name) VALUES
    (1, 'loadtest', 'loadtest-password', '부하테스트');

-- 고정금리라 회차를 확정할 때 공시 기준금리를 찾지 않는다.
INSERT INTO loan_product (loan_product_id, interest_type_id, repayment_method_id, name, additional_rate) VALUES
    (1, 1, 2, '부하 테스트 고정금리 원리금균등', 2.00000);

INSERT INTO loan_contract (loan_contract_id, loan_product_id, member_id, contract_date, maturity_date,
                           contract_principal, contract_additional_rate, contract_base_rate, total_installments)
WITH RECURSIVE seq (n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < @loan_count
)
SELECT n, 1, 1, @contract_date, DATE_ADD(@contract_date, INTERVAL 12 MONTH), 12000000, 2.00000, 3.00000, 12
FROM seq;

INSERT INTO loan_account (loan_account_id, loan_contract_id, repayment_bank_code, repayment_account_no, account_no,
                          loan_status_id, loan_principal_balance, monthly_payment_day)
SELECT loan_contract_id, loan_contract_id, '999',
       CONCAT('300-', LPAD(loan_contract_id DIV 1000000, 3, '0'), '-', LPAD(loan_contract_id MOD 1000000, 6, '0')),
       CONCAT('910-', LPAD(loan_contract_id DIV 1000000, 3, '0'), '-', LPAD(loan_contract_id MOD 1000000, 6, '0')),
       1, 12000000, DAY(@contract_date)
FROM loan_contract;

-- 개설과 같이 예정일 = 계약일 + n개월이고, 모두 예정·미확정이다.
INSERT INTO repayment_schedule (loan_account_id, installment_no, repayment_schedule_status_id, scheduled_date)
WITH RECURSIVE installment (installment_no) AS (
    SELECT 1
    UNION ALL
    SELECT installment_no + 1 FROM installment WHERE installment_no < 12
)
SELECT la.loan_account_id, i.installment_no, 1, DATE_ADD(lc.contract_date, INTERVAL i.installment_no MONTH)
FROM loan_account la
    JOIN loan_contract lc ON lc.loan_contract_id = la.loan_contract_id
    CROSS JOIN installment i;
