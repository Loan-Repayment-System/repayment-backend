# repayment-backend

대출 상환 시스템(Loan Repayment System)의 백엔드 서버.

이 문서가 **코드 컨벤션과 작업 플로우의 정본**이다. `CLAUDE.md`는 이 파일을 가져다 쓴다.
사람이 읽는 기여 가이드는 [CONTRIBUTING.md](./CONTRIBUTING.md), 테이블 목록은 [docs/erd.md](./docs/erd.md)에 있다.

## 빌드 · 실행

```bash
cp .env.sample .env
docker compose up -d      # 로컬 MySQL (빈 DB. 스키마는 앱이 뜰 때 Flyway가 넣는다 — §9)
./gradlew bootRun         # http://localhost:8080, 기본 프로필 local
./gradlew build           # 컴파일 + 테스트 + jar 패키징 — Docker 필요(Testcontainers)
./gradlew test            # 테스트만
```

---

## 1. 기술 스택

| 항목 | 버전 |
|---|---|
| Java | 17 |
| Spring Boot | 3.5.16 (`spring-boot-starter-web`, `-validation`, `-batch`) |
| Gradle | 8.14.3 (Groovy DSL) |
| 영속성 | MyBatis — `mybatis-spring-boot-starter` 3.0.5 |
| DB | MySQL 8.4 (`mysql-connector-j`) |
| 마이그레이션 | Flyway (`flyway-core` + `flyway-mysql`, Boot 관리 버전) |
| 테스트 | JUnit 5, AssertJ, Mockito, `spring-batch-test`, Testcontainers(MySQL) |

- **JPA를 쓰지 않는다.** 영속성은 MyBatis 하나로 통일한다 — SQL을 직접 통제해야 하는 잔액 갱신·배치·집계가 핵심이다
- Spring Boot 3.x는 `jakarta.*` 패키지를 쓴다. `javax.*` 기반 라이브러리를 넣지 않는다
- 의존성 버전은 Boot BOM이 관리하는 것을 우선한다. 직접 버전을 적는 것은 MyBatis 스타터뿐이다

---

## 2. 패키지 구조

```
com.repayment
├─ RepaymentApplication
├─ domain/
│  ├─ customer/     # member, primary_account
│  ├─ product/      # loan_product, interest_type, repayment_method
│  ├─ loan/         # loan_contract, loan_account, loan_status_history, loan_status
│  └─ repayment/    # repayment_schedule, repayment_history, delinquency_history, 상환·연체 코드
├─ batch/{job,step,reader,writer,listener}/
└─ global/{config,exception,util}/

src/main/resources/mapper/{도메인}/{대상}Mapper.xml
```

- 도메인 패키지 안은 필요해질 때 `controller/`, `service/`, `mapper/`, `dto/{request,response}/`로 나눈다
- 도메인 사이 공통 코드는 `global/`에 둔다. 도메인 패키지끼리 순환 참조하지 않는다

---

## 3. 네이밍 컨벤션

| 대상 | 규칙 | 예 |
|---|---|---|
| 패키지 | 소문자·단수 | `com.repayment.domain.loan` |
| 컨트롤러 / 서비스 | `{대상}Controller` / `{대상}Service` | `LoanAccountController`, `RepaymentService` |
| 매퍼 인터페이스 / XML | `{대상}Mapper` / `resources/mapper/{도메인}/{대상}Mapper.xml` | `mapper/loan/LoanAccountMapper.xml` |
| 요청 DTO | `{동작}{대상}Request` | `CreateRepaymentRequest` |
| 응답 DTO | `{대상}{용도}Response` | `LoanAccountDetailResponse` |
| 매퍼 메서드 | 조회 `find*`/`count*`/`exists*`, 변경 `insert*`/`update*`/`delete*` | `findByLoanAccountId` |
| DB → 필드 | `snake_case` → `camelCase` | `loan_principal_balance` → `loanPrincipalBalance` |
| 마이그레이션 | `V{버전}__{설명}.sql` | `V3__add_repayment_index.sql` |
| 테스트 클래스 | `{대상}Test` | `RepaymentServiceTest` |
| 테스트 메서드 | 한글 + 언더스코어 | `상환금액이_잔액을_초과하면_예외를_던진다()` |

---

## 4. MyBatis 규칙

- **`#{}`만 쓴다. `${}` 금지.** 동적 정렬 컬럼처럼 불가피하면 화이트리스트로 검증한 뒤 쓴다
- **`SELECT *` 금지** — 컬럼을 명시한다. 반복되면 `<sql>` + `<include>`로 뺀다
- `map-underscore-to-camel-case`가 켜져 있으므로 **수동 `AS` 별칭을 붙이지 않는다**
- `<select>`의 `id`는 인터페이스 메서드명과 같아야 한다
- 매퍼 파라미터가 2개 이상이면 `@Param`을 붙인다
- `resultType` 매핑은 SELECT하지 않은 컬럼을 조용히 `null`로 남긴다. 중첩 객체는 `resultMap` + `<association>`/`<collection>`을 쓰고, 이때 바깥 컬럼도 `<result>`로 전부 적는다

