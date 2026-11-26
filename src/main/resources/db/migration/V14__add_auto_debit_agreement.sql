-- ============================================================
-- V14: 자동 이체 약정 테이블
-- 자동 이체 상환은 약정이 사용 중이고 기준일이 약정 기간 안인 대출만 출금한다.
-- ============================================================

CREATE TABLE auto_debit_agreement_status (
    auto_debit_agreement_status_id INT NOT NULL,
    name                           VARCHAR(50) NOT NULL,
    created_at                     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (auto_debit_agreement_status_id),
    UNIQUE KEY uq_auto_debit_agreement_status_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO auto_debit_agreement_status (auto_debit_agreement_status_id, name) VALUES
    (1, '사용'),
    (2, '정지'),
    (3, '해지');

-- 대출 하나에 약정 하나다. 출금 계좌는 대출의 상환 계좌를 쓴다.
-- end_date가 NULL이면 기한 없이 유효하다. 상태와 end_date만 바꾸고, 행은 지우지 않는다.
CREATE TABLE auto_debit_agreement (
    auto_debit_agreement_id        BIGINT NOT NULL AUTO_INCREMENT,
    loan_account_id                BIGINT NOT NULL,
    auto_debit_agreement_status_id INT NOT NULL,
    start_date                     DATE NOT NULL,
    end_date                       DATE NULL,
    created_at                     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (auto_debit_agreement_id),
    UNIQUE KEY uq_auto_debit_agreement_loan_account_id (loan_account_id),
    CONSTRAINT fk_auto_debit_agreement_loan_account
        FOREIGN KEY (loan_account_id) REFERENCES loan_account (loan_account_id),
    CONSTRAINT fk_auto_debit_agreement_auto_debit_agreement_status
        FOREIGN KEY (auto_debit_agreement_status_id) REFERENCES auto_debit_agreement_status (auto_debit_agreement_status_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
