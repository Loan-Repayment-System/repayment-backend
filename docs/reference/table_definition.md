# 테이블 정의서

> 스키마의 정본은 `src/main/resources/db/migration/`의 마이그레이션이다(`V0__init.sql`부터 차례로 적용). 스키마를 바꾸면 이 문서를 함께 고친다.
> 공통 컬럼 `created_at`, `updated_at`(DATETIME, DB가 채움)은 모든 테이블에 있어 표에서 생략한다.

---

## 코드성 테이블

12개 모두 구조가 같다. id는 enum과 1:1이므로 AUTO_INCREMENT를 쓰지 않는다.

| No. | 컬럼 영문명 | 데이터 타입 | 제약조건 | NULL | 비고 |
|---|---|---|---|---|---|
| 1 | `{테이블명}_id` | INT | PK | N | enum과 1:1 |
| 2 | name | VARCHAR(50) | UQ | N | |

| 테이블 | 한글명 | 값 (id: 이름) |
|---|---|---|
| interest_type | 이자종류 | 1 고정금리, 2 변동금리 |
| repayment_method | 상환방법 | 1 원금만기일시상환, 2 원리금균등상환, 3 원금균등상환 |
| loan_status | 대출상태 (고객 표시) | 1 정상, 2 연체, 3 완제, 4 기한이익상실, 5 만기 경과 |
| repayment_schedule_status | 회차 상태 | 1 예정, 2 납부 기한 도래, 3 납입 완료, 4 연체, 5 기한이익상실로 종료, 6 만기 경과로 종료 |
| loan_judgment_status | 판정 상태 | 0 정상, 1 만기 경과 미상환, 2 완제, 11 만기일시 이자연체 1개월 미만, 12 만기일시 기한이익상실, 13 만기일시 기한이익상실 ❻항, 21 분할상환 1회 연체, 22 분할상환 기한이익상실, 23 분할상환 기한이익상실 ❻항 |
| delinquency_status | 연체금 상태 | 1 진행중, 2 해소 |
| account_status | 계좌 상태 | 1 정상, 2 해지, 3 정지 |
| transaction_type | 거래 유형 | 1 출금, 2 입금 |
| repayment_channel | 상환 채널 | 1 고객 상환, 2 자동 상환 |
| daily_closing_status | 일마감 상태 | 1 진행중, 2 완료, 3 실패 |
| failure_type | 실패 종류 | 1 기준금리 조회 오류, 2 대출 스케줄 판정 실패, 3 대출 상태 판정 실패, 4 상태 변화 처리 실패, 5 배치 중단 |
| repayment_target_type | 충당 대상 종류 | 1 회차 연체금, 2 회차 이자, 3 회차 원금, 11 기한이익상실 옮긴 연체금, 12 기한이익상실 하루 연체금, 13 기한이익상실 옮긴 이자, 14 기한이익상실 하루 약정이자, 15 기한이익상실 옮긴 밀린 원금, 16 기한이익상실 나머지 원금, 21 만기 경과 옮긴 연체금, 22 만기 경과 하루 연체금, 23 만기 경과 옮긴 이자, 24 만기 경과 원금잔액 |

---

## 회원·계좌·이체

### 회원 (member)

| No. | 컬럼 한글명 | 컬럼 영문명 | 데이터 타입 | 제약조건 | NULL | 기본값 | 비고 |
|---|---|---|---|---|---|---|---|
| 1 | 회원ID | member_id | BIGINT | PK | N | AUTO_INCREMENT | |
| 2 | 로그인ID | login_id | VARCHAR(50) | UQ | N | | |
| 3 | 비밀번호 | password | VARCHAR(255) | | N | | 해시값 저장 |
| 4 | 이름 | name | VARCHAR(50) | | N | | |

### 계좌 (account)

