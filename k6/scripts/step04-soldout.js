import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';

// 타임딜 오픈 뒤 품절: 재고 100개 상품(1번)에 주문이 계속 몰린다.
// 100건이 팔린 뒤의 요청은 모두 "품절"(409)을 들어야 하는데, 그 한 마디를 듣기까지 DB를 얼마나 쓰는지 본다.
// 같은 시간에 상관없는 상품 조회(2번)가 함께 들어올 때 조회도 느려지는지 scenario 태그로 따로 본다.
// DB 쿼리 수는 make k6가 실행 전후 SHOW GLOBAL STATUS 차이로 결과 파일 끝에 남긴다.
const BASE_URLS = (__ENV.BASE_URLS || __ENV.BASE_URL || 'http://localhost:8080').split(',');
const baseUrl = () => BASE_URLS[(__VU + __ITER) % BASE_URLS.length];
const DEAL_PRODUCT_ID = 1;
const OTHER_PRODUCT_ID = 2;

// 딜 상품 주문의 단계별 목표 RPS
const RATES = (__ENV.RATES || '300,600,900').split(',').map(Number);
const BROWSE_RATE = Number(__ENV.BROWSE_RATE || 50);
const DURATION_SEC = RATES.length * 60;

const ordered = new Counter('orders_created');
const soldOut = new Counter('orders_sold_out');
const errors = new Counter('orders_error');
// 품절 응답만 따로 모은 지연. 대부분의 요청이 여기에 해당한다
const soldOutDuration = new Trend('sold_out_duration', true);

// 409(품절)는 정상 응답이다. http_req_failed에 섞이지 않게 기대 상태 코드로 등록한다
http.setResponseCallback(http.expectedStatuses(200, 201, 409));

export const options = {
  scenarios: {
    deal_order: {
      executor: 'ramping-arrival-rate',
      exec: 'dealOrder',
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
    'http_req_duration{scenario:deal_order}': ['p(95)<500'],
    'http_req_duration{scenario:browse}': ['p(95)<100'],
    'http_req_failed{scenario:deal_order}': ['rate<0.01'],
    'http_req_failed{scenario:browse}': ['rate<0.01'],
    sold_out_duration: ['p(95)<100'],
  },
};

export function dealOrder() {
  // 같은 사람이 여러 번 주문하는 상황은 이번 관심사가 아니라서 요청마다 다른 userId를 쓴다
  const userId = __VU * 100000 + __ITER;
  const payload = JSON.stringify({ userId, productId: DEAL_PRODUCT_ID, quantity: 1 });
  const res = http.post(`${baseUrl()}/api/orders`, payload, {
    headers: { 'Content-Type': 'application/json' },
    timeout: '60s',
    tags: { name: 'POST /api/orders' },
  });

  if (res.status === 201) ordered.add(1);
  else if (res.status === 409) {
    soldOut.add(1);
    soldOutDuration.add(res.timings.duration);
  } else errors.add(1);

  check(res, { 'status is 201 or 409': (r) => r.status === 201 || r.status === 409 });
}

export function browse() {
  const res = http.get(`${baseUrl()}/api/products/${OTHER_PRODUCT_ID}`, {
    timeout: '60s',
    tags: { name: 'GET /api/products/{id}' },
  });
  check(res, { 'browse status is 200': (r) => r.status === 200 });
}
