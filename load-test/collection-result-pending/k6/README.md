# 펌뱅킹 장애 부하 테스트

정상 3분 → 장애 10분 → 복구 15분 동안 상환을 초당 15건 보낸다.

## 환경 변수

| 이름 | 기본값 | 설명 |
|---|---|---|
| `BASE_URL` | `http://localhost:8080` | 상환 서버 주소. compose로 실행하면 `http://repayment:8080` |
| `LOAN_ID_START` | `1` | 더미 대출의 첫 번호 |
| `LOAN_COUNT` | `30000` | 더미 대출 개수 |
| `REPAYMENT_AMOUNT` | `10000` | 상환 1건 금액 |
| `FAULT` | 켜짐 | `off`면 장애 없이 돌린다 |
| `TOXIPROXY_URL` | `http://localhost:8474` | 장애를 켜고 끄는 Toxiproxy API 주소. compose로 실행하면 `http://toxiproxy:8474` |

## 실행

상위 폴더(`load-test/collection-result-pending/`)의 docker compose로 상환 서버·펌뱅킹·두 DB·Prometheus·Grafana를 띄우고, k6도 compose로 실행한다. 펌뱅킹은 옆에 clone한 `firmbanking-mock`을 빌드하고, 다른 곳에 있으면 `FIRMBANKING_MOCK_DIR`로 경로를 넘긴다. 일마감이 한국은행 API를 부르므로 루트 `.env`에 `BOK_API_KEY`가 있어야 한다.

명령은 모두 `load-test/collection-result-pending/`에서 실행한다.

1. 띄우기

   ```bash
   docker compose up -d --build
   ```

2. 더미 데이터 넣기. 두 앱이 떠서 Flyway가 테이블을 만든 뒤 넣는다. 상환 DB에는 대출 3만 건, 펌뱅킹 DB에는 그 상환 계좌 3만 개와 출금 동의를 넣는다.

   ```bash
   docker compose exec -T repayment-mysql mysql -urepayment -prepayment1234 repayment < sql/seed.sql
   docker compose exec -T firmbanking-mysql mysql -ufirmbanking -pfirmbanking1234 firmbanking < sql/firmbanking-seed.sql
   ```

3. 일마감 실행. 3만 건의 1회차를 확정한다(로컬에서 약 40초).

   ```bash
   docker compose exec repayment curl -s -X POST localhost:8080/api/batch/daily-closing
   ```

4. 기준 실행: 장애 없이 돌린다. 모든 상환이 200이어야 한다.

   ```bash
   FAULT=off docker compose run --rm k6
   ```

5. 정합성 확인

   ```bash
   docker compose exec -T repayment-mysql mysql -t --default-character-set=utf8mb4 -urepayment -prepayment1234 repayment < sql/verify.sql
   docker compose exec -T firmbanking-mysql mysql -t --default-character-set=utf8mb4 -ufirmbanking -pfirmbanking1234 firmbanking < sql/firmbanking-verify.sql
   ```

6. 초기화: 두 DB를 새로 만들고 두 앱을 다시 띄운다. Prometheus 지표는 남는다. 그다음 2·3을 다시 한다.

   ```bash
   docker compose up -d --force-recreate --renew-anon-volumes repayment-mysql firmbanking-mysql repayment firmbanking
   ```

7. 장애 실행 후 5로 정합성을 확인한다. 3분에 Toxiproxy가 펌뱅킹 응답을 60초 늦추고, 13분에 푼다. 상환 서버는 응답 대기 5초를 넘겨 결과를 모르는 상태가 된다.

   ```bash
   docker compose run --rm k6
   ```

요약은 `summary-baseline.json`, `summary-outage.json`으로 남는다. 지표는 Grafana(http://localhost:3000)의 `펌뱅킹 장애 부하 테스트` 대시보드에서 본다.

## 정합성 SQL

상환 DB(`sql/verify.sql`)

| # | 검사 | 기대 | 의미 |
|---|---|---|---|
| 1 | 집금 요청 상태별 건수 | 요청·집금 완료 0건 | 결과를 모르던 집금이 장애 뒤 모두 자동으로 처리됐다 |
| 2 | 상환 완료 요청과 상환의 건수·금액 | 같음 | 집금된 돈이 빠짐없이 상환에 반영됐다 |
| 3 | 상환 완료 요청과 펌뱅킹 이체 성공의 건수·금액 | 같음 (펌뱅킹 1번과 나란히 본다) | 고객 계좌에서 빠진 돈과 상환 서버가 반영한 돈이 일치한다 |
| 4 | 대출별 상환 건수 | 1건을 넘는 대출 0행 | 재요청이 이중 집금을 만들지 않았다 |
| 5 | 상환 완료 요청마다 같은 거래번호의 상환 | 빠진 요청 0행 | 집금 완료 요청 하나하나가 자기 상환으로 반영됐다 |

펌뱅킹 DB(`sql/firmbanking-verify.sql`)

| # | 검사 | 기대 | 의미 |
|---|---|---|---|
| 1 | 집금 이체 성공 건수·금액 | 상환 DB 2번의 상환 완료 요청과 같음 | 고객 계좌에서 빠진 돈과 상환 서버가 반영한 돈이 일치한다 |
| 2 | 계좌별 집금 성공 건수 | 1건을 넘는 계좌 0행 | 같은 고객 돈이 두 번 빠지지 않았다 |

## 보는 지표

Grafana 대시보드의 패널 4개다.

| 패널 | 출처 | 보는 것 |
|---|---|---|
| 상태별 집금 요청 건수 | 상환 서버 | 복구 뒤 REQUESTED가 0으로 수렴하는지 |
| 상환 응답 코드별 초당 건수 | k6 | 장애 구간에 202로 바뀌고, 복구 뒤 200으로 돌아오는지 |
| 상환 실패율 | k6 | 200, 202 말고 5xx나 시간 초과가 나는지 |
| `dropped_iterations` | k6 | 0인지. 0이 아니면 의도한 초당 요청 수가 나가지 않은 것 |
