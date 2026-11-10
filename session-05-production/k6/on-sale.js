import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

// 1,000 buyers a second for 1,000 tickets — against however many app instances are behind BASE.
// The claim under test: the answer is exactly 1,000, no matter how many pods take part.
const BASE = __ENV.BASE || 'http://localhost:8080';
const JSON_HEADERS = { headers: { 'Content-Type': 'application/json' } };

const accepted = new Counter('orders_accepted');
const soldOut = new Counter('sold_out_409');
const errors = new Counter('server_errors_5xx');

export const options = {
  scenarios: {
    on_sale: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 1000),
      timeUnit: '1s',
      duration: __ENV.DURATION || '15s',
      preAllocatedVUs: 500,
      maxVUs: 3000,
    },
  },
  summaryTrendStats: ['med', 'p(95)', 'p(99)', 'max'],
};

export default function () {
  const body = JSON.stringify({
    customerId: 1 + Math.floor(Math.random() * 200000),
    ticketTypeId: 1,
    quantity: 1,
  });
  let res = http.post(`${BASE}/holds`, body, JSON_HEADERS);
  if (res.status === 201) {
    res = http.post(`${BASE}/holds/${res.json('token')}/confirm`, null);
  }
  if (res.status === 202) accepted.add(1);
  else if (res.status === 409) soldOut.add(1);
  else if (res.status >= 500) errors.add(1);
  check(res, { 'no 5xx': (r) => r.status < 500 });
}
