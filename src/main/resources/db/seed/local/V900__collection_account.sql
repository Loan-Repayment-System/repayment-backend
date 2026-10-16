-- 은행 수납 계좌(member_id NULL). 계좌번호는 application.yml의 repayment.collection-account-no와 같다.
INSERT INTO account (member_id, account_no, balance, account_status_id)
VALUES (NULL, '100-000-000001', 0, 1);
