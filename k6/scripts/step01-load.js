import http from 'k6/http';
import { check } from 'k6';

// 처리량 측정: 초당 요청 수를 단계적으로 올리며 한계가 어디서 오는지 본다.
// 재고가 바닥나지 않는 상품(2번)을 쓴다.
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PRODUCT_ID = 2;

export const options = {
  scenarios: {
    order_load: {
      // 열린 모델: 서버가 느려져도 요청을 줄이지 않는다 (실제 사용자처럼)
      executor: 'ramping-arrival-rate',
      startRate: 5,
      timeUnit: '1s',
      preAllocatedVUs: 100,
      maxVUs: 2000,
      stages: [
        { target: 20, duration: '30s' },
        { target: 20, duration: '1m' }, // 이론상 한계(약 33 TPS) 아래
        { target: 40, duration: '30s' },
        { target: 40, duration: '1m' }, // 이론상 한계 바로 위
        { target: 60, duration: '30s' },
        { target: 60, duration: '1m' }, // 한계의 약 2배
      ],
    },
  },
  // SLO: p95 500ms 미만, 에러율 1% 미만
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
  },
};

export default function () {
  const payload = JSON.stringify({
    userId: Math.floor(Math.random() * 10000) + 1,
    productId: PRODUCT_ID,
    quantity: 1,
  });
  const res = http.post(`${BASE_URL}/api/orders`, payload, {
    headers: { 'Content-Type': 'application/json' },
    timeout: '60s', // Hikari connectionTimeout(30s)보다 길게 잡아 서버 쪽 타임아웃을 관찰한다
    tags: { name: 'POST /api/orders' },
  });
  check(res, { 'status is 201': (r) => r.status === 201 });
}
