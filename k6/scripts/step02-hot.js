import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

// 인기 상품 하나(2번, 재고 충분)에 주문을 몰아서, 락 방식별로 처리량과 지연을 비교한다.
// 열린 모델(ramping-arrival-rate): 서버가 느려져도 요청은 정해진 속도로 계속 들어온다.
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PRODUCT_ID = 2;

// 단계별 목표 RPS. 기본은 100,200,300. DB 락만 따로 보고 싶으면 PG 지연을 0으로 두고 RATES=400,800,1200 처럼 올린다.
const RATES = (__ENV.RATES || '100,200,300').split(',').map(Number);

const ordered = new Counter('orders_created');
const errors = new Counter('orders_error');

export const options = {
  scenarios: {
    hot_product: {
      executor: 'ramping-arrival-rate',
      startRate: 0,
      timeUnit: '1s',
      preAllocatedVUs: 300,
      maxVUs: 1500,
      stages: RATES.flatMap((target) => [
        { target, duration: '15s' },
        { target, duration: '45s' },
      ]),
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  const payload = JSON.stringify({ userId: __VU, productId: PRODUCT_ID, quantity: 1 });
  const res = http.post(`${BASE_URL}/api/orders`, payload, {
    headers: { 'Content-Type': 'application/json' },
    timeout: '60s',
    tags: { name: 'POST /api/orders' },
  });

  if (res.status === 201) ordered.add(1);
  else errors.add(1);

  check(res, { 'status is 201': (r) => r.status === 201 });
}
