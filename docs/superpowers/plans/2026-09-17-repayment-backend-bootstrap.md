# repayment-backend 초기 세팅 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Spring Boot 3.5 + MyBatis + Flyway + Spring Batch 기반 repayment-backend 스캐폴드를 만들고 GitHub 레포로 공개한다.

**Architecture:** 앱 기동 시 Flyway가 `db/migration`(공통)과 `db/seed/local`(local 프로필)을 적용한다. 로컬 MySQL은 Docker Compose, 테스트는 Testcontainers(`@ServiceConnection`)를 쓴다. 도메인 코드는 만들지 않고 빈 패키지만 둔다.

**Tech Stack:** Java 17, Spring Boot 3.5.16, Gradle 8.14.3, io.spring.dependency-management 1.1.7, mybatis-spring-boot-starter 3.0.5, MySQL 8.4, Flyway(Boot 관리 버전), Spring Batch(Boot 관리 버전), Testcontainers(Boot 관리 버전), Lombok

**Spec:** `docs/superpowers/specs/2026-09-17-repayment-backend-bootstrap-design.md`

## Global Constraints

- 작업 디렉터리: `/Users/woo-in/repayment-backend` (이미 git init, 브랜치 `main`)
- 베이스 패키지 `com.repayment`, 메인 클래스 `com.repayment.RepaymentApplication`
- 커밋 메시지: `type: 한국어 설명` + 마지막 줄 `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`
- 비밀값을 커밋되는 파일에 넣지 않는다 (`.env`는 ignore)
- 원본 스키마: `/Users/woo-in/LoanServicing/docs/ddl.sql` — 컬럼·제약·생성 순서를 바꾸지 않는다
- 로컬 3306 포트는 다른 MySQL이 점유 중이다 → 로컬 검증 시 `.env`의 `MYSQL_PORT=3317`
- 기존 GitHub 레포 `Loan-Repayment-System/repayment-backend` 삭제는 **사용자가 직접** 한다. 에이전트는 삭제하지 않는다

---

### Task 1: Gradle 골격과 컨텍스트 기동

**Files:**
- Create: `settings.gradle`, `build.gradle`, `gradle/wrapper/*`, `gradlew`, `gradlew.bat`, `.gitignore`, `.gitattributes`
- Create: `src/main/java/com/repayment/RepaymentApplication.java`
- Create: `src/main/resources/application.yml`
- Create: 빈 패키지 `.gitkeep` — `src/main/java/com/repayment/domain/{customer,product,loan,repayment}/`, `batch/{job,step,reader,writer,listener}/`, `global/{config,exception,util}/`, `src/main/resources/mapper/`, `src/main/resources/db/migration/`
- Test: `src/test/java/com/repayment/TestcontainersConfig.java`, `src/test/java/com/repayment/RepaymentApplicationTests.java`

**Interfaces:**
- Produces: `com.repayment.TestcontainersConfig` (`@TestConfiguration`, `@Bean @ServiceConnection MySQLContainer<?> mysql()`) — 이후 모든 통합 테스트가 `@Import(TestcontainersConfig.class)`로 사용

- [ ] **Step 1: Gradle wrapper 생성**

```bash
cd /Users/woo-in/repayment-backend
printf "rootProject.name = 'repayment-backend'\n" > settings.gradle
gradle wrapper --gradle-version 8.14.3 --distribution-type bin
```

- [ ] **Step 2: `build.gradle` 작성**

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.5.16'
    id 'io.spring.dependency-management' version '1.1.7'
}

group = 'com.repayment'
version = '0.0.1-SNAPSHOT'
description = '대출 상환 시스템 백엔드'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

