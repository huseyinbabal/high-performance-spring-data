import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

// The on-sale moment, ten times bigger than last week: 1,000 buyers/second for 1,000 tickets.
//   v1 → POST /v1/orders                 every buyer hits PostgreSQL
//   v2 → POST /v2/holds, then /confirm   Redis answers "sold out"; only holders reach PostgreSQL
const V = __ENV.V || 'v1';
const BASE = __ENV.BASE || 'http://localhost:8080';
const JSON_HEADERS = { headers: { 'Content-Type': 'application/json' } };

const sold = new Counter('tickets_sold');
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

  let res;
  if (V === 'v1') {
    res = http.post(`${BASE}/v1/orders`, body, JSON_HEADERS);
  } else {
    res = http.post(`${BASE}/v2/holds`, body, JSON_HEADERS);
    if (res.status === 201) {
      res = http.post(`${BASE}/v2/holds/${res.json('token')}/confirm`, null);
    }
  }

  if (res.status === 201) sold.add(1);
  else if (res.status === 409) soldOut.add(1);
  else if (res.status >= 500) errors.add(1);
  check(res, { 'no 5xx': (r) => r.status < 500 });
}
