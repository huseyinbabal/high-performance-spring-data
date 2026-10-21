import http from 'k6/http';
import { check } from 'k6';

// Infinite scroll, deep down the list of upcoming events.
//   v1 → from + size: a random page between 400 and 499 (every shard sorts up to 10,000 hits)
//   v2 → search_after: a random cursor — the same depth, but each shard only collects 20
const V = __ENV.V || 'v1';
const BASE = __ENV.BASE || 'http://localhost:8080';
const NOW = Date.now();
const LAST = 1823174998435;   // the latest startsAt in the seed

export const options = {
  scenarios: {
    scroll: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 500),
      timeUnit: '1s',
      duration: __ENV.DURATION || '20s',
      preAllocatedVUs: 100,
      maxVUs: 600,
    },
  },
  summaryTrendStats: ['med', 'p(95)', 'p(99)', 'max'],
};

export default function () {
  const url = V === 'v1'
    ? `${BASE}/v1/events/browse?page=${400 + Math.floor(Math.random() * 100)}`
    : `${BASE}/v2/events/browse?after=${Math.floor(NOW + Math.random() * (LAST - NOW) * 0.9)},${Math.floor(Math.random() * 100000)}`;
  const res = http.get(url);
  check(res, { 'status 200': (r) => r.status === 200 });
}
