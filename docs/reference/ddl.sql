-- ============================================================
-- LoanServicing 물리적 모델링 DDL
-- 생성 순서: 코드성 테이블 -> 핵심 테이블 -> 운영 테이블 -> 상환 테이블
-- (참조하는 테이블이 먼저 생성되어야 FK 제약 설정 가능)
-- ============================================================

-- ============================================================
-- 1. 코드성 테이블
-- ============================================================

DROP TABLE IF EXISTS interest_type;
CREATE TABLE interest_type (
    interest_type_id INT NOT NULL AUTO_INCREMENT,
    name             VARCHAR(50) NOT NULL,
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (interest_type_id),
    UNIQUE KEY uq_interest_type_name (name)
);

DROP TABLE IF EXISTS repayment_method;
CREATE TABLE repayment_method (
    repayment_method_id INT NOT NULL AUTO_INCREMENT,
    name                VARCHAR(50) NOT NULL,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (repayment_method_id),
    UNIQUE KEY uq_repayment_method_name (name)
);

DROP TABLE IF EXISTS loan_status;
CREATE TABLE loan_status (
    loan_status_id INT NOT NULL AUTO_INCREMENT,
    name           VARCHAR(50) NOT NULL,
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (loan_status_id),
    UNIQUE KEY uq_loan_status_name (name)
);

DROP TABLE IF EXISTS repayment_schedule_status;
CREATE TABLE repayment_schedule_status (
    repayment_schedule_status_id INT NOT NULL AUTO_INCREMENT,
    name                          VARCHAR(50) NOT NULL,
    created_at                    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (repayment_schedule_status_id),
    UNIQUE KEY uq_repayment_schedule_status_name (name)
);

DROP TABLE IF EXISTS delinquency_status;
CREATE TABLE delinquency_status (
    delinquency_status_id INT NOT NULL AUTO_INCREMENT,
    name                   VARCHAR(50) NOT NULL,
    created_at             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (delinquency_status_id),
    UNIQUE KEY uq_delinquency_status_name (name)
);

DROP TABLE IF EXISTS delinquency_reason;
CREATE TABLE delinquency_reason (
    delinquency_reason_id INT NOT NULL AUTO_INCREMENT,
    name                   VARCHAR(50) NOT NULL,
    created_at             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (delinquency_reason_id),
    UNIQUE KEY uq_delinquency_reason_name (name)
);

-- ============================================================
-- 2. 핵심 테이블
-- ============================================================

DROP TABLE IF EXISTS member;
CREATE TABLE member (
    member_id  BIGINT NOT NULL AUTO_INCREMENT,
    login_id   VARCHAR(50) NOT NULL,
    password   VARCHAR(255) NOT NULL,
    name       VARCHAR(50) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (member_id),
    UNIQUE KEY uq_member_login_id (login_id)
);

DROP TABLE IF EXISTS primary_account;
CREATE TABLE primary_account (
    primary_account_id BIGINT NOT NULL AUTO_INCREMENT,
    member_id           BIGINT NOT NULL,
    account_no          VARCHAR(20) NOT NULL,
    balance             DECIMAL(23,4) NOT NULL DEFAULT 0,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (primary_account_id),
    UNIQUE KEY uq_primary_account_member_id (member_id),
    UNIQUE KEY uq_primary_account_account_no (account_no),
    CONSTRAINT fk_primary_account_member
        FOREIGN KEY (member_id) REFERENCES member (member_id)
);

DROP TABLE IF EXISTS loan_product;
CREATE TABLE loan_product (
    loan_product_id      BIGINT NOT NULL AUTO_INCREMENT,
    interest_type_id     INT NOT NULL,
    repayment_method_id  INT NOT NULL,
    name                 VARCHAR(100) NOT NULL,
    additional_rate      DECIMAL(10,5) NOT NULL,
    created_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (loan_product_id),
    CONSTRAINT fk_loan_product_interest_type
        FOREIGN KEY (interest_type_id) REFERENCES interest_type (interest_type_id),
    CONSTRAINT fk_loan_product_repayment_method
        FOREIGN KEY (repayment_method_id) REFERENCES repayment_method (repayment_method_id)
);

