-- ============================================================
-- V900: 로컬 상환 확인용 대출 (local 프로필만)
-- 상환 계좌는 펌뱅킹(firmbanking-mock) local 시드의 고객 계좌다.
--   900-000-000001 → 200-000-000001 (잔액 1천만): 집금 성공, 펌뱅킹 꺼짐
--   900-000-000002 → 200-000-000002 (잔액 1만): 잔액 부족(B003)
--   900-000-000003 → 200-000-000003 (출금 동의 없음): C001
-- 계약일은 시드가 적용되는 날 + 3일 − 1개월이라 1회차 예정일이 3일 뒤다.
-- 그날 일마감을 실행하면 1회차가 확정되어 납부 기한 도래가 된다.
-- 날짜가 지나면 맞지 않으므로 docker compose down -v로 DB를 새로 만들어 쓴다.
-- ============================================================

SET @contract_date = DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 3 DAY), INTERVAL 1 MONTH);

INSERT INTO member (member_id, login_id, password, name) VALUES
    (1, 'kimcs', 'local-password', '김철수'),
    (2, 'leeyh', 'local-password', '이영희'),
    (3, 'namgung', 'local-password', '남궁가나다라마바');

-- 고정금리라 회차를 확정할 때 공시 기준금리를 찾지 않는다.
INSERT INTO loan_product (loan_product_id, interest_type_id, repayment_method_id, name, additional_rate) VALUES
    (1, 1, 2, '로컬 고정금리 원리금균등', 2.00000);

INSERT INTO loan_contract (loan_contract_id, loan_product_id, member_id, contract_date, maturity_date,
                           contract_principal, contract_additional_rate, contract_base_rate, total_installments) VALUES
    (1, 1, 1, @contract_date, DATE_ADD(@contract_date, INTERVAL 12 MONTH), 12000000, 2.00000, 3.00000, 12),
    (2, 1, 2, @contract_date, DATE_ADD(@contract_date, INTERVAL 12 MONTH), 12000000, 2.00000, 3.00000, 12),
    (3, 1, 3, @contract_date, DATE_ADD(@contract_date, INTERVAL 12 MONTH), 12000000, 2.00000, 3.00000, 12);

INSERT INTO loan_account (loan_account_id, loan_contract_id, repayment_bank_code, repayment_account_no, account_no,
                          loan_status_id, loan_principal_balance, monthly_payment_day) VALUES
    (1, 1, '999', '200-000-000001', '900-000-000001', 1, 12000000, DAY(@contract_date)),
    (2, 2, '999', '200-000-000002', '900-000-000002', 1, 12000000, DAY(@contract_date)),
    (3, 3, '999', '200-000-000003', '900-000-000003', 1, 12000000, DAY(@contract_date));

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
