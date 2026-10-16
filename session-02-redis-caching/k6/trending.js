import http from 'k6/http';
import { check } from 'k6';

// The home page's "trending" block — an aggregate that costs the database ~50 ms.
//   v1 → no cache · v2 → @Cacheable (stampede on expiry) · v3 → @Cacheable(sync = true)
const V = __ENV.V || 'v1';
const BASE = __ENV.BASE || 'http://localhost:8080';

export const options = {
  scenarios: {
    trending: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 300),
      timeUnit: '1s',
      duration: __ENV.DURATION || '20s',
      preAllocatedVUs: 200,
      maxVUs: 1000,
    },
  },
  summaryTrendStats: ['med', 'p(95)', 'p(99)', 'max'],
};

export default function () {
  const res = http.get(`${BASE}/${V}/trending`);
  check(res, { 'status 200': (r) => r.status === 200 });
}
