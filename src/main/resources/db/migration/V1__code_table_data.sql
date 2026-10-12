-- ============================================================
-- V1: 코드성 테이블 초기 데이터
-- enum id와 1:1 대응 — id 순서 변경 금지. 모든 환경에 필요하므로 seed가 아니라 migration에 둔다.
-- ============================================================

INSERT INTO interest_type (interest_type_id, name) VALUES
    (1, '고정금리'),
    (2, '변동금리');

INSERT INTO repayment_method (repayment_method_id, name) VALUES
    (1, '원금만기일시상환'),
    (2, '원리금균등상환'),
    (3, '원금균등상환');

-- 고객에게 보여 주는 대출 상태
INSERT INTO loan_status (loan_status_id, name) VALUES
    (1, '정상'),
    (2, '연체'),
    (3, '완제'),
    (4, '기한이익상실'),
    (5, '만기 경과');

INSERT INTO repayment_schedule_status (repayment_schedule_status_id, name) VALUES
    (1, '예정'),
    (2, '납부 기한 도래'),
    (3, '납입 완료'),
    (4, '연체'),
    (5, '기한이익상실로 종료'),
    (6, '만기 경과로 종료');

-- 판정 상태 — id가 판정 번호와 같다 (0 정상, 1~7 연체 근거, 8 완제)
INSERT INTO loan_judgment_status (loan_judgment_status_id, name) VALUES
    (0, '정상'),
    (1, '만기일시 이자연체 1개월 미만'),
    (2, '만기일시 기한이익상실'),
    (3, '만기일시 기한이익상실 ❻항'),
    (4, '만기 경과 미상환'),
    (5, '분할상환 1회 연체'),
    (6, '분할상환 기한이익상실'),
    (7, '분할상환 기한이익상실 ❻항'),
    (8, '완제');

INSERT INTO judgment_source (judgment_source_id, name) VALUES
    (1, '일마감'),
    (2, '상환');

INSERT INTO delinquency_status (delinquency_status_id, name) VALUES
    (1, '진행중'),
    (2, '해소');

INSERT INTO account_status (account_status_id, name) VALUES
    (1, '정상'),
    (2, '해지'),
    (3, '정지');

INSERT INTO transaction_type (transaction_type_id, name) VALUES
    (1, '출금'),
    (2, '입금');

INSERT INTO base_rate_index (base_rate_index_id, name) VALUES
    (1, 'KORIBOR 12개월');

INSERT INTO repayment_channel (repayment_channel_id, name) VALUES
    (1, '고객 상환'),
    (2, '자동 상환');

INSERT INTO daily_closing_status (daily_closing_status_id, name) VALUES
    (1, '진행중'),
    (2, '완료'),
    (3, '실패');

INSERT INTO failure_type (failure_type_id, name) VALUES
    (1, '기준금리 조회 오류'),
    (2, '대출 스케줄 판정 실패'),
    (3, '대출 상태 판정 실패'),
    (4, '상태 변화 처리 실패'),
    (5, '배치 중단');
