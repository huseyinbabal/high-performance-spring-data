import http from 'k6/http';
import { check } from 'k6';

// The event page during a rush: most traffic lands on ONE event.
//   v1 → PostgreSQL every time · v2 → cached info + live availability from Redis
const V = __ENV.V || 'v1';
const BASE = __ENV.BASE || 'http://localhost:8080';

export const options = {
  scenarios: {
    event_page: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 2000),
      timeUnit: '1s',
      duration: __ENV.DURATION || '20s',
      preAllocatedVUs: 200,
      maxVUs: 1000,
    },
  },
  summaryTrendStats: ['med', 'p(95)', 'p(99)', 'max'],
};

export default function () {
  // 80% of visitors want the hot event, the rest browse 200 other pages
  const id = Math.random() < 0.8 ? 1 : 2 + Math.floor(Math.random() * 200);
  const res = http.get(`${BASE}/${V}/events/${id}`);
  check(res, { 'status 200': (r) => r.status === 200 });
}
