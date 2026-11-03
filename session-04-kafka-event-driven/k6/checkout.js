import http from 'k6/http';
import { check } from 'k6';

// Checkout while people keep browsing.
//   v1 → confirm waits for the payment provider on the request thread
//   v2 → confirm commits order + event and answers 202; a Kafka consumer pays
// Run it twice: with a healthy provider, then after `make payment-slow`.
const V = __ENV.V || 'v1';
const BASE = __ENV.BASE || 'http://localhost:8080';
const JSON_HEADERS = { 'Content-Type': 'application/json' };

export const options = {
  scenarios: {
    checkout: {
      executor: 'constant-arrival-rate', exec: 'checkout',
      rate: Number(__ENV.RATE || 200), timeUnit: '1s', duration: __ENV.DURATION || '20s',
      preAllocatedVUs: 300, maxVUs: 2000,
    },
    browse: {
      executor: 'constant-arrival-rate', exec: 'browse',
      rate: 100, timeUnit: '1s', duration: __ENV.DURATION || '20s',
      preAllocatedVUs: 50, maxVUs: 1000,
    },
  },
  // thresholds on tags make k6 print these as separate lines in the summary
  thresholds: {
    'http_req_duration{name:confirm}': ['p(95)>=0'],
    'http_req_duration{name:browse}': ['p(95)>=0'],
    'http_req_failed{name:browse}': ['rate>=0'],
  },
  summaryTrendStats: ['med', 'p(95)', 'p(99)', 'max'],
};

export function checkout() {
  const body = JSON.stringify({
    customerId: 1 + Math.floor(Math.random() * 200000),
    ticketTypeId: 4 + Math.floor(Math.random() * 3000),   // spread over 1,000 events: stock is not the bottleneck
    quantity: 1,
  });
  const hold = http.post(`${BASE}/holds`, body, { headers: JSON_HEADERS, tags: { name: 'hold' } });
  if (hold.status !== 201) return;
  const res = http.post(`${BASE}/${V}/holds/${hold.json('token')}/confirm`, null, { tags: { name: 'confirm' } });
  check(res, { 'order accepted': (r) => r.status === 201 || r.status === 202 });
}

export function browse() {
  const res = http.get(`${BASE}/events/${1 + Math.floor(Math.random() * 200)}`, { tags: { name: 'browse' }, timeout: '10s' });
  check(res, { 'page served': (r) => r.status === 200 });
}
