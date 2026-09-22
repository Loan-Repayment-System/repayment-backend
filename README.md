# repayment-backend

대출금 상환 시스템의 백엔드 서버입니다.

## 프로젝트 소개

KB국민은행 부트캠프(KB IT's Your Life)를 수료한 뒤, 배운 기술을 실제 은행 업무에 적용해 보려고 시작한 개인 프로젝트입니다.
은행 웹사이트에 공개된 가계대출 상품설명서와 약관을 분석해 대출금 상환 업무를 정리하고, 이를 시스템으로 구현합니다.

기술적으로는 다음 세 가지를 목표로 합니다.

- 동시에 같은 대출을 상환해도 잔액이 어긋나지 않도록 동시성을 제어합니다.
- 같은 요청이 여러 번 들어와도 한 번만 처리되도록 멱등성을 보장합니다.
- 조회 쿼리와 대량 배치 처리의 성능을 개선합니다.

## 주요 기능

- 고객 상환 API
- 여신 담당자 업무 API
- 연체 배치, 자동 상환 배치, 여신 현황 집계 배치

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
