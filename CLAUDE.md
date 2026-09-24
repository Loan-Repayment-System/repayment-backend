# repayment-backend

대출 상환 시스템(Loan Repayment System)의 서버

## 빌드 · 실행

```bash
cp .env.sample .env
docker compose up -d      # 로컬 MySQL (빈 DB. 스키마는 앱이 뜰 때 Flyway가 넣는다 — §11)
./gradlew bootRun         # http://localhost:8080, 기본 프로필 local
./gradlew build           # 컴파일 + 테스트 + jar 패키징 — Docker 필요(Testcontainers)
./gradlew test            # 테스트만
```

---

## 1. 기술 스택

| 항목 | 버전 |
|---|---|
| Java | 17 |
| Spring Boot | 3.5.16 |
| Gradle | 8.14.3 (Groovy DSL) |
| 영속성 | MyBatis (스타터 3.0.5) |
| DB | MySQL 8.4 |
| 마이그레이션 | Flyway |
| 테스트 | JUnit 5, AssertJ, Mockito, Testcontainers(MySQL) |

---

## 2. 패키지 구조


```
com.repayment
├─ RepaymentApplication
├─ api/                             # 백엔드
│  └─ {customer,product,loan,repayment}/
│     └─ {대상}Controller
├─ batch/                           # 배치
│  └─ {job,step,reader,writer,listener}/
├─ domain/                          # api·batch가 공유
│  └─ {customer,product,loan,repayment}/
│     ├─ service/
│     ├─ mapper/                    # MyBatis 인터페이스
│     ├─ dto/{request,response}/    # 코드성 enum도 여기
│     └─ exception/                 # 구체 예외 클래스 (§6)
└─ global/
   ├─ code/                         # ErrorCode enum
   ├─ config/
   ├─ dto/                          # ApiResponse
   ├─ exception/                    # BusinessException, 범주 예외, GlobalExceptionHandler
   └─ util/

src/main/resources/mapper/{도메인}/{대상}Mapper.xml
```

- `domain`은 `api`, `batch`를 참조하지 않는다.
- 서비스는 인터페이스 없이 클래스 하나로 만든다.

---

## 3. 네이밍 컨벤션

| 대상 | 규칙 | 예 |
|---|---|---|
| 패키지 | 소문자·단수 | `com.repayment.domain.loan` |
| 컨트롤러 / 서비스 | `{대상}Controller` / `{대상}Service` | `LoanAccountController`, `RepaymentService` |
| 매퍼 인터페이스 / XML | `{대상}Mapper` / `resources/mapper/{도메인}/{대상}Mapper.xml` | `mapper/loan/LoanAccountMapper.xml` |
| 요청 DTO | `{동작}{대상}Request` | `CreateRepaymentRequest` |
| 응답 DTO | `{대상}{용도}Response` | `LoanAccountDetailResponse` |
| 조회 조건 | `{대상}SearchCondition` | `RepaymentHistorySearchCondition` |
| 에러 코드 | 전역 단일 enum `ErrorCode`의 상수 | `ErrorCode.LOAN_ACCOUNT_NOT_FOUND` |
| 범주 예외 | `{범주}Exception` | `NotFoundException` |
| 구체 예외 | `{맥락}Exception` — 이름이 맥락과 1:1 | `LoanAccountNotFoundException` |
| 매퍼 메서드 | 조회 `find*`/`count*`/`exists*`, 변경 `insert*`/`update*`/`delete*` | `findByLoanAccountId` |
| enum 상수 | 영어 대문자. 코드성 테이블의 **id**와 1:1 (§8) | `RepaymentMethod.EQUAL_PRINCIPAL_INTEREST` |
| DB → 필드 | `snake_case` → `camelCase` | `loan_principal_balance` → `loanPrincipalBalance` |
| 마이그레이션 | `V{버전}__{설명}.sql` | `V3__add_repayment_index.sql` |
| 테스트 클래스 | `{대상}Test` / `{대상}IntegrationTest` | `RepaymentServiceTest` |
| 테스트 메서드 | 한글 + 언더스코어 | `상환금액이_잔액을_초과하면_예외를_던진다()` |

---

## 4. DTO 규칙

- Request·Response는 class + Lombok으로 만든다. `@Setter`는 쓰지 않는다.
  - Request: `@Getter @NoArgsConstructor`
  - Response: `@Getter @NoArgsConstructor @AllArgsConstructor @Builder`
- entity는 만들지 않는다. 매퍼는 Request DTO를 그대로 받고, Response DTO로 바로 반환한다.

---

## 5. 응답 포맷

`/api` 아래 모든 응답은 `ApiResponse<T>`로 감싸서 보낸다.

