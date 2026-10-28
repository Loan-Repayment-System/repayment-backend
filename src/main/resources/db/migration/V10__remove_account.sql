-- ============================================================
-- V10: 계좌·거래 테이블을 없앤다
-- 이체는 외부 펌뱅킹 서버가 맡고, 계좌와 잔액도 그 서버가 가진다.
-- 대출의 상환 계좌는 FK 대신 은행코드와 계좌번호 값으로 둔다.
-- 펌뱅킹 은행코드가 정해지기 전이라 기존 행의 은행코드는 임시값 999로 채운다.
-- ============================================================

ALTER TABLE loan_account
    ADD COLUMN repayment_bank_code  CHAR(3)     NULL AFTER loan_contract_id,
    ADD COLUMN repayment_account_no VARCHAR(20) NULL AFTER repayment_bank_code;

UPDATE loan_account la
    JOIN account a ON a.account_id = la.repayment_account_id
SET la.repayment_bank_code  = '999',
    la.repayment_account_no = a.account_no;

ALTER TABLE loan_account
    DROP FOREIGN KEY fk_loan_account_repayment_account;

ALTER TABLE loan_account
    DROP COLUMN repayment_account_id,
    MODIFY COLUMN repayment_bank_code  CHAR(3)     NOT NULL,
    MODIFY COLUMN repayment_account_no VARCHAR(20) NOT NULL;

DROP TABLE account_transaction;
DROP TABLE account;
DROP TABLE transaction_type;
DROP TABLE account_status;