| No. | 컬럼 한글명 | 컬럼 영문명 | 데이터 타입 | 제약조건 | NULL | 기본값 | 비고 |
|---|---|---|---|---|---|---|---|
| 1 | 계좌ID | account_id | BIGINT | PK | N | AUTO_INCREMENT | |
| 2 | 회원ID | member_id | BIGINT | FK | Y | | NULL이면 은행 계좌(대출 상환 수납 계좌) |
| 3 | 계좌번호 | account_no | VARCHAR(20) | UQ | N | | |
| 4 | 잔액 | balance | DECIMAL(23,4) | | N | 0 | 이체만 바꾼다 |
| 5 | 계좌 상태 | account_status_id | INT | FK | N | | 정상·해지·정지 |

### 계좌 거래 기록 (account_transaction)

이체 1건에 출금·입금 2행이 같은 거래번호로 생긴다. 고치거나 지우지 않는다.

| No. | 컬럼 한글명 | 컬럼 영문명 | 데이터 타입 | 제약조건 | NULL | 기본값 | 비고 |
|---|---|---|---|---|---|---|---|
| 1 | 거래ID | account_transaction_id | BIGINT | PK | N | AUTO_INCREMENT | |
| 2 | 계좌ID | account_id | BIGINT | FK, UQ① | N | | |
| 3 | 거래 유형 | transaction_type_id | INT | FK | N | | 출금·입금 |
| 4 | 거래번호 | transaction_no | VARCHAR(40) | UQ① | N | | 요청한 쪽이 넘긴 값 |
| 5 | 금액 | amount | DECIMAL(23,4) | | N | | |
| 6 | 거래 후 잔액 | balance_after | DECIMAL(23,4) | | N | | |
| 7 | 상대 계좌번호 | counterpart_account_no | VARCHAR(20) | | N | | |
| 8 | 거래 일시 | transacted_at | DATETIME | | N | | |

> UQ① (account_id, transaction_no)

---

## 상품·대출·회차·기준금리

### 대출상품 (loan_product)

| No. | 컬럼 한글명 | 컬럼 영문명 | 데이터 타입 | 제약조건 | NULL | 기본값 | 비고 |
|---|---|---|---|---|---|---|---|
| 1 | 상품ID | loan_product_id | BIGINT | PK | N | AUTO_INCREMENT | |
| 2 | 이자종류 | interest_type_id | INT | FK | N | | |
| 3 | 상환방법 | repayment_method_id | INT | FK | N | | |
| 4 | 상품명 | name | VARCHAR(100) | | N | | |
| 5 | 가산금리 | additional_rate | DECIMAL(10,5) | | N | | 단위: % |

### 대출계약 (loan_contract)

| No. | 컬럼 한글명 | 컬럼 영문명 | 데이터 타입 | 제약조건 | NULL | 기본값 | 비고 |
|---|---|---|---|---|---|---|---|
| 1 | 계약ID | loan_contract_id | BIGINT | PK | N | AUTO_INCREMENT | |
| 2 | 상품ID | loan_product_id | BIGINT | FK | N | | |
| 3 | 회원ID | member_id | BIGINT | FK | N | | |
| 4 | 계약일 | contract_date | DATE | | N | | |
| 5 | 만기일 | maturity_date | DATE | | N | | |
| 6 | 약정금액 | contract_principal | DECIMAL(23,4) | | N | | 최초 원금 P0 |
| 7 | 약정 가산금리 | contract_additional_rate | DECIMAL(10,5) | | N | | |
| 8 | 약정 기준금리 | contract_base_rate | DECIMAL(10,5) | | N | | 고정금리에서 사용 |
| 9 | 총 회차 | total_installments | INT | | N | | |

### 대출계좌 (loan_account)

