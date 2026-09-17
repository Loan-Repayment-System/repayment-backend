# 상환방법별 회차 원금/이자 계산기 검증 결과

> `RepaymentAmountCalculator`의 계산 로직을 (1) JUnit 단위테스트, (2) Python `numpy_financial` 외부 라이브러리 교차검증 두 단계로 검증한 결과를 정리한다.

---

## 1. 테스트 대상

| 항목 | 내용 |
|---|---|
| 모듈 | `RepaymentAmountCalculator` |
| 위치 | `CoreBanking/src/main/java/hello/corebanking/domain/repayment/service/component/RepaymentAmountCalculator.java` |
| 공개 API | `calculate(RepaymentMethod method, BigDecimal balance, BigDecimal rate, int remainingInstallments, BigDecimal originalPrincipal, int totalInstallments)` |
| 반환값 | `RepaymentAmount(principal, interest)` — `CoreBanking/src/main/java/hello/corebanking/domain/repayment/dto/RepaymentAmount.java` |
| 검증한 상환방법 3종 | `LEVEL_PAYMENT`(원리금균등), `EQUAL_PRINCIPAL`(원금균등), `BULLET`(만기일시상환) — `domain/product/entity/RepaymentMethod` |
| 반올림 규칙 | scale 4, `RoundingMode.HALF_UP` (`docs/ddl.sql`의 `DECIMAL(23,4)` 금액 컬럼 정밀도에 맞춤) |

---

## 2. 검증 방법

### 2-1. JUnit 단위테스트 (손으로 고른 대표값 검증)

- 위치: `CoreBanking/src/test/java/hello/corebanking/domain/repayment/service/component/RepaymentAmountCalculatorTest.java`
- 순수 JUnit5 + AssertJ (`isEqualByComparingTo`), Mockito/Spring 컨텍스트 불필요 (의존성 없는 순수 함수라 `new RepaymentAmountCalculator()`로 직접 생성)
- 구성 (16개 테스트):
  - **최초/중간/마지막회차** (방법×3 = 9개): 각 상환방법에 대해 n=N(최초), 1<n<N(중간), n=1(마지막)일 때 원금/이자를 손계산 기대값과 비교
  - **순수성** (방법×1 = 3개): 동일 입력을 두 번 호출해 항상 같은 결과가 나오는지 확인
  - **수렴성** (방법×1 = 3개): n=N부터 1까지 반복 호출하며 원금을 잔액에서 차감 → 최종 잔액이 정확히 0, 원금 합계가 최초 대출원금과 일치하는지 확인
  - **분기 검증** (1개): `RepaymentMethod`에 따라 올바른 계산식으로 분기되는지 확인
- 실행: `./gradlew test --tests "*RepaymentAmountCalculatorTest*"`

### 2-2. numpy_financial 교차검증 (넓은 범위의 파라미터 조합 자동 검증)

JUnit 테스트는 소수의 손으로 고른 값만 검증하므로, 특히 원리금균등(annuity) 공식이 다양한 입력에서도 올바른지 **Java 코드와 독립적인 외부 금융 라이브러리**로 재확인했다.

- 위치: `CoreBanking/scripts/verify-repayment-calculator/`
- 구조와 비교 방식:
  1. `Harness.java`가 `RepaymentAmountCalculator.calculate(...)`를 실제 호출하며 얻은 결과(원금/이자)를 `cases.csv`에 한 줄씩 기록한다. → CSV의 `principal`/`interest`는 **Java가 실제로 계산한 값**이다.
  2. `verify_repayment_calculator.py`는 CSV의 각 행에 대해, Java와 무관하게 **독립적으로 기대값을 재계산**한다.
     - `LEVEL_PAYMENT`: `numpy_financial.ppmt`/`ipmt` (원단위 새 n기간 대출로 보는 것이 상환 스케줄의 memoryless 성질상 수학적으로 동일함을 이용)
     - `EQUAL_PRINCIPAL`: Python `Decimal`로 `P0/N`, `balance*rate` 직접 재계산
     - `BULLET`: Python `Decimal`로 `0 또는 balance`, `balance*rate` 직접 재계산
  3. Java 값과 재계산값의 절대오차가 `0.001`원 이하인지 행 단위로 판정한다.
- 실행: `./scripts/verify-repayment-calculator/run.sh` (Gradle 빌드 → Java 하니스 컴파일/실행 → Python venv 준비 → 교차검증까지 한 번에 수행)

---

## 3. 테스트한 파라미터 범위 (numpy_financial 교차검증)

| 파라미터 | 값 |
|---|---|
| 잔액 (balance) | 500,000 / 1,234,567.89 / 10,000,000 / 87,654,321.12 / 250,000,000 (5종, 소액~고액/정수~소수 혼합) |
| 기간이자율 (rate) | 0.0025 / 0.005 / 0.0083333 / 0.01 / 0.02 (5종, 월 0.25%~2% 범위) |
| 잔여회차 (n) | 1 / 2 / 3 / 6 / 12 / 24 / 36 / 60 / 120 / 360 (10종, n=1 포함 — 마지막회차 보정 로직 검증용) |
| 총회차 (N, 원금균등 전용) | 12 / 36 / 60 / 120 / 360 (5종, n ≤ N 조합만 사용) |
| 최초원금 (P0, 원금균등 전용) | 잔액(balance) 값 재사용 |
| rate=0 | 제외 (원리금균등 공식에서 0으로 나누기 발생 — 알려진 미지원 케이스) |

→ 3개 상환방법 × 위 조합을 전개해 **총 1,475개 케이스**를 생성해 검증했다.

---

## 4. 결과

| 구분 | 결과 |
|---|---|
| JUnit 단위테스트 | 16 / 16 통과 |
| numpy_financial 교차검증 | 1,475 / 1,475 통과 (실패 0) |
| 관측된 최대 오차 | 약 0.00005원 (허용치 0.001원 이내) |

관측된 최대 오차는 Java `BigDecimal`(scale 4, HALF_UP)과 Python `float64`(numpy_financial) 간의 정밀도 차이 수준으로, 세 상환방법 공식 모두 독립적인 외부 라이브러리·직접 재계산 결과와 일치함을 확인했다.

---

## 5. 재현 방법

```bash
cd CoreBanking
./gradlew test --tests "*RepaymentAmountCalculatorTest*"   # JUnit 단위테스트
./scripts/verify-repayment-calculator/run.sh                # numpy_financial 교차검증
```
