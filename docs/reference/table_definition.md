# LoanServicing 테이블 정의서

> 물리적 모델링 결과물. 스키마 변경 시 이 문서를 함께 업데이트한다.
> 공통 컬럼(`created_at`, `updated_at`)은 모든 테이블에 동일하게 적용되며, 표 하단에 별도 표기.

---

## 코드성 테이블

### 1. 이자종류 (interest_type)

| No. | 컬럼 한글명 | 컬럼 영문명 | 데이터 타입 | 제약조건 | NULL | 기본값 | 비고 |
|---|---|---|---|---|---|---|---|
| 1 | 종류ID | interest_type_id | INT | PK | N | AUTO_INCREMENT | |
| 2 | 이자종류명 | name | VARCHAR(50) | UQ | N | | 고정금리(1), 변동금리(2) |

### 2. 상환방법 (repayment_method)

| No. | 컬럼 한글명 | 컬럼 영문명 | 데이터 타입 | 제약조건 | NULL | 기본값 | 비고 |
|---|---|---|---|---|---|---|---|
| 1 | 방법ID | repayment_method_id | INT | PK | N | AUTO_INCREMENT | |
| 2 | 상환방법명 | name | VARCHAR(50) | UQ | N | | 원금만기일시상환(1), 원리금균등상환(2), 원금균등상환(3) |

### 3. 대출상태 (loan_status)

| No. | 컬럼 한글명 | 컬럼 영문명 | 데이터 타입 | 제약조건 | NULL | 기본값 | 비고 |
|---|---|---|---|---|---|---|---|
| 1 | 상태ID | loan_status_id | INT | PK | N | AUTO_INCREMENT | |
| 2 | 상태명 | name | VARCHAR(50) | UQ | N | | 정상(1), 연체(2), 완제(3), 기한이익상실(4) |

### 4. 상환스케줄상태 (repayment_schedule_status)

| No. | 컬럼 한글명 | 컬럼 영문명 | 데이터 타입 | 제약조건 | NULL | 기본값 | 비고 |
|---|---|---|---|---|---|---|---|
| 1 | 상태ID | repayment_schedule_status_id | INT | PK | N | AUTO_INCREMENT | |
| 2 | 상태명 | name | VARCHAR(50) | UQ | N | | 예정(1), 납부 기한 도래(2), 납입 완료(3), 연체(4) |

### 5. 연체상태 (delinquency_status)

| No. | 컬럼 한글명 | 컬럼 영문명 | 데이터 타입 | 제약조건 | NULL | 기본값 | 비고 |
|---|---|---|---|---|---|---|---|
| 1 | 상태ID | delinquency_status_id | INT | PK | N | AUTO_INCREMENT | |
| 2 | 상태명 | name | VARCHAR(50) | UQ | N | | 진행중(1), 해소(2) |

### 6. 연체근거 (delinquency_reason)

| No. | 컬럼 한글명 | 컬럼 영문명 | 데이터 타입 | 제약조건 | NULL | 기본값 | 비고 |
|---|---|---|---|---|---|---|---|
| 1 | 근거ID | delinquency_reason_id | INT | PK | N | AUTO_INCREMENT | |
| 2 | 근거명 | name | VARCHAR(50) | UQ | N | | 총 7종 — 아래 데이터 참조 |

#### delinquency_reason 초기 데이터

| id | name |
|---|---|
| 1 | 만기일시 + 이자연체 + 1개월 미만 |
| 2 | 만기일시 + 이자연체 + 1개월 이상 |
| 3 | 만기일시 + 이자연체 + 1개월 이상 + ⑥항 적용 |
| 4 | 만기일시 + 원금연체 |
| 5 | 분할상환 + 연속 1회 |
| 6 | 분할상환 + 연속 2회 이상 |
| 7 | 분할상환 + 연속 2회 이상 + ⑥항 적용 |

---

## 핵심 테이블

### 7. 회원 (member)

| No. | 컬럼 한글명 | 컬럼 영문명 | 데이터 타입 | 제약조건 | NULL | 기본값 | 비고 |
|---|---|---|---|---|---|---|---|
| 1 | 회원ID | member_id | BIGINT | PK | N | AUTO_INCREMENT | |
| 2 | 로그인ID | login_id | VARCHAR(50) | UQ | N | | |
| 3 | 비밀번호 | password | VARCHAR(255) | | N | | 해시값 저장 (BCrypt 등) |
| 4 | 이름 | name | VARCHAR(50) | | N | | 회원 실명 |

> 가입일시는 별도 컬럼 없이 공통 컬럼 `created_at`으로 흡수

### 8. 주 계좌 (primary_account)