| No. | 컬럼 한글명 | 컬럼 영문명 | 데이터 타입 | 제약조건 | NULL | 기본값 | 비고 |
|---|---|---|---|---|---|---|---|
| 1 | 대출계좌ID | loan_account_id | BIGINT | PK | N | AUTO_INCREMENT | |
| 2 | 계약ID | loan_contract_id | BIGINT | FK, UQ | N | | 1:1 |
| 3 | 상환 계좌ID | repayment_account_id | BIGINT | FK | N | | account.account_id |
| 4 | 대출계좌번호 | account_no | VARCHAR(20) | UQ | N | | |
| 5 | 대출 상태 | loan_status_id | INT | FK | N | | 고객에게 보여 주는 현재 상태. 판정에는 쓰지 않는다 |
| 6 | 원금잔액 | loan_principal_balance | DECIMAL(23,4) | | N | | |
| 7 | 월 납입일 | monthly_payment_day | TINYINT | | N | | |
| 8 | 마지막 확정 회차 | last_confirmed_installment_no | INT | | N | 0 | 0이면 없음. 상환금 확정만 올린다 |

### 상환 스케줄 (repayment_schedule)

| No. | 컬럼 한글명 | 컬럼 영문명 | 데이터 타입 | 제약조건 | NULL | 기본값 | 비고 |
|---|---|---|---|---|---|---|---|
| 1 | 회차ID | repayment_schedule_id | BIGINT | PK | N | AUTO_INCREMENT | |
| 2 | 대출계좌ID | loan_account_id | BIGINT | FK, UQ① | N | | |
| 3 | 회차 번호 | installment_no | INT | UQ① | N | | |
| 4 | 회차 상태 | repayment_schedule_status_id | INT | FK | N | | |
| 5 | 예정일 | scheduled_date | DATE | | N | | |
| 6 | 예정 원금 | scheduled_principal | DECIMAL(23,4) | | Y | | 확정 전 NULL |
| 7 | 예정 이자 | scheduled_interest | DECIMAL(23,4) | | Y | | 확정 전 NULL |
| 8 | 적용 기준금리 | applied_base_rate | DECIMAL(10,5) | | Y | | 확정 전 NULL |
| 9 | 적용 가산금리 | applied_additional_rate | DECIMAL(10,5) | | Y | | 확정 전 NULL |
| 10 | 확정 일시 | confirmed_at | DATETIME | | Y | | NULL이면 미확정 |
| 11 | 낸 원금 | paid_principal | DECIMAL(23,4) | | N | 0 | 누적 |
| 12 | 낸 이자 | paid_interest | DECIMAL(23,4) | | N | 0 | 누적 |

> UQ① (loan_account_id, installment_no)

### 기준금리 이력 (base_rate_history)

| No. | 컬럼 한글명 | 컬럼 영문명 | 데이터 타입 | 제약조건 | NULL | 기본값 | 비고 |
|---|---|---|---|---|---|---|---|
| 1 | 이력ID | base_rate_history_id | BIGINT | PK | N | AUTO_INCREMENT | |
| 2 | 공시일 | announced_date | DATE | UQ | N | | |
| 3 | 금리 | rate | DECIMAL(10,5) | | N | | 단위: % |

> KORIBOR 12개월 금리만 담는다. 공시일마다 1행이고 덮어쓰지 않는다.

---

## 상태 판정·연체

### 대출 상태 판정 (loan_status_judgment)

판정할 때마다 1행을 쌓고 고치지 않는다(일마감, 상환). 직전 판정은 그 대출의 가장 큰 `loan_status_judgment_id` 행이다.

| No. | 컬럼 한글명 | 컬럼 영문명 | 데이터 타입 | 제약조건 | NULL | 기본값 | 비고 |
|---|---|---|---|---|---|---|---|
| 1 | 판정ID | loan_status_judgment_id | BIGINT | PK | N | AUTO_INCREMENT | |
| 2 | 대출계좌ID | loan_account_id | BIGINT | FK, IDX① | N | | |
| 3 | 기준일 | business_date | DATE | IDX① | N | | 상환 판정이면 상환한 날 |
| 4 | 판정 상태 | loan_judgment_status_id | INT | FK | N | | 0 정상, 1 만기 경과, 2 완제, 11~13 만기일시, 21~23 분할상환 |

