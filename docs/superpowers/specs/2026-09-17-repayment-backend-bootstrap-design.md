# repayment-backend 초기 세팅 설계

- 작성일: 2026-09-17
- 상태: 승인됨 (구현 전)
- 레포: `Loan-Repayment-System/repayment-backend` (기존 레포를 삭제하고 같은 이름으로 재생성)

## 1. 배경과 목표

대출 상환 시스템(Loan Repayment System)의 백엔드를 **Spring Boot 기반으로 처음부터** 세팅한다.
개인 포트폴리오(여신 코어뱅킹 직무 어필)가 목적이므로 금융권 실무에서 쓰는 스택을 우선한다.

기존 `woo-in/LoanServicing` 레포는 삭제될 예정이다. 그 레포의 스키마와 참고 문서는
**링크가 아니라 파일 자체를 새 레포로 옮긴다.**

### 이번 범위

- 앱 기동, Docker 기반 로컬 MySQL, Flyway 스키마 버전 관리, 테스트 기반, CI, 문서, 레포 설정

### 범위 밖 (필요해질 때 별도 설계)

- 도메인 코드(enum·TypeHandler·Mapper·Service·Controller), Batch Job 구현
- 공통 응답 봉투 / 전역 예외 처리 — 첫 API를 만들 때 함께 설계한다
- Spring Security·JWT, springdoc-openapi, Actuator
- 운영 프로필 파일, 배포 파이프라인

### 완료 기준

1. `cp .env.sample .env && docker compose up -d && ./gradlew bootRun` → MySQL에 도메인 테이블 15개,
   Spring Batch 메타 테이블, 코드성 데이터가 생성된다
2. `./gradlew build`가 docker compose 없이(Testcontainers) 통과한다
3. GitHub Actions CI가 `main`, `develop` 양쪽에서 통과한다

## 2. 기술 스택

| 항목 | 선택 | 비고 |
|---|---|---|
| 언어 / 런타임 | Java 17 | |
| 프레임워크 | Spring Boot 3.5.x (구현 시점 최신 패치) | `spring-boot-starter-web`, `-validation` |
| 빌드 | Gradle (Groovy DSL) + Wrapper | |
| 영속성 | MyBatis (`mybatis-spring-boot-starter` 3.0.x) | JPA 미사용 |
| DB | MySQL 8.4 (`mysql-connector-j`) | 로컬은 Docker Compose |
| 마이그레이션 | Flyway (`flyway-core` + `flyway-mysql`) | **앱 기동 시 실행** (방식 A) |
| 배치 | `spring-boot-starter-batch` | 메타 테이블도 Flyway로 관리 |
| 기타 | Lombok | |
| 테스트 | JUnit 5, AssertJ, Mockito, `spring-batch-test`, Testcontainers(MySQL) + `@ServiceConnection` | |
| CI | GitHub Actions | 빌드·테스트만 |

### 결정 기록

- **MyBatis를 쓰는 이유**: 은행·카드사 코어 시스템의 사실상 표준. 잔액 갱신·대량 배치·집계 쿼리를 SQL로 직접 통제한다.
- **MySQL 8.4를 쓰는 이유**: 사용자 결정. 기존 LoanServicing DDL이 MySQL 문법이다.
- **Flyway를 앱 기동 시 실행하는 이유(방식 A)**: 앱·테스트(Testcontainers)·운영이 같은 경로로 스키마를 적용한다.
  `redgate/flyway` 컨테이너를 따로 두는 방식(B)은 테스트용 설정이 결국 따로 필요해 설정이 두 벌이 된다.
- **Testcontainers를 쓰는 이유**: 로컬 DB 상태와 무관하게 항상 깨끗한 스키마로 테스트하고, CI에 MySQL 서비스가 필요 없다.

## 3. 저장소 구조