configurations {
    compileOnly {
        extendsFrom annotationProcessor
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-batch'
    implementation 'org.mybatis.spring.boot:mybatis-spring-boot-starter:3.0.5'
    implementation 'org.flywaydb:flyway-core'
    implementation 'org.flywaydb:flyway-mysql'
    runtimeOnly 'com.mysql:mysql-connector-j'
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.batch:spring-batch-test'
    testImplementation 'org.mybatis.spring.boot:mybatis-spring-boot-starter-test:3.0.5'
    testImplementation 'org.springframework.boot:spring-boot-testcontainers'
    testImplementation 'org.testcontainers:junit-jupiter'
    testImplementation 'org.testcontainers:mysql'
    testCompileOnly 'org.projectlombok:lombok'
    testAnnotationProcessor 'org.projectlombok:lombok'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

// DB(docker compose)와 JDBC URL을 KST로 맞췄으므로 JVM도 KST로 고정한다.
def timezoneJvmArg = '-Duser.timezone=Asia/Seoul'

tasks.named('test') {
    useJUnitPlatform()
    jvmArgs timezoneJvmArg
}

tasks.named('bootRun') {
    jvmArgs timezoneJvmArg
}
```

- [ ] **Step 3: `.gitignore`, `.gitattributes` 작성**

`.gitignore`:
```gitignore
# Gradle
.gradle/
build/
!gradle/wrapper/gradle-wrapper.jar

# IDE
.idea/
*.iml
.vscode/
out/

# 로컬 환경 변수 (비밀값) — .env.sample만 커밋한다
.env

# OS
.DS_Store
Thumbs.db
```

`.gitattributes`:
```gitattributes
/gradlew text eol=lf
*.bat text eol=crlf
*.jar binary
*.pdf binary
```

- [ ] **Step 4: 실패하는 컨텍스트 테스트 작성**

`src/test/java/com/repayment/TestcontainersConfig.java`:
```java
package com.repayment;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MySQLContainer;

/**
 * 통합 테스트용 MySQL 컨테이너. {@code @ServiceConnection}이 datasource 접속 정보를 덮어쓴다.
 * docker compose의 MySQL과 같은 버전·타임존을 쓴다.
 */
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

`src/test/java/com/repayment/RepaymentApplicationTests.java`:
```java
package com.repayment;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class RepaymentApplicationTests {

    @Test
    void 애플리케이션_컨텍스트가_기동된다() {
    }
}
```

- [ ] **Step 5: 실패 확인**

Run: `./gradlew test --tests com.repayment.RepaymentApplicationTests`
Expected: FAIL — `@SpringBootConfiguration`을 찾지 못함 (메인 클래스 없음)

- [ ] **Step 6: 메인 클래스와 `application.yml` 작성**

`src/main/java/com/repayment/RepaymentApplication.java`:
```java
package com.repayment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RepaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(RepaymentApplication.class, args);
    }
}
```

`src/main/resources/application.yml`:
```yaml
spring:
  application:
    name: repayment-backend
  config:
    # docker-compose.yml과 같은 .env를 읽는다. 없으면(CI·테스트) 무시되고 아래 기본값이 쓰인다.
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
    # 테이블이 이미 있는 DB에 처음 붙으면 V0을 적용된 것으로 본다. 빈 DB는 V0부터 실행한다.
    baseline-version: 0
    # 공통(운영 포함)은 스키마만. 샘플 데이터는 프로필별 파일에서 location을 추가한다.
    locations: classpath:db/migration
  batch:
    jdbc:
      # 메타 테이블은 Flyway(V2__spring_batch_schema.sql)가 만든다.
      initialize-schema: never
    job:
      # 기동 시 Job을 자동 실행하지 않는다.
      enabled: false

mybatis:
  mapper-locations: classpath:mapper/**/*.xml
  configuration:
    map-underscore-to-camel-case: true
```

빈 패키지 생성:
```bash
for p in domain/customer domain/product domain/loan domain/repayment \
         batch/job batch/step batch/reader batch/writer batch/listener \
         global/config global/exception global/util; do
  mkdir -p src/main/java/com/repayment/$p && touch src/main/java/com/repayment/$p/.gitkeep
done
mkdir -p src/main/resources/mapper src/main/resources/db/migration
touch src/main/resources/mapper/.gitkeep src/main/resources/db/migration/.gitkeep
```

- [ ] **Step 7: 통과 확인** (Docker Desktop 실행 필요)

Run: `./gradlew test --tests com.repayment.RepaymentApplicationTests`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "chore: Spring Boot 프로젝트 골격 구성" -m "Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 2: Flyway 마이그레이션 (V0~V2)

**Files:**
- Create: `src/main/resources/db/migration/V0__init.sql`, `V1__code_table_data.sql`, `V2__spring_batch_schema.sql`
- Delete: `src/main/resources/db/migration/.gitkeep`
- Test: `src/test/java/com/repayment/FlywayMigrationTest.java`

**Interfaces:**
- Consumes: `TestcontainersConfig` (Task 1)

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/com/repayment/FlywayMigrationTest.java`:
```java
package com.repayment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class FlywayMigrationTest {

    private static final List<String> DOMAIN_TABLES = List.of(
            "interest_type", "repayment_method", "loan_status",
            "repayment_schedule_status", "delinquency_status", "delinquency_reason",
            "member", "primary_account", "loan_product", "loan_contract",
            "loan_account", "loan_status_history",
            "repayment_schedule", "delinquency_history", "repayment_history");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 마이그레이션_V0부터_V2까지_성공으로_기록된다() {
        List<Map<String, Object>> history = jdbcTemplate.queryForList(
                "SELECT version, success FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank");

        assertThat(history)
                .extracting(row -> row.get("version"), row -> ((Number) row.get("success")).intValue())
                .containsExactly(tuple("0", 1), tuple("1", 1), tuple("2", 1));
    }

    @Test
    void 도메인_테이블이_모두_생성된다() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()",
                String.class);

        assertThat(tables).containsAll(DOMAIN_TABLES);
    }

    @Test
    void 스프링_배치_메타_테이블이_생성된다() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()",
                String.class);

        assertThat(tables).contains(
                "BATCH_JOB_INSTANCE", "BATCH_JOB_EXECUTION", "BATCH_JOB_EXECUTION_PARAMS",
                "BATCH_STEP_EXECUTION", "BATCH_STEP_EXECUTION_CONTEXT", "BATCH_JOB_EXECUTION_CONTEXT",
                "BATCH_STEP_EXECUTION_SEQ", "BATCH_JOB_EXECUTION_SEQ", "BATCH_JOB_SEQ");
    }

    @Test
    void 이자종류_코드가_원본과_같다() {
        assertCodes("SELECT interest_type_id AS id, name FROM interest_type ORDER BY id",
                tuple(1, "고정금리"), tuple(2, "변동금리"));
    }

    @Test
    void 상환방법_코드가_원본과_같다() {
        assertCodes("SELECT repayment_method_id AS id, name FROM repayment_method ORDER BY id",
                tuple(1, "원금만기일시상환"), tuple(2, "원리금균등상환"), tuple(3, "원금균등상환"));
    }

    @Test
    void 대출상태_코드가_원본과_같다() {
        assertCodes("SELECT loan_status_id AS id, name FROM loan_status ORDER BY id",
                tuple(1, "정상"), tuple(2, "연체"), tuple(3, "완제"), tuple(4, "기한이익상실"));
    }

    @Test
    void 상환스케줄상태_코드가_원본과_같다() {
        assertCodes("SELECT repayment_schedule_status_id AS id, name FROM repayment_schedule_status ORDER BY id",
                tuple(1, "예정"), tuple(2, "납부 기한 도래"), tuple(3, "납입 완료"), tuple(4, "연체"));
    }

    @Test
    void 연체상태_코드가_원본과_같다() {
        assertCodes("SELECT delinquency_status_id AS id, name FROM delinquency_status ORDER BY id",
                tuple(1, "진행중"), tuple(2, "해소"));
    }

    @Test
    void 연체사유_코드가_원본과_같다() {
        assertCodes("SELECT delinquency_reason_id AS id, name FROM delinquency_reason ORDER BY id",
                tuple(1, "만기일시 + 이자연체 + 1개월 미만"),
                tuple(2, "만기일시 + 이자연체 + 1개월 이상"),
                tuple(3, "만기일시 + 이자연체 + 1개월 이상 + ⑥항 적용"),
                tuple(4, "만기일시 + 원금연체"),
                tuple(5, "분할상환 + 연속 1회"),
                tuple(6, "분할상환 + 연속 2회 이상"),
                tuple(7, "분할상환 + 연속 2회 이상 + ⑥항 적용"));
    }

    private void assertCodes(String sql, org.assertj.core.groups.Tuple... expected) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        assertThat(rows)
                .extracting(row -> ((Number) row.get("id")).intValue(), row -> row.get("name"))
                .containsExactly(expected);
    }
}
```

> 원본 DDL의 테이블 수: 코드성 6 + 핵심 3(member, primary_account, loan_product) + 운영 3(loan_contract, loan_account, loan_status_history) + 상환 3 = **15개**. 스펙의 "14개"는 오기이므로 이 Task에서 스펙 문서의 숫자도 15로 고친다.

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests com.repayment.FlywayMigrationTest`
Expected: FAIL — `flyway_schema_history` 테이블 없음 / 테이블 목록 불일치

