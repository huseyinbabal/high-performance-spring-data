import http from 'k6/http';
import { check } from 'k6';

// Last week's failure, replayed on purpose: the SYNCHRONOUS confirm with a slow payment provider,
// while other people just browse. Run it once with platform threads, once with virtual threads.
//   make run VT=false  →  make payment-slow  →  make k6-threads
//   make run VT=true   →  make payment-slow  →  make k6-threads
const BASE = __ENV.BASE || 'http://localhost:8080';
const JSON_HEADERS = { 'Content-Type': 'application/json' };

export const options = {
  scenarios: {
    checkout: {
      executor: 'constant-arrival-rate', exec: 'checkout',
      rate: Number(__ENV.RATE || 200), timeUnit: '1s', duration: __ENV.DURATION || '20s',
      preAllocatedVUs: 500, maxVUs: 3000,
    },
    browse: {
      executor: 'constant-arrival-rate', exec: 'browse',
      rate: 100, timeUnit: '1s', duration: __ENV.DURATION || '20s',
      preAllocatedVUs: 50, maxVUs: 1000,
    },
  },
  thresholds: {
    'http_req_duration{name:confirm}': ['p(95)>=0'],
    'http_req_duration{name:browse}': ['p(95)>=0'],
  },
  summaryTrendStats: ['med', 'p(95)', 'p(99)', 'max'],
};

export function checkout() {
  const body = JSON.stringify({
    customerId: 1 + Math.floor(Math.random() * 200000),
    ticketTypeId: 4 + Math.floor(Math.random() * 3000),
    quantity: 1,
  });
  const hold = http.post(`${BASE}/holds`, body, { headers: JSON_HEADERS, tags: { name: 'hold' } });
  if (hold.status !== 201) return;
  const res = http.post(`${BASE}/v1/holds/${hold.json('token')}/confirm`, null, { tags: { name: 'confirm' }, timeout: '30s' });
  check(res, { 'order paid': (r) => r.status === 201 });
}

export function browse() {
  const res = http.get(`${BASE}/events/${1 + Math.floor(Math.random() * 200)}`, { tags: { name: 'browse' }, timeout: '30s' });
  check(res, { 'page served': (r) => r.status === 200 });
}