---

## 5. 금액 · 이율 · 코드성 데이터

- **금액과 이율은 `BigDecimal` 필수.** `double`/`float` 금지
- 스키마 정밀도: 금액 `DECIMAL(23,4)`, 이율 `DECIMAL(10,5)`. 연산 결과를 저장할 때 scale을 맞추고 **`RoundingMode`를 항상 명시**한다
- `BigDecimal` 비교는 `equals`가 아니라 `compareTo`(테스트는 `isEqualByComparingTo`)
- **코드성 테이블의 id는 enum과 1:1로 대응한다.** `V1__code_table_data.sql`의 id 순서를 바꾸지 않는다.
  코드를 추가할 때는 새 마이그레이션으로 INSERT하고 enum도 함께 추가한다

---

## 6. 트랜잭션

- `@Transactional`은 **Service에만** 붙인다. Controller·Mapper에는 붙이지 않는다
- 조회 메서드는 `@Transactional(readOnly = true)`
- **잔액 갱신과 이력 적재는 반드시 한 트랜잭션**으로 묶는다 (예: 상환 처리 = 스케줄 상태 변경 + 대출잔액 차감 + 상환 이력 INSERT)

---

## 7. 날짜 · 시간

- `DATE` → `LocalDate`, `DATETIME` → `LocalDateTime`, 타임존은 `Asia/Seoul`
- 타임존은 세 곳에서 맞춘다: DB(`docker-compose.yml`의 `--default-time-zone=+09:00`), JDBC(`connectionTimeZone=Asia/Seoul`),
  JVM(`build.gradle`의 `bootRun`·`test`에 `-Duser.timezone=Asia/Seoul`). **운영 JVM에도 같은 옵션을 넣는다**
- `created_at`/`updated_at`은 DB DEFAULT가 채운다 → INSERT/UPDATE 문에 쓰지 않는다

---

## 8. 환경 · 프로필 · 비밀값

| 프로필 | 파일 | 용도 |
|---|---|---|
| (공통) | `application.yml` | datasource 기본값, Flyway·Batch·MyBatis 설정 |
| `local` (기본) | `application-local.yml` | seed 경로 추가, `out-of-order` 허용 |
| `test` | (파일 없음) | 통합 테스트. local seed를 읽지 않는다 |

- 프로필을 지정하지 않으면 `local`이다 (`spring.profiles.default`)
- `docker-compose.yml`과 Spring은 **같은 `.env`를 읽는다** (`spring.config.import: optional:file:.env[.properties]`).
  로컬 포트·계정 변경은 `.env`만 고친다
- **비밀값(DB 운영 비밀번호, 외부 API 키 등)을 커밋되는 파일에 넣지 않는다.** `.env`는 ignore 대상이고 `.env.sample`에는 로컬 전용 값만 둔다
- 운영은 `SPRING_PROFILES_ACTIVE`, `SPRING_DATASOURCE_URL`/`_USERNAME`/`_PASSWORD` 환경변수로 주입한다

---

## 9. DB 마이그레이션 (Flyway)

스키마는 **앱 기동 시 Flyway가 적용한다.** 손으로 SQL을 돌리는 절차는 없다. 원본 스키마는 `docs/reference/ddl.sql`이다.

```
src/main/resources/db/
├─ migration/                       # 모든 환경 공통 (application.yml)
│  ├─ V0__init.sql                  # 초기 테이블 15개
│  ├─ V1__code_table_data.sql       # 코드성 데이터 (enum id와 1:1)
│  └─ V2__spring_batch_schema.sql   # Spring Batch 메타 테이블 (spring-batch-core 5.2.6 원본)
└─ seed/local/                      # local 프로필 전용 샘플 데이터 (V900번대)
```

