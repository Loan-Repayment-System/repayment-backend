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
| `FAULT_START_URL` | 없음 | 펌뱅킹 장애 켜기 API. `POST`로 부르고 200을 기대한다 |
| `FAULT_END_URL` | 없음 | 펌뱅킹 장애 끄기 API |

## 실행

상위 폴더(`load-test/collection-result-pending/`)의 docker compose로 상환 서버·펌뱅킹·두 DB·Prometheus·Grafana를 띄우고, k6도 compose로 실행한다. 펌뱅킹은 옆에 clone한 `firmbanking-mock`을 빌드하고, 다른 곳에 있으면 `FIRMBANKING_MOCK_DIR`로 경로를 넘긴다.

```bash
cd load-test/collection-result-pending
docker compose up -d --build
```

1. 기준 실행: 장애 없이 돌려 더미 데이터와 정합성 SQL이 맞는지 확인한다. 모든 상환이 200이어야 한다.

   ```bash
   FAULT=off docker compose run --rm k6
   ```

2. 장애 실행

   ```bash
   FAULT_START_URL=<장애 켜기 API> FAULT_END_URL=<장애 끄기 API> docker compose run --rm k6
   ```

요약은 `summary-baseline.json`, `summary-outage.json`으로 남는다. 지표는 Grafana(http://localhost:3000)의 `펌뱅킹 장애 부하 테스트` 대시보드에서 본다.

## 보는 지표

Grafana 대시보드의 패널 4개다.

| 패널 | 출처 | 보는 것 |
|---|---|---|
| 상태별 집금 요청 건수 | 상환 서버 | 복구 뒤 REQUESTED가 0으로 수렴하는지 |
| 상환 응답 코드별 초당 건수 | k6 | 장애 구간에 202로 바뀌고, 복구 뒤 200으로 돌아오는지 |
| 상환 실패율 | k6 | 200, 202 말고 5xx나 시간 초과가 나는지 |
| `dropped_iterations` | k6 | 0인지. 0이 아니면 의도한 초당 요청 수가 나가지 않은 것 |