```json
// 성공 — code·message 없이 data만 담는다
{ "success": true, "data": [ ... ] }

// 실패 — data는 기본적으로 null이다
{ "success": false, "code": "LOAN_ACCOUNT_NOT_FOUND",
  "message": "대출 계좌를 찾을 수 없습니다.", "data": null }

// 실패 + 부가 데이터 — 필요할 때만 data를 채운다
{ "success": false, "code": "REPAYMENT_EXCEEDS_BALANCE",
  "message": "상환금액이 잔액을 초과합니다.", "data": { "remainingBalance": "1000000.0000" } }

// 검증 실패 — errors가 추가된다 (이때만 나온다)
{ "success": false, "code": "INVALID_INPUT_VALUE",
  "message": "입력값이 올바르지 않습니다.", "data": null,
  "errors": [ { "field": "paidAmount", "reason": "..." } ] }
```

실패 응답의 `code`·`message`·상태코드는 예외 클래스가 아니라 `global/code/ErrorCode` enum 하나가 관리한다.

- `code`는 enum 상수 이름을 그대로 쓴다.
- 주석으로 구역(공통 / 회원 / 대출 / 상환)을 나눈다.
- `message`는 사용자에게 그대로 보일 문구로 적는다.

---

## 6. 예외 처리

예외 클래스는 3단이다.

| 단 | 위치 | 역할 |
|---|---|---|
| `BusinessException` | `global/exception/` | 최상위 abstract 클래스. `ErrorCode`를 들고 있다 |
| `{범주}Exception` | `global/exception/` | 여러 예외를 한 타입으로 잡는 단위 |
| `{맥락}Exception` | `domain/{도메인}/exception/` | 실제로 던지는 예외 |

- 범주는 미리 만들지 않는다. 여러 예외를 한 타입으로 잡아야 할 때 추가한다.
- 던질 때는 구체 예외만 쓴다.
- 금액, ID처럼 원인 추적에 필요한 값은 생성자로 받는다.
- 컨트롤러는 예외를 잡지 않는다. `GlobalExceptionHandler`가 잡아서 응답으로 바꾼다.

---

## 7. MyBatis 규칙

- 값은 `#{}`로 넘긴다. `${}`는 SQL에 문자열을 그대로 붙여 SQL 인젝션이 생긴다.
- 정렬 컬럼처럼 `#{}`로 안 되는 곳만 `${}`를 쓰고, 허용한 컬럼 이름인지 먼저 확인한다.
- `SELECT *`는 쓰지 않는다. 필요한 컬럼만 적는다.
- `AS` 별칭을 붙이지 않는다. `map-underscore-to-camel-case`가 켜져 있다.
- 매퍼 파라미터가 2개 이상이면 `@Param`을 붙인다.
- 응답 DTO 모양에 따라 매핑 방식을 고른다.

  | DTO 모양 | 매핑 |
  |---|---|
  | 필드가 모두 단순 값 | `resultType` |
  | 다른 DTO를 필드로 가짐 | `resultMap` + `<association>` |
  | 다른 DTO 목록을 필드로 가짐 | `resultMap` + `<collection>` |

- `resultType`을 쓸 때는 DTO 필드를 모두 SELECT한다. 빠진 필드는 에러 없이 `null`이 된다.
- `<association>`, `<collection>`을 쓰면 자동 매핑이 꺼진다. 바깥 DTO의 단순 필드도 `<result>`로 모두 적는다.
- 코드성 테이블 enum은 id로 매핑한다. 공통 TypeHandler를 `global/config/typehandler/`에 만들고
  `mybatis.type-handlers-package`로 등록한다. 그 외 TypeHandler는 만들지 않는다.

---

## 8. 데이터 규칙

- 금액과 이율은 `BigDecimal`로 다룬다. `double`, `float`는 쓰지 않는다.
- 이자, 원금 등 계산한 금액은 원 미만을 버린다(`setScale(0, RoundingMode.DOWN)`). 중간 계산에서는 버리지 않고 저장 직전에 한 번만 버린다.
- `BigDecimal`은 `equals` 대신 `compareTo`로 비교한다.
- `created_at`, `updated_at`은 DB가 채우므로 INSERT, UPDATE 문에 적지 않는다.
- 상태나 잔액이 바뀌면 이력 테이블에 남긴다. 변경과 이력 INSERT는 한 트랜잭션으로 묶는다.
- 코드성 테이블의 id는 enum과 1:1이다. `V1__code_table_data.sql`의 id를 바꾸지 않고, 코드를 추가할 때는 새 마이그레이션과 enum을 함께 추가한다.

---

## 9. 날짜 · 시간