> IDX① (loan_account_id, business_date)

### 회차 연체금 (installment_delinquency_charge)

회차 단위 연체(판정 11·21)의 하루 연체금. 계산 근거는 고치지 않는다.

| No. | 컬럼 한글명 | 컬럼 영문명 | 데이터 타입 | 제약조건 | NULL | 기본값 | 비고 |
|---|---|---|---|---|---|---|---|
| 1 | 연체금ID | installment_delinquency_charge_id | BIGINT | PK | N | AUTO_INCREMENT | |
| 2 | 회차ID | repayment_schedule_id | BIGINT | FK, UQ① | N | | |
| 3 | 연체금 날짜 | charge_date | DATE | UQ① | N | | |
| 4 | 근거 | loan_judgment_status_id | INT | FK | N | | 11·21 |
| 5 | 대상 금액 | delinquency_target_amount | DECIMAL(23,4) | | N | | |
| 6 | 연체 이자율 | delinquency_rate | DECIMAL(10,5) | | N | | |
| 7 | 연체금 | delinquency_charge | DECIMAL(23,4) | | N | | |
| 8 | 낸 연체금 | paid_delinquency_charge | DECIMAL(23,4) | | N | 0 | 누적 |
| 9 | 상태 | delinquency_status_id | INT | FK | N | | 진행중·해소 |

> UQ① (repayment_schedule_id, charge_date)

### 기한이익상실 (loan_acceleration) / 만기 경과 (loan_maturity_overdue)

대출당 1행. 전환할 때 밀린 금액을 `carried_*`로 옮겨 적고 낸 만큼 `paid_*`에 더한다. 8·9는 기한이익상실에만 있다(만기 경과는 밀린 원금이 원금잔액과 같아 두지 않는다).

| No. | 컬럼 한글명 | 컬럼 영문명 | 데이터 타입 | 제약조건 | NULL | 기본값 | 비고 |
|---|---|---|---|---|---|---|---|
| 1 | ID | loan_acceleration_id / loan_maturity_overdue_id | BIGINT | PK | N | AUTO_INCREMENT | |
| 2 | 대출계좌ID | loan_account_id | BIGINT | FK, UQ | N | | |
| 3 | 전환일 | converted_date | DATE | | N | | |
| 4 | 적용 기준금리 | applied_base_rate | DECIMAL(10,5) | | N | | 기한이익상실: 가장 오래 밀린 회차, 만기 경과: 마지막 회차 |
| 5 | 적용 가산금리 | applied_additional_rate | DECIMAL(10,5) | | N | | 4와 같은 회차 |
| 6 | 옮긴 이자 | carried_interest | DECIMAL(23,4) | | N | | |
| 7 | 낸 옮긴 이자 | paid_carried_interest | DECIMAL(23,4) | | N | 0 | |
| 8 | 옮긴 밀린 원금 | carried_overdue_principal | DECIMAL(23,4) | | N | | 기한이익상실만 |
| 9 | 낸 옮긴 밀린 원금 | paid_carried_overdue_principal | DECIMAL(23,4) | | N | 0 | 기한이익상실만 |
| 10 | 옮긴 연체금 | carried_delinquency_charge | DECIMAL(23,4) | | N | | |
| 11 | 낸 옮긴 연체금 | paid_carried_delinquency_charge | DECIMAL(23,4) | | N | 0 | |

### 하루 기록 (acceleration_daily_charge / maturity_overdue_daily_charge)

컬럼 구성이 같다. 상위 행 FK만 다르다(`loan_acceleration_id` / `loan_maturity_overdue_id`).

