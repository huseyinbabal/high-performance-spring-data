import http from 'k6/http';
import { check } from 'k6';

// Back-office order feed, somewhere deep in 1M rows.
//   v1 → ?page=N   (OFFSET + count(*) + N+1)
//   v2 → ?after=ID (keyset)
const V = __ENV.V || 'v1';
const BASE = __ENV.BASE || 'http://localhost:8080';

export const options = {
  scenarios: {
    feed: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 100),
      timeUnit: '1s',
      duration: __ENV.DURATION || '20s',
      preAllocatedVUs: 100,
      maxVUs: 400,
    },
  },
  summaryTrendStats: ['med', 'p(95)', 'p(99)', 'max'],
};

export default function () {
  const url = V === 'v1'
    ? `${BASE}/v1/orders?page=${Math.floor(Math.random() * 45000)}&size=20`
    : `${BASE}/v2/orders?after=${100000 + Math.floor(Math.random() * 900000)}&size=20`;
  const res = http.get(url);
  check(res, { 'status 200': (r) => r.status === 200 });
}
