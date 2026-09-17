-- ============================================================
-- V1: 코드성 테이블 초기 데이터 (원본: docs/reference/ddl.sql 5번 섹션)
-- enum id와 1:1 대응 — id 순서 변경 금지. 모든 환경에 필요하므로 seed가 아니라 migration에 둔다.
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
