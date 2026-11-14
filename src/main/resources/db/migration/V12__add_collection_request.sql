-- ============================================================
-- V12: 집금 요청 테이블
-- 상환은 집금 요청을 먼저 저장하고 커밋한 뒤 펌뱅킹에 집금을 보낸다.
-- 응답을 받지 못하거나 상환 반영에 실패해도 보낸 내용이 남아, 같은 키로 다시 보내거나 반영을 다시 할 수 있다.
-- ============================================================

CREATE TABLE collection_request_status (
    collection_request_status_id INT NOT NULL,
    name                         VARCHAR(50) NOT NULL,
    created_at                   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (collection_request_status_id),
    UNIQUE KEY uq_collection_request_status_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO collection_request_status (collection_request_status_id, name) VALUES
    (1, '요청'),
    (2, '집금 완료'),
    (3, '상환 완료'),
    (4, '거절');

-- 펌뱅킹은 (업체번호, 전송일자, 전문번호)가 같으면 같은 거래로 본다. 다시 보낼 때 같은 값을 쓰도록 남긴다.
-- 상태만 바꾸고, 행은 지우지 않는다.
CREATE TABLE collection_request (
    collection_request_id        BIGINT NOT NULL AUTO_INCREMENT,
    loan_account_id              BIGINT NOT NULL,
    repayment_channel_id         INT NOT NULL,
    collection_request_status_id INT NOT NULL,
    send_date                    DATE NOT NULL,
    telegram_no                  INT NOT NULL,
    sent_at                      DATETIME NOT NULL,
    withdrawal_bank_code         CHAR(3)     NOT NULL,
    withdrawal_account_no        VARCHAR(20) NOT NULL,
    amount                       DECIMAL(23,4) NOT NULL,
    created_at                   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (collection_request_id),
    UNIQUE KEY uq_collection_request_send_date_telegram_no (send_date, telegram_no),
    CONSTRAINT fk_collection_request_loan_account
        FOREIGN KEY (loan_account_id) REFERENCES loan_account (loan_account_id),
    CONSTRAINT fk_collection_request_repayment_channel
        FOREIGN KEY (repayment_channel_id) REFERENCES repayment_channel (repayment_channel_id),
    CONSTRAINT fk_collection_request_collection_request_status
        FOREIGN KEY (collection_request_status_id) REFERENCES collection_request_status (collection_request_status_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
