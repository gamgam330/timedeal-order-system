import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

// 타임딜 오픈: 재고 100개 상품(1번)에 300명이 동시에 한 번씩 주문한다.
// 기대값: 성공(201) 100건, 재고 소진(409) 200건, 남은 재고 0
// 서버 여러 대로 측정할 때는 BASE_URLS=http://localhost:8080,http://localhost:8082 처럼 주면 요청을 번갈아 보낸다 (03단계)
const BASE_URLS = (__ENV.BASE_URLS || __ENV.BASE_URL || 'http://localhost:8080').split(',');
const baseUrl = () => BASE_URLS[(__VU + __ITER) % BASE_URLS.length];
const PRODUCT_ID = 1;

const ordered = new Counter('orders_created');
const soldOut = new Counter('orders_sold_out');
const errors = new Counter('orders_error');

export const options = {
  scenarios: {
    timedeal_open: {
      executor: 'per-vu-iterations',
      vus: 300,
      iterations: 1,
      maxDuration: '2m',
    },
  },
};

export default function () {
  const payload = JSON.stringify({ userId: __VU, productId: PRODUCT_ID, quantity: 1 });
  const res = http.post(`${baseUrl()}/api/orders`, payload, {
    headers: { 'Content-Type': 'application/json' },
    timeout: '60s',
    tags: { name: 'POST /api/orders' },
  });

  if (res.status === 201) ordered.add(1);
  else if (res.status === 409) soldOut.add(1);
  else errors.add(1);

  check(res, { 'status is 201 or 409': (r) => r.status === 201 || r.status === 409 });
}