- [ ] **Step 3: `V0__init.sql` 생성**

원본에서 `DROP TABLE IF EXISTS` 줄과 5번(초기 데이터) 섹션을 제거하고, 각 `CREATE TABLE`의 닫는 `);`를 `) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;`로 바꾼다.

```bash
python3 - <<'PY'
import re, pathlib
src = pathlib.Path('/Users/woo-in/LoanServicing/docs/ddl.sql').read_text()
schema = src[:src.index('-- 5. 코드성 테이블 초기 데이터')]
schema = schema[:schema.rstrip().rfind('-- ====')]           # 5번 섹션 머리 구분선 제거
schema = re.sub(r'^DROP TABLE IF EXISTS [^;]+;\n', '', schema, flags=re.M)
schema = re.sub(r'^\);$', ') ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;', schema, flags=re.M)
header = ('-- ============================================================\n'
          '-- V0: 초기 스키마 (원본: docs/reference/ddl.sql)\n'
          '-- 적용된 뒤에는 수정하지 않는다. 변경은 새 버전 파일로 추가한다.\n'
          '-- ============================================================\n\n')
pathlib.Path('src/main/resources/db/migration/V0__init.sql').write_text(header + schema.rstrip() + '\n')
PY
grep -c 'CREATE TABLE' src/main/resources/db/migration/V0__init.sql   # 15
grep -c 'DROP TABLE' src/main/resources/db/migration/V0__init.sql     # 0
```

