# 펌뱅킹 장애 부하 테스트

정상 3분 → 장애 10분 → 복구 15분 동안 상환 초당 15건, 대출 조회 초당 150건을 보낸다.

## 환경 변수

| 이름 | 기본값 | 설명 |
|---|---|---|
| `BASE_URL` | `http://localhost:8080` | 상환 서버 주소 |
| `LOAN_ID_START` | `1` | 더미 대출의 첫 번호 |
| `LOAN_COUNT` | `30000` | 더미 대출 개수 |
| `REPAYMENT_AMOUNT` | `10000` | 상환 1건 금액 |
| `FAULT` | 켜짐 | `off`면 장애 없이 돌린다 |
| `FAULT_START_URL` | 없음 | 펌뱅킹 장애 켜기 API. `POST`로 부르고 200을 기대한다 |
| `FAULT_END_URL` | 없음 | 펌뱅킹 장애 끄기 API |
| `BASELINE_P95_MS` | 없음 | 기준 실행의 조회 p95. 장애·복구 구간 조회 p95가 이 값의 2배 미만이어야 통과 |

## 실행

1. 기준 실행: 장애 없이 돌려 정상 구간 조회 p95를 잰다.

   ```bash
   FAULT=off k6 run load-test/collection-result-pending/k6/firmbanking-outage.js
   ```

2. 장애 실행: 1의 정상 구간 조회 p95를 `BASELINE_P95_MS`로 넘긴다.

   ```bash
   BASELINE_P95_MS=<기준 p95> \
   FAULT_START_URL=<장애 켜기 API> FAULT_END_URL=<장애 끄기 API> \
   K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write \
   K6_PROMETHEUS_RW_TREND_STATS=p(95),p(99),max \
   k6 run -o experimental-prometheus-rw load-test/collection-result-pending/k6/firmbanking-outage.js
   ```

요약은 `summary-baseline.json`, `summary-outage.json`으로 남는다.

## 판정 기준

| 지표 | 기준 |
|---|---|
| 상환 실패율 | 0%. 200 완료와 202 결과 미확인만 정상 |
| 조회 실패율 | 구간별 1% 미만 |
| 조회 p95 | 장애·복구 구간이 기준 실행 p95의 2배 미만 |
| `dropped_iterations` | 0 |
