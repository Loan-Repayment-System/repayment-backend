-- ============================================================
-- V0: 초기 스키마 (설계: Notion 설계 › ERD (MVP))
-- 적용된 뒤에는 수정하지 않는다. 변경은 새 버전 파일로 추가한다.
-- 생성 순서: 참조하는 테이블이 먼저 생성되어야 FK 제약을 걸 수 있다.
--   코드성 -> 회원·계좌 -> 상품·대출·회차·기준금리 -> 납부 -> 판정·연체 -> 납부 상세 -> 일마감
-- ============================================================

-- ============================================================
-- 1. 코드성 테이블
-- id는 enum과 1:1이므로 AUTO_INCREMENT를 쓰지 않는다 (loan_judgment_status는 0부터).
-- ============================================================

CREATE TABLE interest_type (
    interest_type_id INT NOT NULL,
    name             VARCHAR(50) NOT NULL,
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (interest_type_id),
    UNIQUE KEY uq_interest_type_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE repayment_method (
    repayment_method_id INT NOT NULL,
    name                VARCHAR(50) NOT NULL,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (repayment_method_id),
    UNIQUE KEY uq_repayment_method_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE loan_status (
    loan_status_id INT NOT NULL,
    name           VARCHAR(50) NOT NULL,
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (loan_status_id),
    UNIQUE KEY uq_loan_status_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE repayment_schedule_status (
    repayment_schedule_status_id INT NOT NULL,
    name                         VARCHAR(50) NOT NULL,
    created_at                   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (repayment_schedule_status_id),
    UNIQUE KEY uq_repayment_schedule_status_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE loan_judgment_status (
    loan_judgment_status_id INT NOT NULL,
    name                    VARCHAR(50) NOT NULL,
    created_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (loan_judgment_status_id),
    UNIQUE KEY uq_loan_judgment_status_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE judgment_source (
    judgment_source_id INT NOT NULL,
    name               VARCHAR(50) NOT NULL,
    created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (judgment_source_id),
    UNIQUE KEY uq_judgment_source_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE delinquency_status (
    delinquency_status_id INT NOT NULL,
    name                  VARCHAR(50) NOT NULL,
    created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (delinquency_status_id),
    UNIQUE KEY uq_delinquency_status_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE account_status (
    account_status_id INT NOT NULL,
    name              VARCHAR(50) NOT NULL,
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (account_status_id),
    UNIQUE KEY uq_account_status_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE transaction_type (
    transaction_type_id INT NOT NULL,
    name                VARCHAR(50) NOT NULL,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (transaction_type_id),
    UNIQUE KEY uq_transaction_type_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE base_rate_index (
    base_rate_index_id INT NOT NULL,
    name               VARCHAR(50) NOT NULL,
    created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (base_rate_index_id),
    UNIQUE KEY uq_base_rate_index_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE repayment_channel (
    repayment_channel_id INT NOT NULL,
    name                 VARCHAR(50) NOT NULL,
    created_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (repayment_channel_id),
    UNIQUE KEY uq_repayment_channel_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE daily_closing_status (
    daily_closing_status_id INT NOT NULL,
    name                    VARCHAR(50) NOT NULL,
    created_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (daily_closing_status_id),
    UNIQUE KEY uq_daily_closing_status_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE failure_type (
    failure_type_id INT NOT NULL,
    name            VARCHAR(50) NOT NULL,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (failure_type_id),
    UNIQUE KEY uq_failure_type_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 2. 회원·계좌·이체
-- ============================================================

CREATE TABLE member (
    member_id  BIGINT NOT NULL AUTO_INCREMENT,
    login_id   VARCHAR(50) NOT NULL,
    password   VARCHAR(255) NOT NULL,
    name       VARCHAR(50) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (member_id),
    UNIQUE KEY uq_member_login_id (login_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- member_id가 NULL이면 은행 계좌(대출 상환 수납 계좌)다. balance는 이체만 바꾼다.
CREATE TABLE account (
    account_id        BIGINT NOT NULL AUTO_INCREMENT,
    member_id         BIGINT NULL,
    account_no        VARCHAR(20) NOT NULL,
    balance           DECIMAL(23,4) NOT NULL DEFAULT 0,
    account_status_id INT NOT NULL,
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (account_id),
    UNIQUE KEY uq_account_account_no (account_no),
    CONSTRAINT fk_account_member
        FOREIGN KEY (member_id) REFERENCES member (member_id),
    CONSTRAINT fk_account_account_status
        FOREIGN KEY (account_status_id) REFERENCES account_status (account_status_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 이체 1건에 출금·입금 2행이 같은 거래번호로 생긴다. 고치거나 지우지 않는다.
CREATE TABLE account_transaction (
    account_transaction_id BIGINT NOT NULL AUTO_INCREMENT,
    account_id             BIGINT NOT NULL,
    transaction_type_id    INT NOT NULL,
    transaction_no         VARCHAR(40) NOT NULL,
    amount                 DECIMAL(23,4) NOT NULL,
    balance_after          DECIMAL(23,4) NOT NULL,
    counterpart_account_no VARCHAR(20) NOT NULL,
    transacted_at          DATETIME NOT NULL,
    created_at             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (account_transaction_id),
    UNIQUE KEY uq_account_transaction_account_transaction_no (account_id, transaction_no),
    CONSTRAINT fk_account_transaction_account
        FOREIGN KEY (account_id) REFERENCES account (account_id),
    CONSTRAINT fk_account_transaction_transaction_type
        FOREIGN KEY (transaction_type_id) REFERENCES transaction_type (transaction_type_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 3. 상품·대출·회차·기준금리
-- ============================================================

CREATE TABLE loan_product (
    loan_product_id     BIGINT NOT NULL AUTO_INCREMENT,
    interest_type_id    INT NOT NULL,
    repayment_method_id INT NOT NULL,
    name                VARCHAR(100) NOT NULL,
    additional_rate     DECIMAL(10,5) NOT NULL,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (loan_product_id),
    CONSTRAINT fk_loan_product_interest_type
        FOREIGN KEY (interest_type_id) REFERENCES interest_type (interest_type_id),
    CONSTRAINT fk_loan_product_repayment_method
        FOREIGN KEY (repayment_method_id) REFERENCES repayment_method (repayment_method_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE loan_contract (
    loan_contract_id         BIGINT NOT NULL AUTO_INCREMENT,
    loan_product_id          BIGINT NOT NULL,
    member_id                BIGINT NOT NULL,
    contract_date            DATE NOT NULL,
    maturity_date            DATE NOT NULL,
    contract_principal       DECIMAL(23,4) NOT NULL,
    contract_additional_rate DECIMAL(10,5) NOT NULL,
    contract_base_rate       DECIMAL(10,5) NOT NULL,
    total_installments       INT NOT NULL,
    created_at               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (loan_contract_id),
    CONSTRAINT fk_loan_contract_loan_product
        FOREIGN KEY (loan_product_id) REFERENCES loan_product (loan_product_id),
    CONSTRAINT fk_loan_contract_member
        FOREIGN KEY (member_id) REFERENCES member (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- loan_status_id는 고객에게 보여 주는 현재 상태다. 판정 이력은 loan_status_judgment에 있다.
-- last_confirmed_installment_no는 가장 최근 확정 회차(0이면 없음)이며 상환금 확정만 올린다.
CREATE TABLE loan_account (
    loan_account_id               BIGINT NOT NULL AUTO_INCREMENT,
    loan_contract_id              BIGINT NOT NULL,
    repayment_account_id          BIGINT NOT NULL,
    account_no                    VARCHAR(20) NOT NULL,
    loan_status_id                INT NOT NULL,
    loan_principal_balance        DECIMAL(23,4) NOT NULL,
    monthly_payment_day           TINYINT NOT NULL,
    last_confirmed_installment_no INT NOT NULL DEFAULT 0,
    created_at                    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (loan_account_id),
    UNIQUE KEY uq_loan_account_loan_contract_id (loan_contract_id),
    UNIQUE KEY uq_loan_account_account_no (account_no),
    CONSTRAINT fk_loan_account_loan_contract
        FOREIGN KEY (loan_contract_id) REFERENCES loan_contract (loan_contract_id),
    CONSTRAINT fk_loan_account_repayment_account
        FOREIGN KEY (repayment_account_id) REFERENCES account (account_id),
    CONSTRAINT fk_loan_account_loan_status
        FOREIGN KEY (loan_status_id) REFERENCES loan_status (loan_status_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 확정 전에는 금액·적용 금리·confirmed_at이 NULL이다. paid_*는 누적이다.
CREATE TABLE repayment_schedule (
    repayment_schedule_id        BIGINT NOT NULL AUTO_INCREMENT,
    loan_account_id              BIGINT NOT NULL,
    installment_no               INT NOT NULL,
    repayment_schedule_status_id INT NOT NULL,
    scheduled_date               DATE NOT NULL,
    scheduled_principal          DECIMAL(23,4) NULL,
    scheduled_interest           DECIMAL(23,4) NULL,
    applied_base_rate            DECIMAL(10,5) NULL,
    applied_additional_rate      DECIMAL(10,5) NULL,
    confirmed_at                 DATETIME NULL,
    paid_principal               DECIMAL(23,4) NOT NULL DEFAULT 0,
    paid_interest                DECIMAL(23,4) NOT NULL DEFAULT 0,
    created_at                   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (repayment_schedule_id),
    UNIQUE KEY uq_repayment_schedule_loan_account_installment_no (loan_account_id, installment_no),
    CONSTRAINT fk_repayment_schedule_loan_account
        FOREIGN KEY (loan_account_id) REFERENCES loan_account (loan_account_id),
    CONSTRAINT fk_repayment_schedule_status
        FOREIGN KEY (repayment_schedule_status_id) REFERENCES repayment_schedule_status (repayment_schedule_status_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 공시일마다 1행. 덮어쓰지 않는다.
CREATE TABLE base_rate_history (
    base_rate_history_id BIGINT NOT NULL AUTO_INCREMENT,
    base_rate_index_id   INT NOT NULL,
    announced_date       DATE NOT NULL,
    rate                 DECIMAL(10,5) NOT NULL,
    created_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (base_rate_history_id),
    UNIQUE KEY uq_base_rate_history_index_announced_date (base_rate_index_id, announced_date),
    CONSTRAINT fk_base_rate_history_base_rate_index
        FOREIGN KEY (base_rate_index_id) REFERENCES base_rate_index (base_rate_index_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 4. 납부
-- ============================================================

-- 상환 요청 1건에 1행. transaction_no로 account_transaction과 이어진다(FK 아님). 고치거나 지우지 않는다.
CREATE TABLE repayment (
    repayment_id         BIGINT NOT NULL AUTO_INCREMENT,
    loan_account_id      BIGINT NOT NULL,
    repayment_channel_id INT NOT NULL,
    request_key          VARCHAR(64) NOT NULL,
    paid_amount          DECIMAL(23,4) NOT NULL,
    paid_at              DATETIME NOT NULL,
    transaction_no       VARCHAR(40) NOT NULL,
    created_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (repayment_id),
    UNIQUE KEY uq_repayment_request_key (request_key),
    CONSTRAINT fk_repayment_loan_account
        FOREIGN KEY (loan_account_id) REFERENCES loan_account (loan_account_id),
    CONSTRAINT fk_repayment_repayment_channel
        FOREIGN KEY (repayment_channel_id) REFERENCES repayment_channel (repayment_channel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 5. 상태 판정·연체
-- ============================================================

-- 판정할 때마다 1행을 쌓고 고치지 않는다(일마감 3단계, 상환 6단계).
-- 직전 판정 = 그 대출의 가장 최근 judged_at 행.
CREATE TABLE loan_status_judgment (
    loan_status_judgment_id   BIGINT NOT NULL AUTO_INCREMENT,
    loan_account_id           BIGINT NOT NULL,
    business_date             DATE NOT NULL,
    loan_judgment_status_id   INT NOT NULL,
    judgment_source_id        INT NOT NULL,
    repayment_id              BIGINT NULL,
    overdue_installment_count INT NULL,
    oldest_overdue_date       DATE NULL,
    debtor_protection_applied BOOLEAN NOT NULL,
    judged_at                 DATETIME NOT NULL,
    created_at                DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (loan_status_judgment_id),
    KEY idx_loan_status_judgment_loan_account_judged_at (loan_account_id, judged_at),
    CONSTRAINT fk_loan_status_judgment_loan_account
        FOREIGN KEY (loan_account_id) REFERENCES loan_account (loan_account_id),
    CONSTRAINT fk_loan_status_judgment_loan_judgment_status
        FOREIGN KEY (loan_judgment_status_id) REFERENCES loan_judgment_status (loan_judgment_status_id),
    CONSTRAINT fk_loan_status_judgment_judgment_source
        FOREIGN KEY (judgment_source_id) REFERENCES judgment_source (judgment_source_id),
    CONSTRAINT fk_loan_status_judgment_repayment
        FOREIGN KEY (repayment_id) REFERENCES repayment (repayment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 회차 단위 연체(근거 1·5)의 하루 연체금. 계산 근거는 고치지 않고 paid_*·상태만 바뀐다.
CREATE TABLE installment_delinquency_charge (
    installment_delinquency_charge_id BIGINT NOT NULL AUTO_INCREMENT,
    repayment_schedule_id             BIGINT NOT NULL,
    charge_date                       DATE NOT NULL,
    loan_judgment_status_id           INT NOT NULL,
    delinquency_target_amount         DECIMAL(23,4) NOT NULL,
    delinquency_rate                  DECIMAL(10,5) NOT NULL,
    delinquency_charge                DECIMAL(23,4) NOT NULL,
    paid_delinquency_charge           DECIMAL(23,4) NOT NULL DEFAULT 0,
    delinquency_status_id             INT NOT NULL,
    created_at                        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (installment_delinquency_charge_id),
    UNIQUE KEY uq_installment_delinquency_charge_schedule_charge_date (repayment_schedule_id, charge_date),
    CONSTRAINT fk_installment_delinquency_charge_repayment_schedule
        FOREIGN KEY (repayment_schedule_id) REFERENCES repayment_schedule (repayment_schedule_id),
    CONSTRAINT fk_installment_delinquency_charge_loan_judgment_status
        FOREIGN KEY (loan_judgment_status_id) REFERENCES loan_judgment_status (loan_judgment_status_id),
    CONSTRAINT fk_installment_delinquency_charge_delinquency_status
        FOREIGN KEY (delinquency_status_id) REFERENCES delinquency_status (delinquency_status_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 기한이익상실(근거 2·3·6·7) 대출당 1행. 전환할 때 밀린 금액을 carried_*로 옮겨 적는다.
CREATE TABLE loan_acceleration (
    loan_acceleration_id            BIGINT NOT NULL AUTO_INCREMENT,
    loan_account_id                 BIGINT NOT NULL,
    converted_date                  DATE NOT NULL,
    applied_interest_rate           DECIMAL(10,5) NOT NULL,
    carried_interest                DECIMAL(23,4) NOT NULL,
    paid_carried_interest           DECIMAL(23,4) NOT NULL DEFAULT 0,
    carried_overdue_principal       DECIMAL(23,4) NOT NULL,
    paid_carried_overdue_principal  DECIMAL(23,4) NOT NULL DEFAULT 0,
    carried_delinquency_charge      DECIMAL(23,4) NOT NULL,
    paid_carried_delinquency_charge DECIMAL(23,4) NOT NULL DEFAULT 0,
    created_at                      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (loan_acceleration_id),
    UNIQUE KEY uq_loan_acceleration_loan_account_id (loan_account_id),
    CONSTRAINT fk_loan_acceleration_loan_account
        FOREIGN KEY (loan_account_id) REFERENCES loan_account (loan_account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 기한이익상실 대출의 하루 기록. interest_amount는 하루 약정이자(3·7 기한이익상실+❻만).
CREATE TABLE acceleration_daily_charge (
    acceleration_daily_charge_id BIGINT NOT NULL AUTO_INCREMENT,
    loan_acceleration_id         BIGINT NOT NULL,
    charge_date                  DATE NOT NULL,
    loan_judgment_status_id      INT NOT NULL,
    delinquency_target_amount    DECIMAL(23,4) NOT NULL,
    delinquency_rate             DECIMAL(10,5) NOT NULL,
    delinquency_charge           DECIMAL(23,4) NOT NULL,
    paid_delinquency_charge      DECIMAL(23,4) NOT NULL DEFAULT 0,
    interest_amount              DECIMAL(23,4) NOT NULL DEFAULT 0,
    paid_interest                DECIMAL(23,4) NOT NULL DEFAULT 0,
    delinquency_status_id        INT NOT NULL,
    created_at                   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (acceleration_daily_charge_id),
    UNIQUE KEY uq_acceleration_daily_charge_acceleration_charge_date (loan_acceleration_id, charge_date),
    CONSTRAINT fk_acceleration_daily_charge_loan_acceleration
        FOREIGN KEY (loan_acceleration_id) REFERENCES loan_acceleration (loan_acceleration_id),
    CONSTRAINT fk_acceleration_daily_charge_loan_judgment_status
        FOREIGN KEY (loan_judgment_status_id) REFERENCES loan_judgment_status (loan_judgment_status_id),
    CONSTRAINT fk_acceleration_daily_charge_delinquency_status
        FOREIGN KEY (delinquency_status_id) REFERENCES delinquency_status (delinquency_status_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 만기 경과(근거 4) 대출당 1행. loan_acceleration과 컬럼 구성이 같다.
CREATE TABLE loan_maturity_overdue (
    loan_maturity_overdue_id        BIGINT NOT NULL AUTO_INCREMENT,
    loan_account_id                 BIGINT NOT NULL,
    converted_date                  DATE NOT NULL,
    applied_interest_rate           DECIMAL(10,5) NOT NULL,
    carried_interest                DECIMAL(23,4) NOT NULL,
    paid_carried_interest           DECIMAL(23,4) NOT NULL DEFAULT 0,
    carried_overdue_principal       DECIMAL(23,4) NOT NULL,
    paid_carried_overdue_principal  DECIMAL(23,4) NOT NULL DEFAULT 0,
    carried_delinquency_charge      DECIMAL(23,4) NOT NULL,
    paid_carried_delinquency_charge DECIMAL(23,4) NOT NULL DEFAULT 0,
    created_at                      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (loan_maturity_overdue_id),
    UNIQUE KEY uq_loan_maturity_overdue_loan_account_id (loan_account_id),
    CONSTRAINT fk_loan_maturity_overdue_loan_account
        FOREIGN KEY (loan_account_id) REFERENCES loan_account (loan_account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 만기 경과 대출의 하루 기록. interest_amount는 항상 0이다.
CREATE TABLE maturity_overdue_daily_charge (
    maturity_overdue_daily_charge_id BIGINT NOT NULL AUTO_INCREMENT,
    loan_maturity_overdue_id         BIGINT NOT NULL,
    charge_date                      DATE NOT NULL,
    loan_judgment_status_id          INT NOT NULL,
    delinquency_target_amount        DECIMAL(23,4) NOT NULL,
    delinquency_rate                 DECIMAL(10,5) NOT NULL,
    delinquency_charge               DECIMAL(23,4) NOT NULL,
    paid_delinquency_charge          DECIMAL(23,4) NOT NULL DEFAULT 0,
    interest_amount                  DECIMAL(23,4) NOT NULL DEFAULT 0,
    paid_interest                    DECIMAL(23,4) NOT NULL DEFAULT 0,
    delinquency_status_id            INT NOT NULL,
    created_at                       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (maturity_overdue_daily_charge_id),
    UNIQUE KEY uq_maturity_overdue_daily_charge_overdue_charge_date (loan_maturity_overdue_id, charge_date),
    CONSTRAINT fk_maturity_overdue_daily_charge_loan_maturity_overdue
        FOREIGN KEY (loan_maturity_overdue_id) REFERENCES loan_maturity_overdue (loan_maturity_overdue_id),
    CONSTRAINT fk_maturity_overdue_daily_charge_loan_judgment_status
        FOREIGN KEY (loan_judgment_status_id) REFERENCES loan_judgment_status (loan_judgment_status_id),
    CONSTRAINT fk_maturity_overdue_daily_charge_delinquency_status
        FOREIGN KEY (delinquency_status_id) REFERENCES delinquency_status (delinquency_status_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 6. 납부 상세 — 납부 한 건의 상세는 대출 상태에 따라 셋 중 하나에 남는다.
-- ============================================================

CREATE TABLE installment_repayment (
    installment_repayment_id BIGINT NOT NULL AUTO_INCREMENT,
    repayment_id             BIGINT NOT NULL,
    repayment_schedule_id    BIGINT NOT NULL,
    paid_principal           DECIMAL(23,4) NOT NULL DEFAULT 0,
    paid_interest            DECIMAL(23,4) NOT NULL DEFAULT 0,
    paid_delinquency_charge  DECIMAL(23,4) NOT NULL DEFAULT 0,
    created_at               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (installment_repayment_id),
    UNIQUE KEY uq_installment_repayment_repayment_schedule (repayment_id, repayment_schedule_id),
    CONSTRAINT fk_installment_repayment_repayment
        FOREIGN KEY (repayment_id) REFERENCES repayment (repayment_id),
    CONSTRAINT fk_installment_repayment_repayment_schedule
        FOREIGN KEY (repayment_schedule_id) REFERENCES repayment_schedule (repayment_schedule_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE acceleration_repayment (
    acceleration_repayment_id BIGINT NOT NULL AUTO_INCREMENT,
    repayment_id              BIGINT NOT NULL,
    loan_acceleration_id      BIGINT NOT NULL,
    paid_principal            DECIMAL(23,4) NOT NULL DEFAULT 0,
    paid_interest             DECIMAL(23,4) NOT NULL DEFAULT 0,
    paid_delinquency_charge   DECIMAL(23,4) NOT NULL DEFAULT 0,
    created_at                DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (acceleration_repayment_id),
    UNIQUE KEY uq_acceleration_repayment_repayment_id (repayment_id),
    CONSTRAINT fk_acceleration_repayment_repayment
        FOREIGN KEY (repayment_id) REFERENCES repayment (repayment_id),
    CONSTRAINT fk_acceleration_repayment_loan_acceleration
        FOREIGN KEY (loan_acceleration_id) REFERENCES loan_acceleration (loan_acceleration_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE maturity_overdue_repayment (
    maturity_overdue_repayment_id BIGINT NOT NULL AUTO_INCREMENT,
    repayment_id                  BIGINT NOT NULL,
    loan_maturity_overdue_id      BIGINT NOT NULL,
    paid_principal                DECIMAL(23,4) NOT NULL DEFAULT 0,
    paid_interest                 DECIMAL(23,4) NOT NULL DEFAULT 0,
    paid_delinquency_charge       DECIMAL(23,4) NOT NULL DEFAULT 0,
    created_at                    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (maturity_overdue_repayment_id),
    UNIQUE KEY uq_maturity_overdue_repayment_repayment_id (repayment_id),
    CONSTRAINT fk_maturity_overdue_repayment_repayment
        FOREIGN KEY (repayment_id) REFERENCES repayment (repayment_id),
    CONSTRAINT fk_maturity_overdue_repayment_loan_maturity_overdue
        FOREIGN KEY (loan_maturity_overdue_id) REFERENCES loan_maturity_overdue (loan_maturity_overdue_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 7. 일마감·처리 실패
-- ============================================================

-- 기준일마다 1행. 오늘 행이 '완료'일 때만 상환을 받는다.
CREATE TABLE daily_closing (
    business_date           DATE NOT NULL,
    daily_closing_status_id INT NOT NULL,
    started_at              DATETIME NOT NULL,
    finished_at             DATETIME NULL,
    created_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (business_date),
    CONSTRAINT fk_daily_closing_daily_closing_status
        FOREIGN KEY (daily_closing_status_id) REFERENCES daily_closing_status (daily_closing_status_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 실패할 때마다 1행. 실패 내용은 고치지 않고, 풀리면 resolved_at·resolution_note만 한 번 채운다.
-- batch_job_execution_id는 BATCH_JOB_EXECUTION을 가리키지만 V2보다 먼저 만들어지므로 FK를 걸지 않는다.
CREATE TABLE processing_failure (
    processing_failure_id  BIGINT NOT NULL AUTO_INCREMENT,
    failure_type_id        INT NOT NULL,
    business_date          DATE NOT NULL,
    loan_account_id        BIGINT NULL,
    repayment_schedule_id  BIGINT NULL,
    reason                 VARCHAR(1000) NOT NULL,
    batch_job_execution_id BIGINT NULL,
    occurred_at            DATETIME NOT NULL,
    resolved_at            DATETIME NULL,
    resolution_note        VARCHAR(1000) NULL,
    created_at             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (processing_failure_id),
    CONSTRAINT fk_processing_failure_failure_type
        FOREIGN KEY (failure_type_id) REFERENCES failure_type (failure_type_id),
    CONSTRAINT fk_processing_failure_loan_account
        FOREIGN KEY (loan_account_id) REFERENCES loan_account (loan_account_id),
    CONSTRAINT fk_processing_failure_repayment_schedule
        FOREIGN KEY (repayment_schedule_id) REFERENCES repayment_schedule (repayment_schedule_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
