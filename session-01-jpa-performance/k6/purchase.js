import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

// The on-sale moment: everybody wants ticket type 1 (1,000 tickets) at the same second.
//   k6 run -e V=v1 k6/purchase.js   → pool exhaustion + oversell
//   k6 run -e V=v2 k6/purchase.js   → short transactions, exactly 1,000 sold
const V = __ENV.V || 'v1';
const BASE = __ENV.BASE || 'http://localhost:8080';

const sold = new Counter('tickets_sold');
const soldOut = new Counter('sold_out_409');
const errors = new Counter('server_errors_5xx');

export const options = {
  scenarios: {
    on_sale: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 150),
      timeUnit: '1s',
      duration: __ENV.DURATION || '20s',
      preAllocatedVUs: 200,
      maxVUs: 1000,
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
  const res = http.post(`${BASE}/${V}/orders`, body, { headers: { 'Content-Type': 'application/json' } });
  if (res.status === 201) sold.add(1);
  else if (res.status === 409) soldOut.add(1);
  else errors.add(1);
  check(res, { 'no 5xx': (r) => r.status < 500 });
}
