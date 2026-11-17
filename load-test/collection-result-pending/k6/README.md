# 펌뱅킹 장애 부하 테스트

정상 3분 → 장애 10분 → 복구 15분 동안 상환을 초당 15건 보낸다.

## 환경 변수

| 이름 | 기본값 | 설명 |
|---|---|---|
| `BASE_URL` | `http://localhost:8080` | 상환 서버 주소 |
| `LOAN_ID_START` | `1` | 더미 대출의 첫 번호 |
| `LOAN_COUNT` | `30000` | 더미 대출 개수 |
| `REPAYMENT_AMOUNT` | `10000` | 상환 1건 금액 |
| `FAULT_START_URL` | 없음 | 펌뱅킹 장애 켜기 API. `POST`로 부르고 200을 기대한다 |
| `FAULT_END_URL` | 없음 | 펌뱅킹 장애 끄기 API |

## 실행

```bash
FAULT_START_URL=<장애 켜기 API> FAULT_END_URL=<장애 끄기 API> \
K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write \
K6_PROMETHEUS_RW_TREND_STATS=p(95),p(99),max \
k6 run -o experimental-prometheus-rw load-test/collection-result-pending/k6/firmbanking-outage.js
```

요약은 `summary.json`으로 남는다.

## 보는 지표

| 지표 | 보는 것 |
|---|---|
| `http_req_failed{name:POST /api/loans/{id}/repayments}` | 200, 202 말고 5xx나 시간 초과가 나는지 |
| `dropped_iterations` | 0인지. 0이 아니면 의도한 초당 요청 수가 나가지 않은 것 |