| No. | 컬럼 한글명 | 컬럼 영문명 | 데이터 타입 | 제약조건 | NULL | 기본값 | 비고 |
|---|---|---|---|---|---|---|---|
| 1 | ID | acceleration_daily_charge_id / maturity_overdue_daily_charge_id | BIGINT | PK | N | AUTO_INCREMENT | |
| 2 | 상위 행ID | loan_acceleration_id / loan_maturity_overdue_id | BIGINT | FK, UQ① | N | | |
| 3 | 날짜 | charge_date | DATE | UQ① | N | | |
| 4 | 근거 | loan_judgment_status_id | INT | FK | N | | 기한이익상실 12·13·22·23, 만기 경과 1 |
| 5 | 연체금 대상 금액 | delinquency_target_amount | DECIMAL(23,4) | | N | | |
| 6 | 연체 이자율 | delinquency_rate | DECIMAL(10,5) | | N | | |
| 7 | 연체금 | delinquency_charge | DECIMAL(23,4) | | N | | |
| 8 | 낸 연체금 | paid_delinquency_charge | DECIMAL(23,4) | | N | 0 | 누적 |
| 9 | 하루 약정이자 | interest_amount | DECIMAL(23,4) | | N | 0 | 13·23 기한이익상실+❻만. 만기 경과는 항상 0 |
| 10 | 낸 약정이자 | paid_interest | DECIMAL(23,4) | | N | 0 | 누적 |
| 11 | 상태 | delinquency_status_id | INT | FK | N | | 진행중·해소 |

> UQ① (상위 행ID, charge_date)

---

## 납부

### 상환 (repayment)

상환 요청 1건에 1행. 고치거나 지우지 않는다.

| No. | 컬럼 한글명 | 컬럼 영문명 | 데이터 타입 | 제약조건 | NULL | 기본값 | 비고 |
|---|---|---|---|---|---|---|---|
| 1 | 상환ID | repayment_id | BIGINT | PK | N | AUTO_INCREMENT | |
| 2 | 대출계좌ID | loan_account_id | BIGINT | FK | N | | |
| 3 | 상환 채널 | repayment_channel_id | INT | FK | N | | 고객 상환·자동 상환 |
| 4 | 요청 키 | request_key | VARCHAR(64) | UQ | N | | 중복 요청 방지 |
| 5 | 낸 금액 | paid_amount | DECIMAL(23,4) | | N | | |
| 6 | 낸 일시 | paid_at | DATETIME | | N | | |
| 7 | 이체 거래번호 | transaction_no | VARCHAR(40) | | N | | account_transaction과 연결(FK 아님) |

### 충당 상세 (repayment_allocation)

상환 1건이 대상 하나에 충당한 금액을 1행으로 남긴다. 고치거나 지우지 않는다.

| No. | 컬럼 한글명 | 컬럼 영문명 | 데이터 타입 | 제약조건 | NULL | 기본값 | 비고 |
|---|---|---|---|---|---|---|---|
| 1 | 충당 상세ID | repayment_allocation_id | BIGINT | PK | N | AUTO_INCREMENT | |
| 2 | 상환ID | repayment_id | BIGINT | FK | N | | |
| 3 | 대상 종류 | repayment_target_type_id | INT | FK | N | | 십의 자리: 0 회차, 1 기한이익상실, 2 만기 경과 |
| 4 | 대상ID | target_id | BIGINT | | N | | 종류마다 가리키는 테이블이 달라 FK 없음 |
| 5 | 충당 금액 | paid_amount | DECIMAL(23,4) | | N | | 이 상환의 금액(누적 아님) |

- UQ (repayment_id, repayment_target_type_id, target_id)

| 대상 종류 | 대상ID가 가리키는 곳 |
|---|---|
| 1 회차 연체금 | `installment_delinquency_charge` |
| 2·3 회차 이자·원금 | `repayment_schedule` |
| 11·13·15 기한이익상실 옮긴 연체금·이자·밀린 원금 | `loan_acceleration` |
| 12·14 기한이익상실 하루 연체금·약정이자 | `acceleration_daily_charge` |
| 16 기한이익상실 나머지 원금 | `loan_account` |
| 21·23 만기 경과 옮긴 연체금·이자 | `loan_maturity_overdue` |
| 22 만기 경과 하루 연체금 | `maturity_overdue_daily_charge` |
| 24 만기 경과 원금잔액 | `loan_account` |