```
repayment-backend/
├─ .github/
│  ├─ ISSUE_TEMPLATE/            # 7종 (bug_report, feature_request, task, refactor, docs, question, learn) + config.yml
│  ├─ PULL_REQUEST_TEMPLATE.md
│  └─ workflows/ci.yml
├─ docs/
│  ├─ erd.md                     # 테이블 15개 목록 + 도메인 구분
│  ├─ reference/                 # LoanServicing/docs에서 원본 그대로 복사
│  │  ├─ table_definition.md
│  │  ├─ ddl.sql
│  │  ├─ repayment-amount-calculator-verification.md
│  │  └─ 가계대출상품설명서.pdf
│  └─ superpowers/specs/         # 이 문서
├─ gradle/wrapper/, gradlew, gradlew.bat
├─ src/main/java/com/repayment/
│  ├─ RepaymentApplication.java
│  ├─ domain/{customer,product,loan,repayment}/   # .gitkeep만
│  ├─ batch/{job,step,reader,writer,listener}/    # .gitkeep만
│  └─ global/{config,exception,util}/             # .gitkeep만
├─ src/main/resources/
│  ├─ application.yml
│  ├─ application-local.yml
│  ├─ mapper/.gitkeep
│  └─ db/
│     ├─ migration/
│     │  ├─ V0__init.sql
│     │  ├─ V1__code_table_data.sql
│     │  └─ V2__spring_batch_schema.sql
│     └─ seed/local/.gitkeep     # 로컬 샘플 데이터 (V900번대)
├─ src/test/java/com/repayment/
│  ├─ TestcontainersConfig.java
│  ├─ RepaymentApplicationTests.java
│  ├─ FlywayMigrationTest.java
│  └─ LocalSeedLocationTest.java
├─ .env.sample, .gitignore, .gitattributes
├─ docker-compose.yml
├─ build.gradle, settings.gradle
├─ README.md, AGENTS.md, CLAUDE.md, CONTRIBUTING.md, LICENSE(MIT)
```

도메인 패키지 구분은 LoanServicing CoreBanking의 기준을 따른다.

| 패키지 | 담당 테이블 |
|---|---|
| `customer` | `member`, `primary_account` |
| `product` | `loan_product`, `interest_type`, `repayment_method` |
| `loan` | `loan_contract`, `loan_account`, `loan_status_history`, `loan_status` |
| `repayment` | `repayment_schedule`, `repayment_history`, `delinquency_history`, `repayment_schedule_status`, `delinquency_status`, `delinquency_reason` |

## 4. 로컬 개발 환경

### 4.1 흐름

```bash
cp .env.sample .env
docker compose up -d        # MySQL 8.4만 기동 (빈 DB)
./gradlew bootRun           # 기동 시 Flyway가 db/migration + db/seed/local 적용
./gradlew build             # 테스트는 Testcontainers가 MySQL을 직접 띄운다
```

### 4.2 `docker-compose.yml`

- 서비스는 `mysql` 하나. 이미지 `mysql:8.4`, `container_name: repayment-mysql`
- `command: --default-time-zone=+09:00`, `environment.TZ: Asia/Seoul`
  - 이름(`Asia/Seoul`)이 아니라 오프셋을 쓰는 이유: 공식 이미지는 타임존 테이블이 비어 있어 이름을 쓰면 기동에 실패한다
- `MYSQL_ROOT_PASSWORD`, `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`는 `.env`에서 받는다
- 포트 `"${MYSQL_PORT:-3306}:3306"`, 이름 있는 볼륨 `repayment-mysql-data`
- `healthcheck`: `mysqladmin ping` (interval 5s, timeout 5s, retries 10)

### 4.3 `.env`

- `.env.sample`만 커밋하고 `.env`는 `.gitignore` 대상이다
- 값: `MYSQL_ROOT_PASSWORD`, `MYSQL_DATABASE=repayment`, `MYSQL_USER=repayment`, `MYSQL_PASSWORD`, `MYSQL_PORT=3306`
- Spring이 같은 파일을 읽는다: `spring.config.import: optional:file:.env[.properties]`
  - `.env`가 없으면(CI·테스트) 무시되고 기본값이 쓰인다
- **비밀값(외부 API 키 등)은 커밋되는 파일에 절대 넣지 않는다.** LoanServicing은 DB 비밀번호와
  한국은행 API 키가 `application.properties`에 평문 커밋돼 공개 레포에 노출됐다

## 5. 애플리케이션 설정

### 5.1 `application.yml`

```yaml
spring:
  application:
    name: repayment-backend
  config:
    import: optional:file:.env[.properties]
  profiles:
    default: local
  datasource:
    url: jdbc:mysql://localhost:${MYSQL_PORT:3306}/${MYSQL_DATABASE:repayment}?connectionTimeZone=Asia/Seoul&characterEncoding=UTF-8
    username: ${MYSQL_USER:repayment}
    password: ${MYSQL_PASSWORD:}
  flyway:
    enabled: true
    baseline-on-migrate: true
    baseline-version: 0
    locations: classpath:db/migration
  batch:
    jdbc:
      initialize-schema: never
    job:
      enabled: false

mybatis:
  mapper-locations: classpath:mapper/**/*.xml
  configuration:
    map-underscore-to-camel-case: true
```

