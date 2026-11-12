-- ============================================================
-- V11: 펌뱅킹 전문번호 채번 테이블
-- 펌뱅킹은 (업체번호, 전송일자, 전문번호)가 같으면 같은 거래로 본다.
-- 전송일자마다 마지막으로 쓴 전문번호를 1행에 두고, 보낼 때마다 1씩 올린다.
-- ============================================================

CREATE TABLE telegram_no_sequence (
    send_date        DATE NOT NULL,
    last_telegram_no INT NOT NULL,
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (send_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