-- ============================================================
-- 3. 운영 테이블
-- ============================================================

DROP TABLE IF EXISTS loan_contract;
CREATE TABLE loan_contract (
    loan_contract_id    BIGINT NOT NULL AUTO_INCREMENT,
    loan_product_id     BIGINT NOT NULL,
    member_id           BIGINT NOT NULL,
    contract_date            DATE NOT NULL,
    maturity_date            DATE NOT NULL,
    contract_principal       DECIMAL(23,4) NOT NULL,
    contract_additional_rate DECIMAL(10,5) NOT NULL,
    contract_base_rate       DECIMAL(10,5) NOT NULL,
    total_installments        INT NOT NULL,
    created_at               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (loan_contract_id),
    CONSTRAINT fk_loan_contract_loan_product
        FOREIGN KEY (loan_product_id) REFERENCES loan_product (loan_product_id),
    CONSTRAINT fk_loan_contract_member
        FOREIGN KEY (member_id) REFERENCES member (member_id)
);

DROP TABLE IF EXISTS loan_account;
CREATE TABLE loan_account (
    loan_account_id          BIGINT NOT NULL AUTO_INCREMENT,
    loan_contract_id         BIGINT NOT NULL,
    primary_account_id       BIGINT NOT NULL,
    account_no                VARCHAR(20) NOT NULL,
    loan_principal_balance   DECIMAL(23,4) NOT NULL,
    monthly_payment_day      TINYINT NOT NULL,
    current_installment_no   INT NOT NULL DEFAULT 1,
    created_at               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (loan_account_id),
    UNIQUE KEY uq_loan_account_loan_contract_id (loan_contract_id),
    UNIQUE KEY uq_loan_account_account_no (account_no),
    CONSTRAINT fk_loan_account_loan_contract
        FOREIGN KEY (loan_contract_id) REFERENCES loan_contract (loan_contract_id),
    CONSTRAINT fk_loan_account_primary_account
        FOREIGN KEY (primary_account_id) REFERENCES primary_account (primary_account_id)
);

DROP TABLE IF EXISTS loan_status_history;
CREATE TABLE loan_status_history (
    loan_status_history_id BIGINT NOT NULL AUTO_INCREMENT,
    loan_account_id          BIGINT NOT NULL,
    loan_status_id            INT NOT NULL,
    start_date                DATE NOT NULL,
    end_date                  DATE NULL,
    created_at                DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (loan_status_history_id),
    CONSTRAINT fk_loan_status_history_loan_account
        FOREIGN KEY (loan_account_id) REFERENCES loan_account (loan_account_id),
    CONSTRAINT fk_loan_status_history_loan_status
        FOREIGN KEY (loan_status_id) REFERENCES loan_status (loan_status_id)
);

-- ============================================================
-- 4. 상환 테이블
-- ============================================================

DROP TABLE IF EXISTS repayment_schedule;
CREATE TABLE repayment_schedule (
    repayment_schedule_id          BIGINT NOT NULL AUTO_INCREMENT,
    loan_account_id                  BIGINT NOT NULL,
    repayment_schedule_status_id     INT NOT NULL,
    scheduled_at                     DATETIME NOT NULL,
    scheduled_principal              DECIMAL(23,4) NULL,
    scheduled_interest               DECIMAL(23,4) NULL,
    applied_additional_rate          DECIMAL(10,5) NULL,
    applied_base_rate                DECIMAL(10,5) NULL,
    created_at                       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (repayment_schedule_id),
    CONSTRAINT fk_repayment_schedule_loan_account
        FOREIGN KEY (loan_account_id) REFERENCES loan_account (loan_account_id),
    CONSTRAINT fk_repayment_schedule_status
        FOREIGN KEY (repayment_schedule_status_id) REFERENCES repayment_schedule_status (repayment_schedule_status_id)
);