| No. | 컬럼 한글명 | 컬럼 영문명 | 데이터 타입 | 제약조건 | NULL | 기본값 | 비고 |
|---|---|---|---|---|---|---|---|
| 1 | 계좌ID | primary_account_id | BIGINT | PK | N | AUTO_INCREMENT | |
| 2 | 회원ID | member_id | BIGINT | FK, UQ | N | | member.member_id 참조. 1:1 |
| 3 | 계좌번호 | account_no | VARCHAR(20) | UQ | N | | |
| 4 | 잔액 | balance | DECIMAL(23,4) | | N | 0 | |

### 9. 대출상품 (loan_product)

| No. | 컬럼 한글명 | 컬럼 영문명 | 데이터 타입 | 제약조건 | NULL | 기본값 | 비고 |
|---|---|---|---|---|---|---|---|
| 1 | 상품ID | loan_product_id | BIGINT | PK | N | AUTO_INCREMENT | |
| 2 | 종류ID | interest_type_id | INT | FK | N | | interest_type.interest_type_id 참조 |
| 3 | 방법ID | repayment_method_id | INT | FK | N | | repayment_method.repayment_method_id 참조 |
| 4 | 상품명 | name | VARCHAR(100) | | N | | |
| 5 | 가산금리 | additional_rate | DECIMAL(10,5) | | N | | 단위: % |

---

## 운영 테이블

### 10. 대출계약 (loan_contract)

| No. | 컬럼 한글명 | 컬럼 영문명 | 데이터 타입 | 제약조건 | NULL | 기본값 | 비고 |
|---|---|---|---|---|---|---|---|
| 1 | 계약ID | loan_contract_id | BIGINT | PK | N | AUTO_INCREMENT | |
| 2 | 상품ID | loan_product_id | BIGINT | FK | N | | loan_product.loan_product_id 참조 |
| 3 | 회원ID | member_id | BIGINT | FK | N | | member.member_id 참조. 1:N |
| 4 | 계약일 | contract_date | DATE | | N | | |
| 5 | 만기일 | maturity_date | DATE | | N | | |
| 6 | 계약대출원금 | contract_principal | DECIMAL(23,4) | | N | | 계약 시점 확정 원금 (고정값) |
| 7 | 계약 가산금리 | contract_additional_rate | DECIMAL(10,5) | | N | | 계약 시 확정 가산금리. 단위: % |
| 8 | 계약 기준금리 | contract_base_rate | DECIMAL(10,5) | | N | | 계약 시 확정 기준금리. 고정금리 시 만기까지 불변, 변동금리 시 초기 기준값. 단위: % |
| 9 | 총 대출 회차 | total_installments | INT | | N | | 계약 시점 확정 총 상환 회차 |

### 11. 대출계좌 (loan_account)

| No. | 컬럼 한글명 | 컬럼 영문명 | 데이터 타입 | 제약조건 | NULL | 기본값 | 비고 |
|---|---|---|---|---|---|---|---|
| 1 | 계좌ID | loan_account_id | BIGINT | PK | N | AUTO_INCREMENT | |
| 2 | 계약ID | loan_contract_id | BIGINT | FK, UQ | N | | loan_contract.loan_contract_id 참조. 1:1 |
| 3 | 주계좌ID | primary_account_id | BIGINT | FK | N | | primary_account.primary_account_id 참조 |
| 4 | 계좌번호 | account_no | VARCHAR(20) | UQ | N | | |
| 5 | 대출원금잔액 | loan_principal_balance | DECIMAL(23,4) | | N | | 상환 진행에 따라 감소하는 현재 잔액 |
| 6 | 매달 상환일 | monthly_payment_day | TINYINT | | N | | 1~31 |
| 7 | 현재 회차 | current_installment_no | INT | | N | 1 | |

### 12. 대출상태 이력 (loan_status_history)

| No. | 컬럼 한글명 | 컬럼 영문명 | 데이터 타입 | 제약조건 | NULL | 기본값 | 비고 |
|---|---|---|---|---|---|---|---|
| 1 | 이력ID | loan_status_history_id | BIGINT | PK | N | AUTO_INCREMENT | |
| 2 | 대출계좌ID | loan_account_id | BIGINT | FK | N | | loan_account.loan_account_id 참조. 1:N |
| 3 | 상태ID | loan_status_id | INT | FK | N | | loan_status.loan_status_id 참조 |
| 4 | 시작일 | start_date | DATE | | N | | |
| 5 | 종료일 | end_date | DATE | | Y | | NULL = 현재 진행 중인 상태 |

---

## 상환 테이블

### 13. 상환 스케줄 (repayment_schedule)

