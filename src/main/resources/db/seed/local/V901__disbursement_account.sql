-- 은행 지급 계좌(member_id NULL). 계좌번호는 application.yml의 repayment.disbursement-account-no와 같다.
-- 대출금 지급이 잔액 부족으로 막히지 않도록 1조 원을 넣어 둔다.
INSERT INTO account (member_id, account_no, balance, account_status_id)
VALUES (NULL, '100-000-000002', 1000000000000, 1);