- [ ] **Step 4: `V1__code_table_data.sql` 생성**

```bash
python3 - <<'PY'
import pathlib
src = pathlib.Path('/Users/woo-in/LoanServicing/docs/ddl.sql').read_text()
data = src[src.index('INSERT INTO interest_type'):]
header = ('-- ============================================================\n'
          '-- V1: 코드성 테이블 초기 데이터 (원본: docs/reference/ddl.sql 5번 섹션)\n'
          '-- enum id와 1:1 대응 — id 순서 변경 금지. 모든 환경에 필요하므로 seed가 아니라 migration에 둔다.\n'
          '-- ============================================================\n\n')
pathlib.Path('src/main/resources/db/migration/V1__code_table_data.sql').write_text(header + data.rstrip() + '\n')
PY
grep -c 'INSERT INTO' src/main/resources/db/migration/V1__code_table_data.sql   # 6
```

- [ ] **Step 5: `V2__spring_batch_schema.sql` 생성**

사용 중인 spring-batch-core jar에서 그대로 추출한다.

```bash
# 이 프로젝트가 실제로 쓰는 spring-batch-core 버전을 확인한 뒤, 그 jar의 원본 스크립트를 복사한다.
VER=$(./gradlew -q dependencyInsight --dependency org.springframework.batch:spring-batch-core --configuration runtimeClasspath \
      | grep -o 'spring-batch-core:[0-9][0-9.]*' | head -1 | cut -d: -f2)
JAR=$(find ~/.gradle/caches/modules-2 -name "spring-batch-core-$VER.jar" | head -1)
echo "$VER $JAR"
{ printf -- '-- V2: Spring Batch 메타 테이블 (spring-batch-core-%s.jar 의 org/springframework/batch/core/schema-mysql.sql 원본)\n\n' "$VER"
  unzip -p "$JAR" org/springframework/batch/core/schema-mysql.sql; } \
  > src/main/resources/db/migration/V2__spring_batch_schema.sql
rm src/main/resources/db/migration/.gitkeep
grep -c 'CREATE TABLE' src/main/resources/db/migration/V2__spring_batch_schema.sql   # 9 (메타 6 + 시퀀스 3)
```

- [ ] **Step 6: 스펙 문서의 테이블 수 수정**

`docs/superpowers/specs/2026-09-17-repayment-backend-bootstrap-design.md`에서 "테이블 14개" / "14개"를 모두 "15개"로 바꾼다.

```bash
sed -i '' 's/테이블 14개/테이블 15개/g; s/14개 CREATE/15개 CREATE/g; s/도메인 테이블 14개/도메인 테이블 15개/g' docs/superpowers/specs/2026-09-17-repayment-backend-bootstrap-design.md
grep -n '14' docs/superpowers/specs/2026-09-17-repayment-backend-bootstrap-design.md   # 출력 없어야 함
```