| No. | 컬럼 한글명 | 컬럼 영문명 | 데이터 타입 | 제약조건 | NULL | 기본값 | 비고 |
|---|---|---|---|---|---|---|---|
| 1 | 스케줄ID | repayment_schedule_id | BIGINT | PK | N | AUTO_INCREMENT | |
| 2 | 대출계좌ID | loan_account_id | BIGINT | FK | N | | loan_account.loan_account_id 참조 |
| 3 | 상태ID | repayment_schedule_status_id | INT | FK | N | | repayment_schedule_status.repayment_schedule_status_id 참조 |
| 4 | 예정일 | scheduled_at | DATETIME | | N | | 계좌 생성 시 확정, 이후 변경되지 않는 값 |
| 5 | 상환금(원금) | scheduled_principal | DECIMAL(23,4) | | Y | | |
| 6 | 상환금(이자) | scheduled_interest | DECIMAL(23,4) | | Y | | |
| 7 | 적용 가산금리 | applied_additional_rate | DECIMAL(10,5) | | Y | | 해당 회차 가산금리. 단위: % |
| 8 | 적용 기준금리 | applied_base_rate | DECIMAL(10,5) | | Y | | 해당 회차 기준금리. 고정: 매 회차 동일, 변동: 회차마다 갱신. 단위: % |

### 14. 상환 이력 (repayment_history)

| No. | 컬럼 한글명 | 컬럼 영문명 | 데이터 타입 | 제약조건 | NULL | 기본값 | 비고 |
|---|---|---|---|---|---|---|---|
| 1 | 이력ID | repayment_history_id | BIGINT | PK | N | AUTO_INCREMENT | |
| 2 | 스케줄ID | repayment_schedule_id | BIGINT | FK | N | | repayment_schedule.repayment_schedule_id 참조 |
| 3 | 연체ID | delinquency_history_id | BIGINT | FK | Y | | delinquency_history.delinquency_history_id 참조. 정상 상환 시 NULL |
| 4 | 납부 원금 | paid_principal | DECIMAL(23,4) | | N | | |
| 5 | 납부 이자 | paid_interest | DECIMAL(23,4) | | N | | |
| 6 | 납부 연체금 | paid_delinquency_charge | DECIMAL(23,4) | | N | 0 | |
| 7 | 납부 일시 | paid_at | DATETIME | | N | | 실제 납부 발생 시각 (created_at과 구분) |

### 15. 연체 이력 (delinquency_history)

| No. | 컬럼 한글명 | 컬럼 영문명 | 데이터 타입 | 제약조건 | NULL | 기본값 | 비고 |
|---|---|---|---|---|---|---|---|
| 1 | 연체ID | delinquency_history_id | BIGINT | PK | N | AUTO_INCREMENT | |
| 2 | 상태ID | delinquency_status_id | INT | FK | N | | delinquency_status.delinquency_status_id 참조 |
| 3 | 근거ID | delinquency_reason_id | INT | FK | N | | delinquency_reason.delinquency_reason_id 참조 |
| 4 | 스케줄ID | repayment_schedule_id | BIGINT | FK | N | | repayment_schedule.repayment_schedule_id 참조. UQ 없음 (한 회차가 여러 일에 걸쳐 연체 가능) |
| 5 | 연체 일시 | delinquent_at | DATETIME | | N | | |
| 6 | 연체 이자율 | delinquent_rate | DECIMAL(10,5) | | N | | 단위: % |
| 7 | 연체금 | delinquency_charge | DECIMAL(23,4) | | N | | |

---

## 공통 컬럼 (전체 15개 테이블 공통)

| No. | 컬럼 한글명 | 컬럼 영문명 | 데이터 타입 | 제약조건 | NULL | 기본값 | 비고 |
|---|---|---|---|---|---|---|---|
| 1 | 생성일시 | created_at | DATETIME | | N | CURRENT_TIMESTAMP | |
| 2 | 수정일시 | updated_at | DATETIME | | N | CURRENT_TIMESTAMP | ON UPDATE CURRENT_TIMESTAMP |

---

## 용어 사전 (Naming Convention 핵심)

| 한글 | 영문 | 비고 |
|---|---|---|
| ~ID | ~_id | PK는 `테이블명_id`, FK는 참조 테이블 PK명 그대로 |
| ~명 | name | 테이블 컨텍스트로 의미 명확 시 접두사 생략 |
| 계좌번호 | account_no | number → no 축약 |
| 생성/수정일시 | created_at / updated_at | DEFAULT/ON UPDATE CURRENT_TIMESTAMP로 자동화 |
| (이미 발생한) ~일시 | _at | 가입일시, 납부일시, 연체일시, 상환 예정일(확정값) |
| 시작일/종료일/계약일/만기일 | _date | 시간 정보 불필요 |
| 금액 | DECIMAL(23,4) | 부동소수점(FLOAT/DOUBLE) 미사용 |
| 이자율/금리 | DECIMAL(10,5) | 가산금리, 적용금리, 연체이자율 공통 |