---|---|---|---|---|---|---|---|
| 1 | ID | `{테이블명}_id` | BIGINT | PK | N | AUTO_INCREMENT | |
| 2 | 상환ID | repayment_id | BIGINT | FK, UQ | N | | 회차 방식은 (repayment_id, repayment_schedule_id) UQ |
| 3 | 대상ID | repayment_schedule_id / loan_acceleration_id / loan_maturity_overdue_id | BIGINT | FK | N | | |
| 4 | 낸 원금 | paid_principal | DECIMAL(23,4) | | N | 0 | |
| 5 | 낸 이자 | paid_interest | DECIMAL(23,4) | | N | 0 | |
| 6 | 낸 연체금 | paid_delinquency_charge | DECIMAL(23,4) | | N | 0 | |

---

## 일마감·처리 실패

### 일마감 (daily_closing)

| No. | 컬럼 한글명 | 컬럼 영문명 | 데이터 타입 | 제약조건 | NULL | 기본값 | 비고 |
|---|---|---|---|---|---|---|---|
| 1 | 기준일 | business_date | DATE | PK | N | | |
| 2 | 상태 | daily_closing_status_id | INT | FK | N | | 진행중·완료·실패 |
| 3 | 시작 일시 | started_at | DATETIME | | N | | |
| 4 | 종료 일시 | finished_at | DATETIME | | Y | | |

### 처리 실패 (processing_failure)

실패 내용은 고치지 않는다. 풀리면 `resolved_at`·`resolution_note`만 한 번 채운다.

| No. | 컬럼 한글명 | 컬럼 영문명 | 데이터 타입 | 제약조건 | NULL | 기본값 | 비고 |
|---|---|---|---|---|---|---|---|
| 1 | 실패ID | processing_failure_id | BIGINT | PK | N | AUTO_INCREMENT | |
| 2 | 실패 종류 | failure_type_id | INT | FK | N | | |
| 3 | 기준일 | business_date | DATE | | N | | |
| 4 | 대출계좌ID | loan_account_id | BIGINT | FK | Y | | |
| 5 | 회차ID | repayment_schedule_id | BIGINT | FK | Y | | |
| 6 | 사유 | reason | VARCHAR(1000) | | N | | |
| 7 | 배치 실행ID | batch_job_execution_id | BIGINT | | Y | | BATCH_JOB_EXECUTION 참조(FK 없음, V2보다 먼저 생성) |
| 8 | 발생 일시 | occurred_at | DATETIME | | N | | |
| 9 | 해결 일시 | resolved_at | DATETIME | | Y | | NULL이면 미해결 |
| 10 | 해결 메모 | resolution_note | VARCHAR(1000) | | Y | | |

---

## 용어 사전

| 한글 | 영문 | 비고 |
|---|---|---|
| ~ID | ~_id | PK는 `테이블명_id`, FK는 참조 테이블 PK명 그대로 |
| ~명 | name | 테이블 컨텍스트로 의미 명확 시 접두사 생략 |
| 계좌번호 | account_no | number → no 축약 |
| 생성/수정일시 | created_at / updated_at | DEFAULT/ON UPDATE CURRENT_TIMESTAMP로 자동화 |
| (이미 발생한) ~일시 | _at | 확정 일시, 납부 일시, 판정 일시 |
| 날짜 | _date | 시간 정보 불필요 (계약일, 만기일, 예정일, 기준일) |
| 누적 낸 금액 | paid_* | 기본값 0, 충당할 때 더한다 |
| 옮긴 금액 | carried_* | 기한이익상실·만기 경과 전환 때 회차에서 옮긴 금액 |
| 금액 | DECIMAL(23,4) | 부동소수점(FLOAT/DOUBLE) 미사용 |
| 이자율/금리 | DECIMAL(10,5) | 가산금리, 적용금리, 연체이자율 공통 |