- 타임존은 DB, JDBC, JVM 세 곳에서 `Asia/Seoul`로 맞춘다.
- JVM 옵션(`-Duser.timezone=Asia/Seoul`)은 `build.gradle`의 `bootRun`, `test`에만 걸려 있다. `java -jar`나 운영 배포에도 넣는다.

---

## 10. 환경 설정

- 프로필은 `local`(기본)과 `test`가 있다. `test`는 통합 테스트용이고 local 샘플 데이터를 읽지 않는다.
- 로컬 포트나 계정을 바꿀 때는 `.env`만 고친다. docker compose와 Spring이 같은 `.env`를 읽는다.
- 비밀값은 커밋하는 파일에 넣지 않는다. `.env`는 커밋하지 않고, `.env.sample`에는 로컬용 값만 둔다.

---

## 11. DB 마이그레이션 (Flyway)

- 스키마는 앱이 뜰 때 Flyway가 적용한다. SQL을 손으로 실행하지 않는다.
- 이미 적용된 파일은 고치지 않는다. 바꿀 때는 새 버전 파일을 추가한다.
- 스키마 변경과 코드성 데이터는 `db/migration/`에 다음 번호로 추가한다. 로컬 샘플 데이터는 `db/seed/local/`에 `V900`번대로 둔다.
- Spring Batch 메타 테이블도 Flyway가 관리한다. Batch 버전을 올려 스키마가 바뀌면 새 마이그레이션을 추가한다.
- 로컬 DB를 처음부터 다시 만들려면 `docker compose down -v` 후 앱을 다시 띄운다.

---

## 12. 배치 (Spring Batch)

- 앱이 뜰 때 Job은 자동 실행되지 않는다(`spring.batch.job.enabled: false`). 이 설정을 켜지 않는다.
- chunk 크기가 곧 커밋 단위다. Job을 만들 때 먼저 정한다.
- `skip`, `retry` 대상은 예외 범주로 지정한다. `BusinessException`처럼 최상위를 지정하면 멈춰야 할 실패까지 건너뛴다.

---

## 13. 테스트

- 서비스와 계산 로직은 단위 테스트로 검증한다. DB 없이 Mockito나 순수 JUnit을 쓴다.
- 매퍼, Flyway, 배치 Job은 통합 테스트로 검증한다. `@SpringBootTest` + `@Import(TestcontainersConfig.class)` + `@ActiveProfiles("test")`를 쓰고, 배치는 `@SpringBatchTest`를 더한다.
- 컨트롤러 테스트는 선택이다. 쓸 때는 MockMvc `standaloneSetup`을 쓴다.
- 단언은 AssertJ로 쓴다. 금액은 `isEqualByComparingTo`로 비교한다.
- 이자, 상환액 계산은 경계값(첫 회차, 마지막 회차 잔액 보정, 0원 이자)을 테스트한다.
- 통합 테스트가 컨테이너 기동에서 실패하면 Docker Desktop이 켜져 있는지부터 확인한다.
- 데이터를 바꾸는 통합 테스트는 `@Transactional`로 롤백한다. 배치 Job 테스트는 `@Transactional`을 쓸 수 없으니 테스트 안에서 정리한다.

### 작성 순서

테스트는 요구사항과 결정 이력을 담은 문서다. 케이스를 사람이 검토하기 전에는 테스트 코드를 쓰지 않는다.

1. 코드를 쓰기 전에 케이스 목록을 먼저 제안한다. 케이스마다 상황(given), 동작(when), 기대 결과(then)를 적는다.
2. 사람이 케이스를 검토하고 확정할 때까지 기다린다. 추가·삭제·수정된 케이스는 반영해서 목록을 다시 보여준다.
3. 확정된 케이스만 코드로 옮긴다. 구현 중에 새 케이스가 필요해지면 멈추고 1번부터 다시 한다.

```
| # | 상황 (given) | 동작 (when) | 기대 결과 (then) |
|---|---|---|---|
| 1 | 대출이자율 15% | 연체 이자율을 계산한다 | 17% |
```

### BDD 스타일

- 테스트 본문은 `// given`, `// when`, `// then` 주석으로 나눈다. `when`은 한 번만 호출한다.
- Mockito는 BDDMockito를 쓴다. `when(...).thenReturn(...)` 대신 `given(...).willReturn(...)`, `verify(...)` 대신 `then(...).should()`를 쓴다.
- 같은 상황의 케이스는 `@Nested` 클래스로 묶는다.

### @DisplayName

