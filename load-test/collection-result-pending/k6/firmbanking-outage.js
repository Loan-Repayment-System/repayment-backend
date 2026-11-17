import http from 'k6/http';
import exec from 'k6/execution';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.1.0/index.js';

/**
 * 펌뱅킹 장애 부하 테스트. 정상 3분 → 장애 10분 → 복구 15분 동안 상환을 초당 요청 수 고정으로 보낸다.
 * 장애는 펌뱅킹이 이체는 처리하고 응답을 보내지 않는 상태이고, 3분과 13분에 이 스크립트가 켜고 끈다.
 * 상환 서버와 펌뱅킹 사이의 Toxiproxy에서 펌뱅킹 → 상환 서버 방향 데이터를 늦춰 만든다.
 * FAULT=off면 장애 없이 돌려 더미 데이터와 정합성 SQL이 맞는지 먼저 확인한다.
 */

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const LOAN_ID_START = Number(__ENV.LOAN_ID_START || 1);
const LOAN_COUNT = Number(__ENV.LOAN_COUNT || 30000);
const REPAYMENT_AMOUNT = Number(__ENV.REPAYMENT_AMOUNT || 10000);
const FAULT_ENABLED = __ENV.FAULT !== 'off';
const TOXIPROXY_URL = __ENV.TOXIPROXY_URL || 'http://localhost:8474';
const FAULT_TOXIC_NAME = 'response-delay';
const FAULT_RESPONSE_DELAY_MS = 60000;

const NORMAL_MINUTES = 3;
const OUTAGE_MINUTES = 10;
const RECOVERY_MINUTES = 15;
const TOTAL_MINUTES = NORMAL_MINUTES + OUTAGE_MINUTES + RECOVERY_MINUTES;

const scenarios = {
    repayment: {
        executor: 'constant-arrival-rate',
        exec: 'repay',
        rate: 15,
        timeUnit: '1s',
        duration: `${TOTAL_MINUTES}m`,
        preAllocatedVUs: 200,
        maxVUs: 400,
    },
};

if (FAULT_ENABLED) {
    scenarios.fault_start = {
        executor: 'shared-iterations',
        exec: 'startFault',
        vus: 1,
        iterations: 1,
        startTime: `${NORMAL_MINUTES}m`,
    };
    scenarios.fault_end = {
        executor: 'shared-iterations',
        exec: 'endFault',
        vus: 1,
        iterations: 1,
        startTime: `${NORMAL_MINUTES + OUTAGE_MINUTES}m`,
    };
}

export const options = {
    scenarios,
    summaryTrendStats: ['avg', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const repaymentParams = {
    headers: { 'Content-Type': 'application/json' },
    // 202는 결과 미확인 응답이라 실패가 아니다.
    responseCallback: http.expectedStatuses(200, 202),
};

// 같은 대출에 미확정 건이 있으면 거절되므로 요청마다 다른 대출을 상환한다.
export function repay() {
    const iteration = exec.scenario.iterationInTest;
    if (iteration >= LOAN_COUNT) {
        exec.test.abort(`대출 ${LOAN_COUNT}개를 모두 상환했다. LOAN_COUNT를 늘려야 한다`);
    }
    const loanAccountId = LOAN_ID_START + iteration;
    http.post(`${BASE_URL}/api/loans/${loanAccountId}/repayments`,
        JSON.stringify({ amount: REPAYMENT_AMOUNT }),
        Object.assign({ tags: { name: 'POST /api/loans/{id}/repayments' } }, repaymentParams));
}

// 요청은 펌뱅킹에 닿고, 응답과 연결 닫힘은 상환 서버의 응답 대기(5초)보다 늦게 돌아온다.
// timeout toxic은 응답은 버려도 펌뱅킹이 연결을 닫는 것은 전달해, 응답 대기 전에 끊김으로 끝난다.
export function startFault() {
    const toxic = {
        name: FAULT_TOXIC_NAME,
        type: 'latency',
        stream: 'downstream',
        attributes: { latency: FAULT_RESPONSE_DELAY_MS },
    };
    const response = http.post(`${TOXIPROXY_URL}/proxies/firmbanking/toxics`, JSON.stringify(toxic),
        { headers: { 'Content-Type': 'application/json' }, tags: { name: 'fault' } });
    checkFaultResponse(response, 200);
}

export function endFault() {
    const response = http.del(`${TOXIPROXY_URL}/proxies/firmbanking/toxics/${FAULT_TOXIC_NAME}`, null,
        { tags: { name: 'fault' } });
    checkFaultResponse(response, 204);
}

// 장애를 켜고 끄지 못하면 시나리오가 성립하지 않으므로 바로 멈춘다.
function checkFaultResponse(response, expectedStatus) {
    if (response.status !== expectedStatus) {
        exec.test.abort(`장애 API 호출 실패: ${response.request.method} ${response.url} ${response.status}`);
    }
}

export function handleSummary(data) {
    return {
        stdout: textSummary(data, { indent: ' ', enableColors: true }),
        [`summary-${FAULT_ENABLED ? 'outage' : 'baseline'}.json`]: JSON.stringify(data, null, 2),
    };
}