- **적용된 파일은 수정하지 않는다.** 체크섬 불일치로 기동이 실패한다. 변경은 **새 버전 파일**로 추가한다
- 파일명은 `V{버전}__{설명}.sql` — 언더스코어 **두 개**
- **버전 번호는 모든 location을 통틀어 겹치면 안 된다.** 스키마는 `V3`, `V4`…, 로컬 seed는 `V900`번대
- **location은 환경별 폴더를 정확히 지정한다.** `classpath:db/seed`처럼 상위 폴더를 주면 하위 폴더가 전부 적용된다
- local은 `out-of-order: true`다 — seed(V900)가 적용된 뒤 추가된 `V3`이 순서 역전으로 막히지 않게 하기 위함이다
- `baseline-version: 0` — 테이블이 이미 있는데 이력 테이블이 없는 DB에 처음 붙으면 `V0`을 적용된 것으로 본다. 빈 DB는 `V0`부터 실행한다
- 모든 환경에 필요한 데이터(코드성 데이터 등)는 `migration/`에, 샘플 데이터는 `seed/{환경}/`에 둔다
- Spring Batch 메타 테이블도 Flyway가 관리한다 (`spring.batch.jdbc.initialize-schema: never`). Batch 버전을 올려 스키마가 바뀌면 새 마이그레이션으로 반영한다

> `docker compose up -d`는 빈 DB만 만든다. 로컬 스키마를 처음부터 다시 만들려면 `docker compose down -v`.

---

## 10. 배치 (Spring Batch)

- Job 코드는 `batch/{job,step,reader,writer,listener}`에 둔다
- `spring.batch.job.enabled: false` — 앱 기동 시 Job을 자동 실행하지 않는다. Job은 명시적으로 실행한다
- 배치에서도 금액·트랜잭션 규칙(§5, §6)을 그대로 지킨다. chunk 단위 커밋 경계를 설계 단계에서 명시한다
- Job 테스트는 `spring-batch-test`(`@SpringBatchTest`)와 Testcontainers를 함께 쓴다

---

## 11. 테스트

| 종류 | 설정 | DB |
|---|---|---|
| 단위 (Service, 계산 로직) | `@ExtendWith(MockitoExtension.class)` 또는 순수 JUnit | 불필요 |
| 통합 (Mapper, Flyway, Batch) | `@SpringBootTest` + `@Import(TestcontainersConfig.class)` + `@ActiveProfiles("test")` | Testcontainers MySQL 8.4 |

- `TestcontainersConfig`의 `@ServiceConnection`이 datasource 접속 정보를 덮어쓴다. 테스트는 `.env`·로컬 DB와 무관하다
- **단언은 AssertJ로 통일**한다. JUnit `Assertions.*`를 쓰지 않는다
- 금액 비교는 `isEqualByComparingTo`
- 이자·상환액 계산은 경계값(첫 회차, 마지막 회차 잔액 보정, 0원 이자 등)을 반드시 테스트한다
- 데이터를 바꾸는 통합 테스트는 `@Transactional`로 롤백하거나 테스트 안에서 정리한다

---

## 12. 참고 자료

- `docs/erd.md` — 테이블 15개 목록, 도메인 구분, 관계도
- `docs/reference/table_definition.md` — 컬럼 상세 (원본 테이블 정의서)
- `docs/reference/ddl.sql` — 원본 DDL
- `docs/reference/가계대출상품설명서.pdf` — 연체 근거(⑥항 등) 업무 규칙의 근거
- `docs/reference/repayment-amount-calculator-verification.md` — 이전 프로젝트의 상환금액 계산기 검증 기록 (계산식·검증 방법 참고용)

---

## 13. Git / GitHub 워크플로우

### Git 컨벤션

원본 규칙은 [CONTRIBUTING.md](./CONTRIBUTING.md)에 있다.

| 브랜치 | 역할 | 들어오는 PR | 병합 |
|---|---|---|---|
| `main` | 배포 브랜치. 릴리스된 코드만 담는다 | `develop` → `main` 릴리스 PR | Merge commit |
| `develop` | 통합 브랜치이자 **기본 브랜치** | `feat/*` · `fix/*` · `docs/*` 등 | Squash and Merge |

- `main`, `develop` 모두 **직접 push 금지**
- 작업 브랜치는 **`develop`에서 분기해 `develop`으로 PR**한다
- 브랜치: `{type}/{설명}` — **영어 소문자 + 하이픈** (`feat`, `fix`, `docs`, `chore`, `style`, `refactor`, `test`, `perf`, `ci`)
- 커밋: `type: 한국어 설명` (Conventional Commits)

### GitHub 작업 플로우 (이슈 → PR)

새 작업은 아래 순서를 **사용자 확인 없이 연속으로** 진행한다 (`gh` CLI, 이미 인증돼 있음).
이슈 등록부터 PR 생성까지는 이 문서로 사전 승인된 자동화 범위다.

#### 1. 이슈 등록 (`gh issue create --repo Loan-Repayment-System/repayment-backend`)

제목은 `[TYPE] 한국어 설명`. **타입 라벨은 제목 접두사와 1:1로 반드시 매칭**한다.