- 테스트 클래스, `@Nested` 클래스, 테스트 메서드에 모두 `@DisplayName`을 붙인다. 테스트 결과를 위에서부터 읽으면 명세처럼 읽혀야 한다.
- 테스트 클래스는 대상, `@Nested`는 상황("~일 때"), 메서드는 조건과 결과를 담은 문장("~하면 ~한다")으로 적는다.
- 숫자와 결과를 문장에 그대로 적는다. "정상 동작한다", "예외가 발생한다"처럼 무엇인지 알 수 없는 문장은 쓰지 않는다.
- 근거(요구사항 번호, 약관 조항)는 메서드 위 한 줄 주석으로 남긴다.
- 메서드 이름은 §3 네이밍을 따른다.

```java
@DisplayName("연체 이자율 계산")
class DelinquentRateCalculatorTest {

    @Nested
    @DisplayName("대출이자율이 최고 연체 이자율(15%) 이상일 때")
    class 대출이자율이_15퍼센트_이상 {

        // DELQ-01, 상품설명서 6-가
        @Test
        @DisplayName("대출이자율 15%면 연체 이자율은 2%를 더한 17%다")
        void 대출이자율에_2퍼센트를_더한다() {
            // given
            BigDecimal loanRate = new BigDecimal("15");

            // when
            BigDecimal result = calculator.calculate(loanRate);

            // then
            assertThat(result).isEqualByComparingTo("17");
        }
    }
}
```

---

## 14. 참고 자료

- 테이블 목록과 관계: `docs/erd.md` (컬럼 상세는 `docs/reference/table_definition.md`)
- 연체 등 업무 규칙의 근거: `docs/reference/가계대출상품설명서.pdf`
- 상환금액 계산식: `docs/reference/repayment-amount-calculator-verification.md` (반올림은 §8을 따른다)

---

## 15. Git / GitHub 워크플로우

### 브랜치 · 커밋

- `main`은 배포 브랜치, `develop`은 기본 브랜치다. 둘 다 직접 push하지 않는다.
- 작업 브랜치는 `develop`에서 만들고 `develop`으로 PR한다. 병합은 Squash and Merge다.
- 릴리스는 사람이 `develop` → `main` PR을 만들어 Merge commit으로 병합한다.
- 브랜치 이름은 `{type}/{설명}`이고 영어 소문자와 하이픈만 쓴다.
  type은 `feat`, `fix`, `docs`, `chore`, `style`, `refactor`, `test`, `perf`, `ci` 중 하나다.
- 커밋 메시지는 `type: 한국어 설명`이다. `Co-Authored-By` 트레일러는 붙이지 않는다.
- 작업과 무관한 파일은 같이 커밋하지 않는다.

### 작업 순서

1~5단계는 확인 없이 이어서 진행한다.

1. 이슈 등록: `gh issue create --repo Loan-Repayment-System/repayment-backend`
2. 브랜치 생성: `git checkout develop && git pull origin develop && git checkout -b {type}/{설명}`
3. 구현 후 `./gradlew build`가 통과하는지 확인한다(Docker 필요).
4. 커밋
5. push: `git push -u origin {브랜치명}`
6. PR은 사람이 만든다. push까지 하고 멈춘 뒤 브랜치명과 변경 요약만 알린다.
   사용자가 PR 본문을 주면 그대로 `gh pr create --base develop`으로 올리는 건 된다.

큰 작업은 상위 이슈나 마일스톤으로 묶고, 하위 작업마다 이 순서를 반복한다.

### 이슈 작성

제목은 `[TYPE] 한국어 설명`이고, 접두사에 맞는 타입 라벨을 반드시 붙인다.

| 접두사 | 라벨 | 본문 섹션 |
|---|---|---|
| `[BUG]` | `🐛 버그` | 버그 설명 / 재현 방법 / 예상 동작 / 실제 동작 / 스크린샷 / 환경 / 추가 정보 |
| `[FEAT]` | `✨ 기능` | 기능 요약 / 배경 및 이유 / 구현 방법 나열 / 추가 정보 |
| `[TASK]` | `🛠️ 작업` | 작업 내용 / 작업 목표 / 세부 작업 목록(체크박스) / 참고 자료 / 완료 조건 |
| `[REFACTOR]` | `🧹 리팩터링` | 리팩토링 대상 / 현재 문제점 / 개선 방향 / 예상 영향 범위 / 주의사항 |
| `[DOCS]` | `📝 문서` | 문서 작업 내용 / 작업 이유 / 작업 범위(체크박스) / 참고 자료 |
| `[QUESTION]` | (없음) | 질문 내용 / 배경 / 시도해본 것 / 참고 자료 |
| `[LEARN]` | `📚 학습` | 무엇을 했나 / 왜 이렇게 했나 / 어떻게 동작하나 / 몰랐다가 알게 된 것 / 참고 사항 |

- 해당되면 우선순위·상태·영역 라벨을 더 붙인다. 라벨 목록과 설명은 `gh label list`로 본다.
- 관련 마일스톤이 있으면 `--milestone`으로 연결한다.