- [ ] **Step 7: 통과 확인**

Run: `./gradlew test`
Expected: PASS (RepaymentApplicationTests 1 + FlywayMigrationTest 9)

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat: LoanServicing 스키마 기반 Flyway 초기 마이그레이션 추가" -m "Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 3: 로컬 개발 환경 (Docker Compose + local 프로필)

**Files:**
- Create: `docker-compose.yml`, `.env.sample`, `src/main/resources/application-local.yml`, `src/main/resources/db/seed/local/.gitkeep`
- Test: `src/test/java/com/repayment/LocalSeedLocationTest.java`

**Interfaces:**
- Consumes: `TestcontainersConfig` (Task 1), 마이그레이션 V0~V2 (Task 2)

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/com/repayment/LocalSeedLocationTest.java`:
```java
package com.repayment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * local 프로필은 샘플 데이터 경로(db/seed/local)를 추가로 읽는다.
 * 상위 폴더(db/seed)를 지정하면 다른 환경의 seed까지 섞이므로 경로를 정확히 고정한다.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("local")
class LocalSeedLocationTest {

    @Autowired
    private FlywayProperties flywayProperties;

    @Test
    void local_프로필은_스키마와_로컬_시드_경로만_읽는다() {
        assertThat(flywayProperties.getLocations())
                .containsExactly("classpath:db/migration", "classpath:db/seed/local");
    }