DROP TABLE IF EXISTS delinquency_history;
CREATE TABLE delinquency_history (
    delinquency_history_id  BIGINT NOT NULL AUTO_INCREMENT,
    delinquency_status_id     INT NOT NULL,
    delinquency_reason_id     INT NOT NULL,
    repayment_schedule_id     BIGINT NOT NULL,
    delinquent_at              DATETIME NOT NULL,
    delinquent_rate            DECIMAL(10,5) NOT NULL,
    delinquency_charge         DECIMAL(23,4) NOT NULL,
    created_at                 DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                 DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (delinquency_history_id),
    CONSTRAINT fk_delinquency_history_status
        FOREIGN KEY (delinquency_status_id) REFERENCES delinquency_status (delinquency_status_id),
    CONSTRAINT fk_delinquency_history_reason
        FOREIGN KEY (delinquency_reason_id) REFERENCES delinquency_reason (delinquency_reason_id),
    CONSTRAINT fk_delinquency_history_repayment_schedule
        FOREIGN KEY (repayment_schedule_id) REFERENCES repayment_schedule (repayment_schedule_id)
);

DROP TABLE IF EXISTS repayment_history;
CREATE TABLE repayment_history (
    repayment_history_id      BIGINT NOT NULL AUTO_INCREMENT,
    repayment_schedule_id     BIGINT NOT NULL,
    delinquency_history_id    BIGINT NULL,
    paid_principal             DECIMAL(23,4) NOT NULL,
    paid_interest              DECIMAL(23,4) NOT NULL,
    paid_delinquency_charge   DECIMAL(23,4) NOT NULL DEFAULT 0,
    paid_at                    DATETIME NOT NULL,
    created_at                 DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                 DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (repayment_history_id),
    CONSTRAINT fk_repayment_history_repayment_schedule
        FOREIGN KEY (repayment_schedule_id) REFERENCES repayment_schedule (repayment_schedule_id),
    CONSTRAINT fk_repayment_history_delinquency_history
        FOREIGN KEY (delinquency_history_id) REFERENCES delinquency_history (delinquency_history_id)
);

-- ============================================================
-- 5. 코드성 테이블 초기 데이터
-- (enum id와 1:1 대응 — id 순서 변경 금지)
-- ============================================================

INSERT INTO interest_type (interest_type_id, name) VALUES
    (1, '고정금리'),
    (2, '변동금리');

INSERT INTO repayment_method (repayment_method_id, name) VALUES
    (1, '원금만기일시상환'),
    (2, '원리금균등상환'),
    (3, '원금균등상환');

INSERT INTO loan_status (loan_status_id, name) VALUES
    (1, '정상'),
    (2, '연체'),
    (3, '완제'),
    (4, '기한이익상실');

INSERT INTO repayment_schedule_status (repayment_schedule_status_id, name) VALUES
    (1, '예정'),
    (2, '납부 기한 도래'),
    (3, '납입 완료'),
    (4, '연체');

INSERT INTO delinquency_status (delinquency_status_id, name) VALUES
    (1, '진행중'),
    (2, '해소');

INSERT INTO delinquency_reason (delinquency_reason_id, name) VALUES
    (1, '만기일시 + 이자연체 + 1개월 미만'),
    (2, '만기일시 + 이자연체 + 1개월 이상'),
    (3, '만기일시 + 이자연체 + 1개월 이상 + ⑥항 적용'),
    (4, '만기일시 + 원금연체'),
    (5, '분할상환 + 연속 1회'),
    (6, '분할상환 + 연속 2회 이상'),
    (7, '분할상환 + 연속 2회 이상 + ⑥항 적용');