- `profiles.default: local` — 프로필 미지정 시 local
- `batch.jdbc.initialize-schema: never` — 메타 테이블 생성 책임을 Flyway(V2) 하나로 모은다
- `batch.job.enabled: false` — 기동 시 Job 자동 실행을 막는다. Job은 명시적으로 실행한다

### 5.2 `application-local.yml`

```yaml
spring:
  flyway:
    locations: classpath:db/migration, classpath:db/seed/local
    out-of-order: true
```

- `classpath:db/seed`처럼 상위 폴더를 주면 하위 폴더 전부가 스캔되므로 **환경별 폴더를 정확히 지정**한다
- 버전 번호는 모든 location을 통틀어 겹치면 안 되므로 seed는 **V900번대**를 쓴다
- seed(V900)가 적용된 뒤 스키마 V3이 추가되면 Flyway가 순서 역전으로 막으므로 local에만 `out-of-order: true`를 켠다
- `dev` 등 다른 프로필 파일은 필요해질 때 만든다

### 5.3 운영

운영 프로필 파일은 만들지 않는다. 운영은 `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD`와
`SPRING_PROFILES_ACTIVE`를 환경변수로 주입한다. 운영에는 seed가 적용되지 않는다(공통 `locations`가 migration만).

### 5.4 타임존

- DB: compose의 `--default-time-zone=+09:00`
- JDBC: `connectionTimeZone=Asia/Seoul`
- JVM: `build.gradle`의 `bootRun`, `test` 태스크에 `-Duser.timezone=Asia/Seoul`

## 6. 스키마 마이그레이션

원본은 `docs/reference/ddl.sql`(LoanServicing `docs/ddl.sql` 사본)이다.

| 파일 | 내용 | 변환 규칙 |
|---|---|---|
| `V0__init.sql` | 테이블 15개 CREATE | 원본의 생성 순서(코드성 → 핵심 → 운영 → 상환)와 컬럼·제약을 그대로 유지. `DROP TABLE IF EXISTS` 제거. 각 테이블에 `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4` 명시 |
| `V1__code_table_data.sql` | 코드성 테이블 초기 데이터 | 원본 5번 섹션 INSERT를 그대로 옮김 (id는 enum과 1:1 — 순서 변경 금지) |
| `V2__spring_batch_schema.sql` | Spring Batch 메타 테이블 | 사용 중인 spring-batch-core jar의 `org/springframework/batch/core/schema-mysql.sql`을 그대로 복사 |

코드성 데이터를 `seed`가 아니라 `migration`에 두는 이유: enum이 이 id에 의존하므로 운영에도 반드시 있어야 한다.

### 규칙 (AGENTS.md에 명시)

- 적용된 마이그레이션 파일은 **수정하지 않는다** (체크섬 불일치로 기동 실패). 변경은 새 버전 파일로 추가한다
- 파일명: `V{버전}__{설명}.sql` (언더스코어 두 개)
- `baseline-version: 0`이므로 테이블이 이미 있는 DB에 처음 붙이면 `V0`은 건너뛴다. 빈 DB는 `V0`부터 실행된다
- 로컬 샘플 데이터는 `db/seed/local/V9xx__*.sql`

## 7. 테스트

### 7.1 공통 설정

```java
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {
    @Bean
    @ServiceConnection
    MySQLContainer<?> mysql() {
        return new MySQLContainer<>("mysql:8.4")
                .withCommand("--default-time-zone=+09:00");
    }
}
```

- 통합 테스트는 `@SpringBootTest` + `@Import(TestcontainersConfig.class)`
- `@ServiceConnection`이 datasource 접속 정보를 덮어쓴다 (`.env`·yml 기본값보다 우선)
- 기본은 `@ActiveProfiles("test")` — local seed가 섞이지 않는다. `application-test.yml`은 만들지 않는다

### 7.2 초기 테스트