    @Test
    void local_프로필은_순서가_뒤바뀐_마이그레이션을_허용한다() {
        assertThat(flywayProperties.isOutOfOrder()).isTrue();
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests com.repayment.LocalSeedLocationTest`
Expected: FAIL — locations가 `[classpath:db/migration]`, outOfOrder가 false

- [ ] **Step 3: `application-local.yml`과 seed 폴더 작성**

`src/main/resources/application-local.yml`:
```yaml
spring:
  flyway:
    # 환경별 seed 폴더를 정확히 지정한다. classpath:db/seed로 주면 하위 폴더(다른 환경)까지 전부 적용된다.
    # seed 버전은 V900번대를 쓴다 — 버전 번호는 모든 location을 통틀어 겹치면 안 된다.
    locations:
      - classpath:db/migration
      - classpath:db/seed/local
    # seed(V900)가 적용된 뒤 추가되는 스키마 버전(V3..)이 순서 역전으로 막히지 않게 한다.
    out-of-order: true
```

```bash
mkdir -p src/main/resources/db/seed/local && touch src/main/resources/db/seed/local/.gitkeep
```

- [ ] **Step 4: 통과 확인**

Run: `./gradlew test`
Expected: PASS (12건)

- [ ] **Step 5: `docker-compose.yml`, `.env.sample` 작성**

`docker-compose.yml`:
```yaml
services:
  mysql:
    image: mysql:8.4
    container_name: repayment-mysql
    restart: unless-stopped
    # 서버 타임존을 KST로 고정한다. 이름(Asia/Seoul)을 쓰면 공식 이미지에 타임존 테이블이 없어 기동에 실패하므로
    # 오프셋을 쓴다(한국은 서머타임이 없다). 값을 바꾸면 `docker compose down -v`로 재생성해야 반영된다.
    command: --default-time-zone=+09:00
    environment:
      TZ: Asia/Seoul
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      MYSQL_DATABASE: ${MYSQL_DATABASE}
      MYSQL_USER: ${MYSQL_USER}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD}
    ports:
      - "${MYSQL_PORT:-3306}:3306"
    # 스키마·데이터는 여기서 넣지 않는다. 앱이 뜰 때 Flyway가 적용한다.
    volumes:
      - repayment-mysql-data:/var/lib/mysql
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-uroot", "-p${MYSQL_ROOT_PASSWORD}"]
      interval: 5s
      timeout: 5s
      retries: 10

volumes:
  repayment-mysql-data:
```

`.env.sample`:
```properties
# 이 파일을 .env로 복사해서 사용한다: cp .env.sample .env
# docker-compose.yml과 Spring(application.yml의 spring.config.import)이 같은 .env를 읽는다.
# 로컬 개발 전용 값이다. 운영 비밀값이나 외부 API 키는 커밋되는 파일에 넣지 않는다.

MYSQL_ROOT_PASSWORD=root1234
MYSQL_DATABASE=repayment
MYSQL_USER=repayment
MYSQL_PASSWORD=repayment1234
# 3306이 이미 쓰이고 있으면 이 값만 바꾼다 (compose와 Spring 양쪽에 반영된다)
MYSQL_PORT=3306
```

- [ ] **Step 6: 로컬 실행 수동 검증**

```bash
cp .env.sample .env && sed -i '' 's/^MYSQL_PORT=.*/MYSQL_PORT=3317/' .env
docker compose up -d
until [ "$(docker inspect -f '{{.State.Health.Status}}' repayment-mysql)" = healthy ]; do sleep 2; done
(sleep 60 | ./gradlew bootRun > build/bootrun.log 2>&1 &)
until grep -q 'Started RepaymentApplication\|APPLICATION FAILED' build/bootrun.log 2>/dev/null; do sleep 2; done
grep -E 'Successfully applied|Started RepaymentApplication|APPLICATION FAILED' build/bootrun.log
docker exec repayment-mysql mysql -urepayment -prepayment1234 repayment -e \
  "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='repayment'; SELECT version, success FROM flyway_schema_history; SELECT @@global.time_zone;"
```
Expected: `Successfully applied 3 migrations`, `Started RepaymentApplication`, 테이블 수 25 (도메인 15 + Batch 9 + flyway_schema_history 1), time_zone `+09:00`

정리:
```bash
pkill -f 'sleep 60' || true
docker compose down -v
rm .env
```

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "chore: Docker Compose 로컬 MySQL과 local 프로필 Flyway 설정 추가" -m "Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 4: 문서, 참고 자료, GitHub 템플릿, CI

**Files:**
- Create: `docs/reference/{table_definition.md,ddl.sql,repayment-amount-calculator-verification.md,가계대출상품설명서.pdf}` (원본 복사)
- Create: `docs/erd.md`, `README.md`, `AGENTS.md`, `CLAUDE.md`, `CONTRIBUTING.md`, `LICENSE`
- Create: `.github/ISSUE_TEMPLATE/*` (7종 + config.yml), `.github/PULL_REQUEST_TEMPLATE.md`, `.github/workflows/ci.yml`

- [ ] **Step 1: 참고 문서 복사와 무결성 확인**

```bash
mkdir -p docs/reference
cp /Users/woo-in/LoanServicing/docs/{table_definition.md,ddl.sql,repayment-amount-calculator-verification.md,가계대출상품설명서.pdf} docs/reference/
(cd /Users/woo-in/LoanServicing/docs && shasum -a 256 table_definition.md ddl.sql repayment-amount-calculator-verification.md 가계대출상품설명서.pdf) > /tmp/src.sha
(cd docs/reference && shasum -a 256 -c /tmp/src.sha)
```
Expected: 4개 모두 `OK`

- [ ] **Step 2: GitHub 템플릿 복사**

이전 repayment-backend 스캐폴드(`/private/tmp/claude-501/-Users-woo-in-LoanServicing/6325dd81-d4bb-4e4c-9171-65814d6a03ea/scratchpad/repayment-backend/.github`)에서 `ISSUE_TEMPLATE/`과 `PULL_REQUEST_TEMPLATE.md`만 가져온다. 해당 경로가 없으면 `gh api`로 `heartbeat-kb-town/fitwallet-backend`의 같은 파일을 받은 뒤 `config.yml` URL과 `learn.md` 라벨을 아래처럼 맞춘다.

```bash
mkdir -p .github/workflows
cp -R /private/tmp/claude-501/-Users-woo-in-LoanServicing/6325dd81-d4bb-4e4c-9171-65814d6a03ea/scratchpad/repayment-backend/.github/ISSUE_TEMPLATE .github/
cp /private/tmp/claude-501/-Users-woo-in-LoanServicing/6325dd81-d4bb-4e4c-9171-65814d6a03ea/scratchpad/repayment-backend/.github/PULL_REQUEST_TEMPLATE.md .github/
grep -n 'url:' .github/ISSUE_TEMPLATE/config.yml   # .../Loan-Repayment-System/repayment-backend/blob/main/CONTRIBUTING.md
grep -n '^labels' .github/ISSUE_TEMPLATE/learn.md   # labels: "📚 학습"
```

- [ ] **Step 3: CI 작성**

`.github/workflows/ci.yml`:
```yaml
name: CI

on:
  pull_request:
    branches: [main, develop]
  push:
    branches: [main, develop]
  workflow_dispatch:

concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: true

permissions:
  contents: read

jobs:
  build:
    runs-on: ubuntu-latest
    # MySQL services를 두지 않는다. 통합 테스트는 Testcontainers가 러너의 Docker로 MySQL을 띄운다.
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Build with Gradle (compile + test + package)
        run: ./gradlew build

      - name: Upload test report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: test-report
          path: build/reports/tests/test
          retention-days: 7
```

- [ ] **Step 4: 문서 작성**

각 파일의 필수 내용 (스펙 §9 기준):

`LICENSE` — MIT 전문, 첫 줄 `Copyright (c) 2026 Loan-Repayment-System`

`docs/erd.md`:
- 제목 `# ERD`, "스키마의 정본은 `src/main/resources/db/migration/`이다" 문장
- 표: 테이블 | 한글명 | 도메인 패키지 | 구분 — 15행 (스펙 §3의 도메인 구분 표 기준, 한글명은 `docs/reference/table_definition.md`의 제목에서)
- "컬럼 상세는 [table_definition.md](./reference/table_definition.md)" 링크

`README.md`:
- 제목·소개 한 줄
- 기술 스택 표 (Global Constraints의 Tech Stack과 동일)
- 로컬 실행: Docker Desktop 필요 → `cp .env.sample .env` → `docker compose up -d` → `./gradlew bootRun` → 확인 쿼리(`docker exec repayment-mysql mysql -urepayment -prepayment1234 repayment -e "SELECT version, success FROM flyway_schema_history;"`) → 정리(`docker compose down` / `down -v`)
- Windows 명령 대응 표 (`copy .env.sample .env`, `gradlew.bat bootRun`)
- 테스트: `./gradlew build` — Testcontainers 사용, docker compose 불필요, Docker Desktop만 실행
- 스키마 변경: `db/migration/V{다음번호}__{설명}.sql` 추가, 적용된 파일 수정 금지(체크섬 오류), 로컬 샘플 데이터는 `db/seed/local/V9xx__*.sql`
- 폴더 구조 트리 (스펙 §3)
- 문서 안내: AGENTS.md / CONTRIBUTING.md / docs/erd.md / docs/reference/

`AGENTS.md` (코드 컨벤션 정본) 섹션:
1. 빌드·실행 명령
2. 기술 스택 표 + "Spring Boot 3.5 / Java 17 / JPA 미사용(MyBatis)" 명시
3. 패키지 구조 (스펙 §3 트리와 도메인 구분 표)
4. 네이밍: `{도메인}Controller`, `{도메인}Service`, `{도메인}Mapper` + `resources/mapper/{도메인}/{도메인}Mapper.xml`, 요청 DTO `{동작}{대상}Request`, 응답 DTO `{대상}{용도}Response`, 매퍼 메서드 조회 `find*/count*/exists*`·변경 `insert*/update*/delete*`, 테스트 메서드 한글+언더스코어
5. MyBatis: `#{}`만 사용(`${}` 금지), `SELECT *` 금지, `map-underscore-to-camel-case`가 있으므로 수동 `AS` 별칭 금지
6. 금액·이율: `BigDecimal` 필수(`double`/`float` 금지), 원본 스키마 정밀도 `DECIMAL(23,4)`·`DECIMAL(10,5)`, 반올림 시 `RoundingMode` 명시
7. 코드성 테이블: id는 enum과 1:1, 순서 변경 금지
8. 트랜잭션: `@Transactional`은 Service에만, 조회는 `readOnly = true`
9. DB 마이그레이션: 스펙 §6 규칙 전부 + §5.2의 location·V900·out-of-order 설명
10. 환경·비밀값: 프로필(기본 local), `.env` 공유 방식, 비밀값 커밋 금지, 운영은 `SPRING_DATASOURCE_*` 환경변수, 타임존 3곳
11. 배치: `spring.batch.job.enabled=false`, 메타 테이블은 Flyway가 관리, Job 코드는 `batch/{job,step,reader,writer,listener}`
12. 테스트: `@SpringBootTest` + `@Import(TestcontainersConfig.class)` + `@ActiveProfiles("test")`, AssertJ 통일, 금액 비교 `isEqualByComparingTo`
13. Git/GitHub 워크플로우: 이전 repayment-backend `AGENTS.md` §14(브랜치 표, 이슈 접두사·라벨 표, 브랜치→구현→커밋→push→PR 순서, `--repo Loan-Repayment-System/repayment-backend --base develop`, 릴리스는 사람이 결정)와 라벨 전체 목록을 그대로 옮긴다. 원본 경로: `/private/tmp/claude-501/-Users-woo-in-LoanServicing/6325dd81-d4bb-4e4c-9171-65814d6a03ea/scratchpad/repayment-backend/AGENTS.md` (단, "위 컨벤션(1~13)"은 "(1~12)"로 맞춘다)

`CLAUDE.md`:
```markdown
# CLAUDE.md

이 저장소의 코드 컨벤션과 작업 플로우 정본은 [AGENTS.md](./AGENTS.md)다.
아래 import로 전부 가져오므로, **규칙을 고칠 때는 `AGENTS.md`만 고친다.**

@AGENTS.md

---

## Claude Code 전용

- `AGENTS.md`의 "GitHub 작업 플로우"는 사용자 확인 없이 연속 실행해도 되는 사전 승인 범위다.
  작업 브랜치는 `develop`에서 분기하고 PR base도 `develop`이다.
- 통합 테스트는 Docker가 필요하다. `./gradlew test`가 컨테이너 기동에서 실패하면 Docker Desktop 실행 여부부터 확인한다.
- 로컬 MySQL 포트는 개발자마다 다를 수 있다 (`.env`의 `MYSQL_PORT`).
- 커밋 메시지 끝에 `Co-Authored-By: Claude <noreply@anthropic.com>` 트레일러를 붙인다.
```

`CONTRIBUTING.md` — 이전 repayment-backend 스캐폴드의 `CONTRIBUTING.md`를 그대로 복사한다(`develop` 기본·분기, Squash, 릴리스 Merge commit, 네이밍·커밋·PR·리뷰 규칙).

```bash
cp /private/tmp/claude-501/-Users-woo-in-LoanServicing/6325dd81-d4bb-4e4c-9171-65814d6a03ea/scratchpad/repayment-backend/CONTRIBUTING.md .
```

- [ ] **Step 5: 검증**

```bash
grep -rnI 'fitwallet\|heartbeat\|LoanServicing/' --exclude-dir=.git --exclude-dir=build --exclude-dir=reference --exclude-dir=superpowers . || echo clean
./gradlew build
```
Expected: `clean`, BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "docs: 프로젝트 문서, 참고 자료, GitHub 템플릿과 CI 추가" -m "Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 5: GitHub 레포 생성과 설정

**전제:** 사용자가 기존 `Loan-Repayment-System/repayment-backend`를 삭제했다.

- [ ] **Step 1: 기존 레포 삭제 여부 확인**

```bash
gh api repos/Loan-Repayment-System/repayment-backend --jq .full_name
```
Expected: `404 Not Found`. 아직 존재하면 **중단하고 사용자에게 삭제를 요청**한다 (에이전트가 삭제하지 않는다).

- [ ] **Step 2: 레포 생성과 push**

```bash
gh repo create Loan-Repayment-System/repayment-backend --public \
  --description "대출 상환 시스템(Loan Repayment System)의 백엔드" \
  --source . --remote origin --push
git push origin main:develop
```

- [ ] **Step 3: 레포 설정**

```bash
R=Loan-Repayment-System/repayment-backend
gh api -X PATCH repos/$R -f default_branch=develop -F has_wiki=false --jq .default_branch
gh api -X PUT repos/$R/topics -f 'names[]=backend' -f 'names[]=spring-boot' -f 'names[]=mybatis' \
  -f 'names[]=mysql' -f 'names[]=flyway' -f 'names[]=spring-batch' --jq '.names|join(",")'
gh label list -R $R --limit 100 --json name --jq '.[].name' | while read -r l; do gh label delete "$l" -R $R --yes; done
gh label clone heartbeat-kb-town/fitwallet-backend -R $R
gh label list -R $R --limit 100 | wc -l
```
Expected: `develop`, topics 6개, 라벨 21개

- [ ] **Step 4: CI 확인**

```bash
R=Loan-Repayment-System/repayment-backend
until [ "$(gh run list -R $R --json status --jq '[.[]|select(.status!="completed")]|length')" = 0 ] && [ "$(gh run list -R $R --json status --jq length)" -ge 2 ]; do sleep 10; done
gh run list -R $R --limit 5
```
Expected: `main`, `develop` 두 실행 모두 `success`
