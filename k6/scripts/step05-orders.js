import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';

// 주문이 100만 건 쌓인 뒤의 "내 주문 목록" 조회 (make seed로 데이터를 먼저 넣고, make k6 ... RESET=0으로 실행)
// - 사용자 1~10,000 중 한 명을 골라 첫 페이지(20건)를 본다
// - 그중 일부는 "결제 실패한 주문만" 거른다 (status=FAILED). 사용자마다 실패 주문이 20건이 안 되는 경우가 많다
// 같은 시간에 상관없는 상품 조회(2번)가 함께 들어올 때 조회도 느려지는지 scenario 태그로 따로 본다.
// DB가 읽은 행 수는 make k6가 실행 전후 SHOW GLOBAL STATUS 차이로 결과 파일 끝에 남긴다.
const BASE_URLS = (__ENV.BASE_URLS || __ENV.BASE_URL || 'http://localhost:8080').split(',');
const baseUrl = () => BASE_URLS[(__VU + __ITER) % BASE_URLS.length];
const USERS = 10000;
const OTHER_PRODUCT_ID = 2;

// 주문 목록 조회의 단계별 목표 RPS
const RATES = (__ENV.RATES || '10,20,40').split(',').map(Number);
const FAILED_RATIO = Number(__ENV.FAILED_RATIO || 0.3);
const BROWSE_RATE = Number(__ENV.BROWSE_RATE || 50);
const DURATION_SEC = RATES.length * 60;

// 상태 조건 유무에 따라 따로 모은 지연
const allDuration = new Trend('list_all_duration', true);
const failedDuration = new Trend('list_failed_duration', true);

export const options = {
  scenarios: {
    my_orders: {
      executor: 'ramping-arrival-rate',
      exec: 'myOrders',
      startRate: 0,
      timeUnit: '1s',
      preAllocatedVUs: 100,
      maxVUs: 1500,
      stages: RATES.flatMap((target) => [
        { target, duration: '15s' },
        { target, duration: '45s' },
      ]),
    },
    browse: {
      executor: 'constant-arrival-rate',
      exec: 'browse',
      rate: BROWSE_RATE,
      timeUnit: '1s',
      duration: `${DURATION_SEC}s`,
      preAllocatedVUs: 50,
      maxVUs: 500,
    },
  },
  // 시나리오별 지연을 요약에 따로 출력하려고 threshold를 건다
  thresholds: {
    'http_req_duration{scenario:my_orders}': ['p(95)<200'],
    'http_req_duration{scenario:browse}': ['p(95)<100'],
    'http_req_failed{scenario:my_orders}': ['rate<0.01'],
    'http_req_failed{scenario:browse}': ['rate<0.01'],
    list_all_duration: ['p(95)<200'],
    list_failed_duration: ['p(95)<200'],
  },
};

export function myOrders() {
  const userId = 1 + Math.floor(Math.random() * USERS);
  const onlyFailed = Math.random() < FAILED_RATIO;
  const query = onlyFailed ? '?size=20&status=FAILED' : '?size=20';
  const res = http.get(`${baseUrl()}/api/users/${userId}/orders${query}`, {
    timeout: '60s',
    tags: { name: onlyFailed ? 'GET /api/users/{id}/orders?status' : 'GET /api/users/{id}/orders' },
  });

  (onlyFailed ? failedDuration : allDuration).add(res.timings.duration);
  check(res, { 'list status is 200': (r) => r.status === 200 });
}

export function browse() {
  const res = http.get(`${baseUrl()}/api/products/${OTHER_PRODUCT_ID}`, {
    timeout: '60s',
    tags: { name: 'GET /api/products/{id}' },
  });
  check(res, { 'browse status is 200': (r) => r.status === 200 });
}
