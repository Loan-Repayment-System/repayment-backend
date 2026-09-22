# repayment-backend

대출 상환 시스템(Loan Repayment System)의 백엔드 서버

## 기술 스택

| 항목 | 선택 |
|---|---|
| 언어 | Java 17 |
| 프레임워크 | Spring Boot 3.5.16 (Web, Validation, Batch) |
| 영속성 | MyBatis (mybatis-spring-boot-starter 3.0.5) |
| DB | MySQL 8.4 (로컬은 Docker Compose) |
| 스키마 버전 관리 | Flyway (앱 기동 시 적용) |
| 빌드 | Gradle 8.14.3 |
| 테스트 | JUnit 5, AssertJ, Mockito, Spring Batch Test, Testcontainers |
| CI | GitHub Actions |

## 로컬 실행

### 0. 준비물

- JDK 17
- [Docker Desktop](https://www.docker.com/products/docker-desktop/) — 실행 중이어야 한다 (`docker compose version`으로 확인)

### 1. 환경 변수 파일 준비

```bash
cp .env.sample .env
```

`docker-compose.yml`과 Spring이 **같은 `.env`를 읽는다.** 3306 포트가 이미 쓰이고 있으면
`.env`의 `MYSQL_PORT`만 바꾸면 된다. `.env`는 커밋되지 않는다.

### 2. MySQL 기동

```bash
docker compose up -d
docker compose ps        # repayment-mysql이 healthy인지 확인
```

이 단계에서는 **빈 데이터베이스만** 만들어진다. 스키마와 데이터는 다음 단계에서 Flyway가 넣는다.

### 3. 애플리케이션 기동

```bash
./gradlew bootRun        # http://localhost:8080
```

기동하면서 Flyway가 `db/migration`(스키마·코드성 데이터·Batch 메타 테이블)과
`db/seed/local`(로컬 샘플 데이터)을 적용한다. 프로필을 지정하지 않으면 `local`로 뜬다.

적용 결과 확인:

```bash
docker exec repayment-mysql mysql -urepayment -prepayment1234 repayment \
  -e "SELECT version, description, success FROM flyway_schema_history;"
```

### 4. 정리

```bash
docker compose down      # 컨테이너만 정리 (데이터 유지)
docker compose down -v   # 데이터까지 초기화
```

### Windows

| 단계 | cmd | PowerShell |
|---|---|---|
| 1 | `copy .env.sample .env` | `Copy-Item .env.sample .env` |
| 3 | `gradlew.bat bootRun` | `.\gradlew.bat bootRun` |

Git Bash를 쓰면 위의 macOS/Linux 명령을 그대로 쓸 수 있다.

## 테스트

```bash
./gradlew build          # 컴파일 + 테스트 + 패키징
```

통합 테스트는 **Testcontainers**가 MySQL 8.4 컨테이너를 직접 띄운다. `docker compose up`은 필요 없고
Docker Desktop만 실행 중이면 된다. 로컬 DB의 데이터는 테스트에 영향을 주지 않는다.

## 스키마 변경

```
src/main/resources/db/
├─ migration/                       # 모든 환경 공통
│  ├─ V0__init.sql                  # 초기 테이블 15개
│  ├─ V1__code_table_data.sql       # 코드성 테이블 데이터 (enum id와 1:1)
│  └─ V2__spring_batch_schema.sql   # Spring Batch 메타 테이블
└─ seed/local/                      # local 프로필 전용 샘플 데이터 (V900번대)
```

- 스키마를 바꾸려면 `db/migration/`에 **`V{다음번호}__{설명}.sql`을 새로 추가**한다. 다음 기동 때 자동 적용된다
- **이미 적용된 파일은 고치지 않는다.** 체크섬이 달라져 기동이 실패한다
- 로컬 샘플 데이터는 `db/seed/local/V9xx__{설명}.sql`로 추가한다

> 기동 로그에 `Flyway upgrade recommended: MySQL 8.4 is newer than this version of Flyway`
> 경고가 나온다. Spring Boot 3.5가 관리하는 Flyway 버전이 MySQL 8.4를 공식 테스트 대상에 넣지 않아서
> 나오는 경고이며, 마이그레이션 적용과 테스트에는 문제가 없다.

## 폴더 구조

```
repayment-backend/
├─ src/main/java/com/repayment/
│  ├─ RepaymentApplication.java
│  ├─ api/{customer,product,loan,repayment}/        # HTTP 표면
│  ├─ batch/{job,step,reader,writer,listener}/      # 배치 표면
│  ├─ domain/{customer,product,loan,repayment}/     # 업무 로직 (api·batch가 공유)
│  └─ global/{code,config,dto,exception,util}/
├─ src/main/resources/
│  ├─ application.yml, application-local.yml
│  ├─ mapper/                       # MyBatis XML
│  └─ db/{migration,seed/local}/
├─ src/test/java/com/repayment/     # Testcontainers 기반 통합 테스트
├─ docs/
│  ├─ erd.md
│  └─ reference/                    # 테이블 정의서, 원본 DDL, 상품설명서 등
├─ docker-compose.yml, .env.sample
└─ build.gradle
```

## 문서

- [CLAUDE.md](./CLAUDE.md) — 코드 컨벤션과 작업 플로우의 정본
- [CONTRIBUTING.md](./CONTRIBUTING.md) — 브랜치 전략, 커밋·PR 규칙
- [docs/erd.md](./docs/erd.md) — 테이블 목록과 관계
- [docs/reference/](./docs/reference) — 테이블 정의서, 원본 DDL, 가계대출 상품설명서, 상환금액 계산기 검증 기록