| 테스트 | 프로필 | 검증 내용 |
|---|---|---|
| `RepaymentApplicationTests` | test | 컨텍스트 기동 (MyBatis·Flyway·Batch 배선) |
| `FlywayMigrationTest` | test | `flyway_schema_history`에 V0·V1·V2가 성공으로 기록됨 / 도메인 테이블 15개 존재 / `BATCH_JOB_INSTANCE` 등 Batch 메타 테이블 존재 / 코드성 데이터 건수(interest_type 2, repayment_method 3, loan_status 4, repayment_schedule_status 4, delinquency_status 2, delinquency_reason 7)와 id·name이 원본과 일치 |
| `LocalSeedLocationTest` | local | `db/seed/local` location이 포함된 상태로 기동과 마이그레이션이 성공함 |

### 7.3 규칙 (AGENTS.md에 명시)

- 단언은 AssertJ로 통일
- 테스트 메서드명은 한글 + 언더스코어
- 금액·이율은 `BigDecimal`, 비교는 `isEqualByComparingTo`

## 8. CI

`.github/workflows/ci.yml`

- 트리거: `pull_request`, `push` (브랜치 `main`, `develop`), `workflow_dispatch`
- `concurrency`: 같은 ref의 이전 실행 취소
- 잡 `build` (`ubuntu-latest`)
  1. `actions/checkout@v4`
  2. `actions/setup-java@v4` (temurin 17)
  3. `gradle/actions/setup-gradle@v4`
  4. `./gradlew build`
  5. `actions/upload-artifact@v4`로 `build/reports/tests/test` 업로드 (`if: always()`)
- MySQL `services` 없음 — Testcontainers가 러너의 Docker를 사용한다
- 배포 잡 없음

## 9. 문서

| 파일 | 내용 |
|---|---|
| `README.md` | 소개, 기술 스택 표, 로컬 실행 순서, 마이그레이션 추가 방법, 폴더 구조, 문서 안내 |
| `AGENTS.md` | 코드 컨벤션·작업 플로우 정본: 기술 스택, 패키지 구조, Flyway 규칙(§6), 금액 `BigDecimal`, 테스트 규칙(§7.3), 비밀값 관리, Git/GitHub 플로우, 라벨 목록 |
| `CLAUDE.md` | `@AGENTS.md` import + Claude Code 전용 메모 |
| `CONTRIBUTING.md` | 브랜치 전략(`develop` 기본·분기, Squash 머지, 릴리스는 `develop`→`main` Merge commit), 브랜치 네이밍, 커밋 컨벤션(`type: 한국어 설명`), PR·리뷰 규칙 |
| `docs/erd.md` | 테이블 15개 목록·도메인 구분 표, 상세는 `docs/reference/table_definition.md` 참조 |
| `docs/reference/*` | LoanServicing `docs/`의 파일 4개를 **수정 없이** 복사 |
| `.github/ISSUE_TEMPLATE/*`, `PULL_REQUEST_TEMPLATE.md` | 이전 repayment-backend와 동일 (config.yml의 링크만 새 레포 기준) |
| `LICENSE` | MIT, Copyright (c) 2026 Loan-Repayment-System |

## 10. 레포 설정 (구현 단계 순서)

1. 기존 `Loan-Repayment-System/repayment-backend` 삭제 (권한 부족 시 사용자가 `gh auth refresh -h github.com -s delete_repo` 실행)
2. 같은 이름의 public 레포 생성, 설명 "대출 상환 시스템(Loan Repayment System)의 백엔드"
3. 로컬 `/Users/woo-in/repayment-backend`의 `main`을 push, `develop` 브랜치 생성
4. 기본 브랜치 `develop`, wiki 비활성화
5. GitHub 기본 라벨 삭제 후 `heartbeat-kb-town/fitwallet-backend` 라벨 21개 복제
6. topics: `backend`, `spring-boot`, `mybatis`, `mysql`, `flyway`, `spring-batch`
7. CI가 `main`, `develop` 양쪽에서 통과하는지 확인

## 11. 위험과 대응

| 위험 | 대응 |
|---|---|
| Testcontainers가 로컬 Docker를 못 찾음 | README에 Docker Desktop 실행 필요를 명시 |
| Flyway의 MySQL 8.4 지원 경고 | Boot 3.5 관리 버전의 Flyway로 기동 로그를 확인하고, 경고만 나오면 README에 기록 |
| 로컬 3306 포트 충돌 | `.env`의 `MYSQL_PORT`만 바꾸면 compose·Spring 양쪽에 반영 |
| LoanServicing 삭제 전 문서 누락 | 구현 시 `docs/reference/` 파일 4개의 해시를 원본과 비교해 확인 |