| 접두사 | 템플릿 | 라벨 | 본문 섹션 |
|---|---|---|---|
| `[BUG]` | `bug_report.md` | `🐛 버그` | 버그 설명 / 재현 방법 / 예상 동작 / 실제 동작 / 스크린샷 / 환경 / 추가 정보 |
| `[FEAT]` | `feature_request.md` | `✨ 기능` | 기능 요약 / 배경 및 이유 / 구현 방법 나열 / 추가 정보 |
| `[TASK]` | `task.md` | `🛠️ 작업` | 작업 내용 / 작업 목표 / 세부 작업 목록(체크박스) / 참고 자료 / 완료 조건 |
| `[REFACTOR]` | `refactor.md` | `🧹 리팩터링` | 리팩토링 대상 / 현재 문제점 / 개선 방향 / 예상 영향 범위 / 주의사항 |
| `[DOCS]` | `docs.md` | `📝 문서` | 문서 작업 내용 / 작업 이유 / 작업 범위(체크박스) / 참고 자료 |
| `[QUESTION]` | `question.md` | (없음) | 질문 내용 / 배경 / 시도해본 것 / 참고 자료 |
| `[LEARN]` | `learn.md` | `📚 학습` | 무엇을 했나 / 왜 이렇게 했나 / 어떻게 동작하나 / 몰랐다가 알게 된 것 / 참고 사항 |

내용상 해당되면 아래 라벨을 **추가로** 붙인다:
- 우선순위 `🔼 높음` / `➖ 보통` / `🔽 낮음`
- 상태 `🧊 대기` / `👀 검토필요` / `🚧 진행중`
- 영역 `🌐 API` / `🗄️ DB` / `🔌 외부연동` / `🔐 인증` / `🔒 보안` / `✅ 테스트` / `🧭 도메인` / `🧰 인프라`
- 긴급하면 `🔥 긴급`

관련 마일스톤이 있으면 `--milestone`으로 연결한다.

#### 2. 브랜치 생성

```bash
git checkout develop && git pull origin develop
git checkout -b {type}/{설명}
```

#### 3. 구현 + 검증

`./gradlew build`로 컴파일과 테스트 통과를 확인한다(Docker 실행 필요). 위 컨벤션(1~12)을 지킨다.

#### 4. 커밋

`type: 한국어 설명` 형식. 이 작업과 무관한 미추적 파일은 같이 add하지 않는다.

#### 5. push

```bash
git push -u origin {브랜치명}
```

#### 6. PR 생성 (`gh pr create --repo Loan-Repayment-System/repayment-backend --base develop`)

- 제목: `[#이슈번호] type: 작업 내용`
- 본문: 관련 이슈(`closes #N`) / 작업 내용 / 변경 유형(체크박스) / 체크리스트 / 리뷰어에게 전달할 내용
- 이슈가 마일스톤에 연결돼 있으면 PR도 같은 마일스톤에 연결한다

이슈가 여러 개로 쪼개지는 큰 작업은 먼저 상위 이슈나 마일스톤으로 묶고, 하위 작업 단위로 이 플로우를 반복한다.

#### 릴리스 (develop → main)

릴리스는 사람이 결정한다. **자동화 범위에 포함하지 않는다.**
`develop` → `main` PR을 만들고 **Merge commit**으로 병합한다.

## 라벨 전체 목록

| 라벨 | 설명 |
|---|---|
| `✨ 기능` | 새 기능 또는 기존 기능 개선 |
| `🐛 버그` | 재현 가능한 오류 또는 예상과 다른 동작 |
| `🛠️ 작업` | 구현, 설정, 정리처럼 실행할 작업 |
| `📝 문서` | README, API 문서, 가이드, 주석 개선 |
| `🧹 리팩터링` | 동작 변경 없는 구조와 품질 개선 |
| `✅ 테스트` | 테스트 추가, 수정, 검증 작업 |
| `🔒 보안` | 취약점, 민감정보, 권한 오남용 방지 |
| `🧰 인프라` | CI/CD, 빌드, 설정, 의존성, 개발환경 |
| `🌐 API` | 컨트롤러, 요청과 응답, API 스펙 |
| `🗄️ DB` | 데이터베이스, 마이그레이션, 쿼리, 모델 |
| `🔌 외부연동` | 외부 API, SDK, 웹훅, 연동 오류 |
| `🔐 인증` | 로그인, 토큰, 인가, 세션 |
| `🔥 긴급` | 즉시 처리해야 하는 장애, 보안, 차단 이슈 |
| `🧭 도메인` | 핵심 비즈니스 로직, 정책, 유스케이스 |
| `📚 학습` | 새롭게 배운 것 공유 |
| `➖ 보통` / `🔼 높음` / `🔽 낮음` | 우선순위 |
| `🧊 대기` / `👀 검토필요` / `🚧 진행중` | 진행 상태 |
