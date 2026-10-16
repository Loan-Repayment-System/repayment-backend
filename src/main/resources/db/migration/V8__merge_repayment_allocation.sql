-- ============================================================
-- V8: 충당 상세를 테이블 하나로 합친다
-- 갚을 금액 목록 한 줄(대상 종류, 대상 행 id, 금액)을 채운 만큼 그대로 1행으로 남긴다.
-- 지우는 세 테이블은 아직 행이 없다.
-- ============================================================

-- 충당 대상 종류. 십의 자리로 묶는다: 0 회차, 1 기한이익상실, 2 만기 경과
CREATE TABLE repayment_target_type (
    repayment_target_type_id INT NOT NULL,
    name                     VARCHAR(50) NOT NULL,
    created_at               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (repayment_target_type_id),
    UNIQUE KEY uq_repayment_target_type_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO repayment_target_type (repayment_target_type_id, name) VALUES
    (1, '회차 연체금'),
    (2, '회차 이자'),
    (3, '회차 원금'),
    (11, '기한이익상실 옮긴 연체금'),
    (12, '기한이익상실 하루 연체금'),
    (13, '기한이익상실 옮긴 이자'),
    (14, '기한이익상실 하루 약정이자'),
    (15, '기한이익상실 옮긴 밀린 원금'),
    (16, '기한이익상실 나머지 원금'),
    (21, '만기 경과 옮긴 연체금'),
    (22, '만기 경과 하루 연체금'),
    (23, '만기 경과 옮긴 이자'),
    (24, '만기 경과 원금잔액');

-- 상환 1건이 대상 하나에 충당한 금액. paid_amount는 이 상환의 금액이라 누적이 아니다. 고치거나 지우지 않는다.
-- target_id는 종류마다 가리키는 테이블이 달라 FK를 걸지 않는다.
CREATE TABLE repayment_allocation (
    repayment_allocation_id  BIGINT NOT NULL AUTO_INCREMENT,
    repayment_id             BIGINT NOT NULL,
    repayment_target_type_id INT NOT NULL,
    target_id                BIGINT NOT NULL,
    paid_amount              DECIMAL(23,4) NOT NULL,
    created_at               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (repayment_allocation_id),
    UNIQUE KEY uq_repayment_allocation_repayment_target (repayment_id, repayment_target_type_id, target_id),
    CONSTRAINT fk_repayment_allocation_repayment
        FOREIGN KEY (repayment_id) REFERENCES repayment (repayment_id),
    CONSTRAINT fk_repayment_allocation_repayment_target_type
        FOREIGN KEY (repayment_target_type_id) REFERENCES repayment_target_type (repayment_target_type_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE installment_repayment;
DROP TABLE acceleration_repayment;
DROP TABLE maturity_overdue_repayment;
