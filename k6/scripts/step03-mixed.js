import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

// 인기 상품(2번) 주문이 몰리는 동안, 상관없는 상품 조회(1번)가 함께 들어오면 조회도 느려지는가?
// 락을 DB 안에서 기다리면(비관적 락, 원자적 UPDATE) 대기자가 커넥션을 쥐고 있어서 조회도 커넥션을 기다린다는 가설을 확인한다.
// 두 시나리오의 지연은 scenario 태그로 따로 본다.
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const HOT_PRODUCT_ID = 2;
const OTHER_PRODUCT_ID = 1;

// 인기 상품 주문의 단계별 목표 RPS. 락 대기가 드러나게 PG 지연을 0으로 두고 돌린다.
const RATES = (__ENV.RATES || '300,600,900').split(',').map(Number);
const BROWSE_RATE = Number(__ENV.BROWSE_RATE || 50);
const DURATION_SEC = RATES.length * 60;

const ordered = new Counter('orders_created');
const errors = new Counter('orders_error');

export const options = {
  scenarios: {
    hot_order: {
      executor: 'ramping-arrival-rate',
      exec: 'hotOrder',
      startRate: 0,
      timeUnit: '1s',
      preAllocatedVUs: 300,
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
    'http_req_duration{scenario:hot_order}': ['p(95)<500'],
    'http_req_duration{scenario:browse}': ['p(95)<100'],
    'http_req_failed{scenario:hot_order}': ['rate<0.01'],
    'http_req_failed{scenario:browse}': ['rate<0.01'],
  },
};

export function hotOrder() {
  const payload = JSON.stringify({ userId: __VU, productId: HOT_PRODUCT_ID, quantity: 1 });
  const res = http.post(`${BASE_URL}/api/orders`, payload, {
    headers: { 'Content-Type': 'application/json' },
    timeout: '60s',
    tags: { name: 'POST /api/orders' },
  });

  if (res.status === 201) ordered.add(1);
  else errors.add(1);

  check(res, { 'order status is 201': (r) => r.status === 201 });
}

export function browse() {
  const res = http.get(`${BASE_URL}/api/products/${OTHER_PRODUCT_ID}`, {
    timeout: '60s',
    tags: { name: 'GET /api/products/{id}' },
  });
  check(res, { 'browse status is 200': (r) => r.status === 200 });
}
